package com.florin.report;

import com.florin.document.CustomerSnapshot;
import com.florin.document.Document;
import com.florin.document.DocumentLedger;
import com.florin.document.RecurringSchedule;
import com.florin.document.RecurringScheduleRepository;
import com.florin.report.dto.OverviewComingUp;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class ComingUp {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);
    private static final int DAYS = 90;
    private static final int RUNS = 5;
    private static final int QUOTATIONS = 3;

    private static final String UNANSWERED = """
            d.business_pkid = :business and %s and d.document_type = 'Quotation'
              and coalesce(d.currency, '') = :currency and d.accepted_at is null and d.declined_at is null
            """.formatted(DocumentLedger.COUNTED);

    private static final String AWAITING = "(d.due_date is null or d.due_date >= :startOfToday)";

    private final JdbcClient jdbc;
    private final RecurringScheduleRepository schedules;

    ComingUp(JdbcClient jdbc, RecurringScheduleRepository schedules) {
        this.jdbc = jdbc;
        this.schedules = schedules;
    }

    OverviewComingUp read(long businessPkid, String currency, ZoneId zone, LocalDate today) {
        return new OverviewComingUp(
                recurring(businessPkid, currency, today), quotations(businessPkid, currency, zone, today));
    }

    private OverviewComingUp.Recurring recurring(long businessPkid, String currency, LocalDate today) {
        LocalDate last = today.plusDays(DAYS - 1);
        List<RecurringSchedule> running = schedules.findRunning(businessPkid).stream()
                .filter(schedule ->
                        currency.equals(Objects.requireNonNullElse(schedule.getTemplate().getCurrency(), "")))
                .toList();
        List<OverviewComingUp.Run> runs = new ArrayList<>();
        for (RecurringSchedule schedule : running) {
            Document template = schedule.getTemplate();
            CustomerSnapshot client = template.getCustomerSnapshot();
            for (int done = schedule.getRunsDone(); ; done++) {
                LocalDate date = schedule.runAfter(done);
                if (date == null || date.isAfter(last)) {
                    break;
                }
                runs.add(new OverviewComingUp.Run(
                        schedule.getId(),
                        template.getId(),
                        template.getDocumentNumber(),
                        client == null ? null : client.getName(),
                        template.getTotal(),
                        date.isBefore(today) ? today : date));
            }
        }
        runs.sort(Comparator.comparing(OverviewComingUp.Run::date));
        return new OverviewComingUp.Recurring(
                running.size(),
                new OverviewComingUp.Next90Days(
                        runs.size(), runs.stream().map(OverviewComingUp.Run::amount).reduce(ZERO, BigDecimal::add)),
                runs.stream().limit(RUNS).toList());
    }

    private OverviewComingUp.Quotations quotations(long businessPkid, String currency, ZoneId zone, LocalDate today) {
        Map<String, Object> params = Map.of(
                "business", businessPkid,
                "currency", currency,
                "startOfToday", Timestamp.from(today.atStartOfDay(zone).toInstant()),
                "most", QUOTATIONS);
        List<OverviewComingUp.Quotation> soonest = jdbc.sql("""
                        select d.id, d.document_number, d.customer_name, d.total, d.due_date
                        from documents d
                        where %s and %s
                        order by d.due_date nulls last, d.pkid
                        limit :most
                        """.formatted(UNANSWERED, AWAITING))
                .params(params)
                .query((result, rowNumber) -> {
                    Timestamp due = result.getTimestamp("due_date");
                    LocalDate validUntil = due == null ? null : LocalDate.ofInstant(due.toInstant(), zone);
                    return new OverviewComingUp.Quotation(
                            result.getObject("id", UUID.class),
                            result.getString("document_number"),
                            result.getString("customer_name"),
                            result.getBigDecimal("total"),
                            validUntil,
                            validUntil == null ? null : ChronoUnit.DAYS.between(today, validUntil));
                })
                .list();
        List<OverviewComingUp.ExpiredQuotation> latest = jdbc.sql("""
                        select d.id, d.document_number, d.customer_name, d.total, d.due_date
                        from documents d
                        where %s and not %s
                        order by d.due_date desc, d.pkid
                        limit :most
                        """.formatted(UNANSWERED, AWAITING))
                .params(params)
                .query((result, rowNumber) -> new OverviewComingUp.ExpiredQuotation(
                        result.getObject("id", UUID.class),
                        result.getString("document_number"),
                        result.getString("customer_name"),
                        result.getBigDecimal("total"),
                        LocalDate.ofInstant(result.getTimestamp("due_date").toInstant(), zone)))
                .list();
        return jdbc.sql("""
                        select count(*) filter (where %1$s) as awaiting,
                               coalesce(sum(d.total) filter (where %1$s), 0) as awaiting_amount,
                               count(*) filter (where not %1$s) as expired,
                               coalesce(sum(d.total) filter (where not %1$s), 0) as expired_amount
                        from documents d
                        where %2$s
                        """.formatted(AWAITING, UNANSWERED))
                .params(params)
                .query((result, rowNumber) -> new OverviewComingUp.Quotations(
                        new OverviewComingUp.Awaiting(
                                result.getLong("awaiting"),
                                result.getBigDecimal("awaiting_amount").setScale(2),
                                soonest),
                        new OverviewComingUp.Expired(
                                result.getLong("expired"),
                                result.getBigDecimal("expired_amount").setScale(2),
                                latest)))
                .single();
    }
}
