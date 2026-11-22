package com.florin.document;

import com.florin.common.SearchText;
import com.florin.document.dto.DocumentListTotals;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class DocumentDirectory {

    private static final Map<String, String> ORDER_BY = Map.of(
            "createdAt", "d.created_at",
            "dueDate", "d.due_date",
            "total", "d.total",
            "number", "lower(d.document_number)",
            "client", "lower(d.customer_name)",
            "status", "d.status");

    private static final int BATCH = 500;

    private record Filter(String where, Map<String, Object> params) {}

    private final JdbcClient jdbc;
    private final DocumentRepository documentRepository;
    private final EntityManager entityManager;

    DocumentDirectory(JdbcClient jdbc, DocumentRepository documentRepository, EntityManager entityManager) {
        this.jdbc = jdbc;
        this.documentRepository = documentRepository;
        this.entityManager = entityManager;
    }

    List<Document> page(Long businessPkid, DocumentSearch search, LocalDate today, ZoneId zone) {
        Filter filter = filter(businessPkid, search, today, zone);
        List<Long> pkids = jdbc.sql("""
                        select d.pkid from documents d
                        where %s
                        order by %s
                        limit :limit offset :offset
                        """.formatted(filter.where(), orderBy(search)))
                .params(filter.params())
                .param("limit", search.size())
                .param("offset", (long) (search.page() - 1) * search.size())
                .query(Long.class)
                .list();
        return load(pkids, documentRepository::findByPkidIn);
    }

    void forEach(Long businessPkid, DocumentSearch search, LocalDate today, ZoneId zone, Consumer<Document> each) {
        Filter filter = filter(businessPkid, search, today, zone);
        List<Long> pkids = jdbc.sql("select d.pkid from documents d where %s order by %s"
                        .formatted(filter.where(), orderBy(search)))
                .params(filter.params())
                .query(Long.class)
                .list();
        for (int start = 0; start < pkids.size(); start += BATCH) {
            List<Long> batch = pkids.subList(start, Math.min(start + BATCH, pkids.size()));
            load(batch, documentRepository::findAllById).forEach(each);
            entityManager.clear();
        }
    }

    List<DocumentListTotals> totals(Long businessPkid, DocumentSearch search, LocalDate today, ZoneId zone) {
        Filter filter = filter(businessPkid, search, today, zone);
        return jdbc.sql("""
                        select coalesce(d.currency, '') as currency,
                               count(*) as documents,
                               coalesce(sum(%1$s) filter (where %3$s), 0) as billed,
                               coalesce(sum(%2$s) filter (where %4$s), 0) as balance_due,
                               coalesce(sum(%2$s) filter (where %4$s and d.due_date < :startOfToday), 0) as overdue,
                               count(*) filter (where %4$s and d.due_date < :startOfToday) as overdue_documents
                        from documents d
                        where %5$s
                        group by 1
                        order by 1
                        """.formatted(
                                DocumentLedger.BILLED,
                                DocumentLedger.OWED,
                                DocumentLedger.BILLED_DOCUMENT,
                                DocumentLedger.OWED_INVOICE,
                                filter.where()))
                .params(filter.params())
                .param("startOfToday", startOf(today, zone))
                .query((result, rowNumber) -> new DocumentListTotals(
                        result.getString("currency"),
                        result.getLong("documents"),
                        result.getBigDecimal("billed").setScale(2),
                        result.getBigDecimal("balance_due").setScale(2),
                        result.getBigDecimal("overdue").setScale(2),
                        result.getLong("overdue_documents")))
                .list();
    }

    private static List<Document> load(List<Long> pkids, Function<List<Long>, List<Document>> reader) {
        if (pkids.isEmpty()) {
            return List.of();
        }
        Map<Long, Document> found = reader.apply(pkids).stream()
                .collect(Collectors.toMap(Document::getPkid, Function.identity()));
        return pkids.stream().map(found::get).filter(Objects::nonNull).toList();
    }

    private static String orderBy(DocumentSearch search) {
        String direction = search.ascending() ? "asc" : "desc";
        return ORDER_BY.get(search.sort()) + " " + direction + ", d.pkid " + direction;
    }

    private static Filter filter(Long businessPkid, DocumentSearch search, LocalDate today, ZoneId zone) {
        List<String> conditions = new ArrayList<>(List.of("d.business_pkid = :business"));
        Map<String, Object> params = new HashMap<>(Map.of("business", businessPkid));
        if (search.query() != null && !search.query().isBlank()) {
            conditions.add("""
                    (lower(d.document_number) like :pattern escape '!'
                     or lower(d.customer_name) like :pattern escape '!'
                     or lower(d.customer_email) like :pattern escape '!')""");
            params.put("pattern", SearchText.containing(search.query()));
        }
        if (search.state() == DocumentState.DRAFT) {
            conditions.add("d.issued_at is null");
        }
        if (search.state() == DocumentState.VOID) {
            conditions.add("d.voided_at is not null");
        }
        if (search.state() == DocumentState.ISSUED || search.status() != null || search.overdueOnly()) {
            conditions.add(DocumentLedger.COUNTED);
        }
        if (search.status() != null) {
            conditions.add("d.status = :status and d.document_type not in ('Credit Note', 'Quotation')");
            params.put("status", search.status().getValue());
        }
        if (search.type() != null) {
            conditions.add("d.document_type = :type");
            params.put("type", search.type().getValue());
        }
        if (search.overdueOnly()) {
            conditions.add("d.document_type = 'Invoice' and d.status <> 'Paid' and d.due_date < :startOfToday");
            params.put("startOfToday", startOf(today, zone));
        }
        if (search.outstanding()) {
            conditions.add(DocumentLedger.OWED_INVOICE);
        }
        if (search.billed()) {
            conditions.add(DocumentLedger.BILLED_DOCUMENT);
        }
        if (search.due() != null) {
            LocalDate first = search.due().firstDay(today);
            params.put("dueFrom", startOf(first, zone));
            if (search.due() == DueWeek.CURRENT) {
                conditions.add(DocumentLedger.OWED_INVOICE);
            }
            if (search.due().runsOn()) {
                conditions.add("(d.due_date >= :dueFrom or d.due_date is null)");
            } else {
                conditions.add("d.due_date >= :dueFrom and d.due_date < :dueBefore");
                params.put("dueBefore", startOf(first.plusWeeks(1), zone));
            }
        }
        if (search.customerId() != null) {
            conditions.add("d.customer_pkid in (select c.pkid from customers c where c.id = :customer)");
            params.put("customer", search.customerId());
        }
        if (search.currency() != null) {
            conditions.add("coalesce(d.currency, '') = :currency");
            params.put("currency", search.currency());
        }
        if (search.issuedFrom() != null) {
            conditions.add("d.issue_date >= :from");
            params.put("from", search.issuedFrom());
        }
        if (search.issuedTo() != null) {
            conditions.add("d.issue_date <= :to");
            params.put("to", search.issuedTo());
        }
        return new Filter(String.join(" and ", conditions), params);
    }

    private static Timestamp startOf(LocalDate day, ZoneId zone) {
        return Timestamp.from(day.atStartOfDay(zone).toInstant());
    }
}
