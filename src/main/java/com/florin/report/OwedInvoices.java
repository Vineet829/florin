package com.florin.report;

import com.florin.document.DocumentLedger;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class OwedInvoices {

    record Owed(Instant dueDate, BigDecimal amount, UUID clientId, String client) {}

    private final JdbcClient jdbc;

    OwedInvoices(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    List<Owed> read(Long businessPkid, String currency) {
        return jdbc.sql("""
                        select d.due_date, %s as owed, c.id as client_id, coalesce(c.name, %s) as client
                        from documents d
                        left join customers c on c.pkid = d.customer_pkid
                        where d.business_pkid = :business and %s and coalesce(d.currency, '') = :currency
                        order by d.customer_pkid, d.pkid
                        """.formatted(DocumentLedger.OWED, Overview.SAVED_NAME, DocumentLedger.OWED_INVOICE))
                .param("business", businessPkid)
                .param("currency", currency)
                .query((result, rowNumber) -> {
                    Timestamp due = result.getTimestamp("due_date");
                    return new Owed(
                            due == null ? null : due.toInstant(),
                            result.getBigDecimal("owed"),
                            result.getObject("client_id", UUID.class),
                            result.getString("client"));
                })
                .list();
    }

    static long daysLate(Owed invoice, ZoneId zone, LocalDate today) {
        return invoice.dueDate() == null
                ? 0
                : ChronoUnit.DAYS.between(LocalDate.ofInstant(invoice.dueDate(), zone), today);
    }

    static int fifteenDays(long days) {
        return days <= 15 ? 0 : days <= 30 ? 1 : days <= 45 ? 2 : 3;
    }
}
