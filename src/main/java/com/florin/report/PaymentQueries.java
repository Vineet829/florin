package com.florin.report;

import com.florin.common.SearchText;
import com.florin.document.DocumentLedger;
import com.florin.report.dto.DocumentSummaryResponse;
import com.florin.report.dto.MethodTotals;
import com.florin.report.dto.PaymentEntry;
import com.florin.report.dto.PaymentSummaryResponse;
import com.florin.report.dto.PaymentTotals;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class PaymentQueries {

    private static final String DOCUMENT = """
            d.id as document_id, d.document_number, d.document_type, d.customer_name as client,
            d.customer_email as client_email, d.customer_pkid, %s as saved_name, d.currency"""
            .formatted(Overview.SAVED_NAME);

    private static final String ROWS = """
            select 'payment' as kind, p.id, p.pkid as sequence, p.date_paid as day, p.amount_paid as amount,
                   p.payment_method as method, p.additional_info as note, %2$s
            from payments p
            join documents d on d.pkid = p.document_pkid
            where d.business_pkid = :business and %1$s and d.document_type = 'Invoice'
            union all
            select 'receipt', d.id, d.pkid, d.issue_date, d.total, null, null, %2$s
            from documents d
            where d.business_pkid = :business and %1$s and d.document_type = 'Receipt'
            union all
            select 'refund', r.id, r.pkid, r.date_refunded, -r.amount, r.payment_method, r.reference, %2$s
            from refunds r
            join documents d on d.pkid = r.document_pkid
            where d.business_pkid = :business and %1$s
            """.formatted(DocumentLedger.COUNTED, DOCUMENT);

    private static final Map<String, String> ORDER_BY = Map.of(
            "date", "m.day",
            "amount", "m.amount",
            "client", "lower(m.client)");

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    static final String ON_A_RECEIPT = "On a receipt";

    private record Filter(String where, Map<String, Object> params) {}

    private final JdbcClient jdbc;

    PaymentQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    List<PaymentEntry> page(long businessPkid, PaymentSearch search) {
        Filter filter = filter(businessPkid, search);
        return jdbc.sql("""
                        select m.* from (%s) m
                        where %s
                        order by %s
                        limit :limit offset :offset
                        """.formatted(ROWS, filter.where(), orderBy(search)))
                .params(filter.params())
                .param("limit", search.size())
                .param("offset", (long) (search.page() - 1) * search.size())
                .query(PaymentQueries::entry)
                .list();
    }

    List<PaymentTotals> totals(long businessPkid, PaymentSearch search) {
        Filter filter = filter(businessPkid, search);
        return jdbc.sql("""
                        select coalesce(m.currency, '') as currency, count(*) as payments,
                               count(*) filter (where m.kind = 'receipt') as receipts, sum(m.amount) as received
                        from (%s) m
                        where %s
                        group by 1
                        order by 1
                        """.formatted(ROWS, filter.where()))
                .params(filter.params())
                .query((result, rowNumber) -> new PaymentTotals(
                        result.getString("currency"),
                        result.getLong("payments"),
                        result.getLong("receipts"),
                        result.getBigDecimal("received").setScale(2)))
                .list();
    }

    DocumentSummaryResponse.Received received(long businessPkid, String currency, LocalDate from, LocalDate to) {
        return totals(businessPkid, PaymentSearch.dated(currency, from, to)).stream()
                .findFirst()
                .map(totals -> new DocumentSummaryResponse.Received(totals.received(), totals.payments()))
                .orElse(new DocumentSummaryResponse.Received(ZERO, 0));
    }

    List<MethodTotals> byMethod(long businessPkid, PaymentSearch search) {
        Filter filter = filter(businessPkid, search);
        return jdbc.sql("""
                        select case when m.kind = 'receipt' then :receipt else m.method end as method,
                               count(*) as payments, sum(m.amount) as amount
                        from (%s) m
                        where %s
                        group by 1
                        """.formatted(ROWS, filter.where()))
                .params(filter.params())
                .param("receipt", ON_A_RECEIPT)
                .query((result, rowNumber) -> new MethodTotals(
                        result.getString("method"), result.getLong("payments"), result.getBigDecimal("amount")))
                .list();
    }

    List<PaymentSummaryResponse.Payer> byPayer(long businessPkid, PaymentSearch search) {
        Filter filter = filter(businessPkid, search);
        return jdbc.sql("""
                        select c.id as client_id, coalesce(c.name, m.saved_name) as client,
                               count(*) as payments, sum(m.amount) as amount
                        from (%s) m
                        left join customers c on c.pkid = m.customer_pkid
                        where %s
                        group by m.customer_pkid, m.saved_name, c.id, c.name
                        order by m.customer_pkid, m.saved_name
                        """.formatted(ROWS, filter.where()))
                .params(filter.params())
                .query((result, rowNumber) -> new PaymentSummaryResponse.Payer(
                        result.getObject("client_id", UUID.class),
                        result.getString("client"),
                        result.getLong("payments"),
                        result.getBigDecimal("amount")))
                .list();
    }

    List<String> methods(long businessPkid) {
        return jdbc.sql("select distinct m.method from (%s) m where m.method is not null order by 1"
                        .formatted(ROWS))
                .param("business", businessPkid)
                .query(String.class)
                .list();
    }

    private static String orderBy(PaymentSearch search) {
        String direction = search.ascending() ? "asc" : "desc";
        return ORDER_BY.get(search.sort()) + " " + direction + ", m.kind " + direction + ", m.sequence " + direction;
    }

    private static Filter filter(long businessPkid, PaymentSearch search) {
        List<String> conditions = new ArrayList<>(List.of("true"));
        Map<String, Object> params = new HashMap<>(Map.of("business", businessPkid));
        if (search.query() != null && !search.query().isBlank()) {
            conditions.add("""
                    (lower(m.document_number) like :pattern escape '!'
                     or lower(m.client) like :pattern escape '!'
                     or lower(m.client_email) like :pattern escape '!')""");
            params.put("pattern", SearchText.containing(search.query()));
        }
        if (PaymentSearch.RECEIPT.equals(search.method())) {
            conditions.add("m.kind = 'receipt'");
        } else if (search.method() != null) {
            conditions.add("m.method = :method");
            params.put("method", search.method());
        }
        if (search.currency() != null) {
            conditions.add("coalesce(m.currency, '') = :currency");
            params.put("currency", search.currency());
        }
        if (search.from() != null) {
            conditions.add("m.day >= :from");
            params.put("from", search.from());
        }
        if (search.to() != null) {
            conditions.add("m.day <= :to");
            params.put("to", search.to());
        }
        return new Filter(String.join(" and ", conditions), params);
    }

    private static PaymentEntry entry(ResultSet result, int rowNumber) throws SQLException {
        return new PaymentEntry(
                result.getString("kind"),
                result.getObject("id", UUID.class),
                result.getObject("day", LocalDate.class),
                result.getString("client"),
                new PaymentEntry.Document(
                        result.getObject("document_id", UUID.class),
                        result.getString("document_number"),
                        result.getString("document_type")),
                result.getString("method"),
                result.getString("note"),
                result.getBigDecimal("amount"),
                result.getString("currency"));
    }
}
