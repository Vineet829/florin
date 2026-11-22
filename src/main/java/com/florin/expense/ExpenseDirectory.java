package com.florin.expense;

import com.florin.common.SearchText;
import com.florin.expense.dto.ExpenseListTotals;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class ExpenseDirectory {

    private static final String ORDER_BY = "e.expense_date desc, e.pkid desc";

    private static final int BATCH = 500;

    private record Filter(String where, Map<String, Object> params) {}

    private final JdbcClient jdbc;
    private final ExpenseRepository expenseRepository;

    ExpenseDirectory(JdbcClient jdbc, ExpenseRepository expenseRepository) {
        this.jdbc = jdbc;
        this.expenseRepository = expenseRepository;
    }

    List<Expense> page(Long businessPkid, ExpenseSearch search) {
        Filter filter = filter(businessPkid, search);
        List<Long> pkids = jdbc.sql("""
                        select e.pkid from expenses e
                        where %s
                        order by %s
                        limit :limit offset :offset
                        """.formatted(filter.where(), ORDER_BY))
                .params(filter.params())
                .param("limit", search.size())
                .param("offset", (long) (search.page() - 1) * search.size())
                .query(Long.class)
                .list();
        return load(pkids);
    }

    List<Expense> all(Long businessPkid, ExpenseSearch search) {
        Filter filter = filter(businessPkid, search);
        List<Long> pkids = jdbc.sql("select e.pkid from expenses e where %s order by %s"
                        .formatted(filter.where(), ORDER_BY))
                .params(filter.params())
                .query(Long.class)
                .list();
        List<Expense> expenses = new ArrayList<>();
        for (int start = 0; start < pkids.size(); start += BATCH) {
            expenses.addAll(load(pkids.subList(start, Math.min(start + BATCH, pkids.size()))));
        }
        return expenses;
    }

    List<ExpenseListTotals> totals(Long businessPkid, ExpenseSearch search) {
        Filter filter = filter(businessPkid, search);
        return jdbc.sql("""
                        select e.currency, count(*) as expenses, sum(%s) as spent
                        from expenses e
                        where %s
                        group by 1
                        order by 1
                        """.formatted(ExpenseLedger.PAID, filter.where()))
                .params(filter.params())
                .query((result, rowNumber) -> new ExpenseListTotals(
                        result.getString("currency"),
                        result.getLong("expenses"),
                        result.getBigDecimal("spent").setScale(2)))
                .list();
    }

    private List<Expense> load(List<Long> pkids) {
        if (pkids.isEmpty()) {
            return List.of();
        }
        Map<Long, Expense> found = expenseRepository.findAllById(pkids).stream()
                .collect(Collectors.toMap(Expense::getPkid, Function.identity()));
        return pkids.stream().map(found::get).filter(Objects::nonNull).toList();
    }

    private static Filter filter(Long businessPkid, ExpenseSearch search) {
        List<String> conditions = new ArrayList<>(List.of("e.business_pkid = :business"));
        Map<String, Object> params = new HashMap<>(Map.of("business", businessPkid));
        if (search.query() != null && !search.query().isBlank()) {
            conditions.add("""
                    (lower(e.vendor) like :pattern escape '!'
                     or lower(e.category) like :pattern escape '!'
                     or lower(e.reference) like :pattern escape '!'
                     or lower(e.notes) like :pattern escape '!')""");
            params.put("pattern", SearchText.containing(search.query()));
        }
        if (search.category() != null) {
            conditions.add("lower(e.category) = lower(:category)");
            params.put("category", search.category());
        }
        if (search.currency() != null) {
            conditions.add("e.currency = :currency");
            params.put("currency", search.currency());
        }
        if (search.from() != null) {
            conditions.add("e.expense_date >= :from");
            params.put("from", search.from());
        }
        if (search.to() != null) {
            conditions.add("e.expense_date <= :to");
            params.put("to", search.to());
        }
        return new Filter(String.join(" and ", conditions), params);
    }
}
