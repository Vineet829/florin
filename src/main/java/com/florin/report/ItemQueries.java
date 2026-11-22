package com.florin.report;

import com.florin.document.DocumentLedger;
import com.florin.document.dto.BillingItemResponse;
import com.florin.report.dto.ItemSalesDetailResponse;
import com.florin.report.dto.OverviewItem;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class ItemQueries {

    static final String OTHER_LINES = "Other lines";

    private static final String ITEM = "lower(nullif(btrim(i.item_name), ''))";

    private static final String NAMED = ITEM + " is not distinct from lower(nullif(btrim(cast(:name as text)), ''))";

    private static final String LINE_NET =
            "round(i.quantity * coalesce(i.unit_price, 0) * (1 - i.discount / 100), 2)";

    private static final String SIGN = "(case when d.document_type = 'Credit Note' then -1 else 1 end)";

    private static final String LINES = "documents d join document_billing_items i on i.document_pkid = d.pkid";
    private static final String COUNTED = """
            d.business_pkid = :business and %s and coalesce(d.currency, '') = :currency
              and d.issue_date between :from and :to""".formatted(DocumentLedger.BILLED_DOCUMENT);

    record Sold(String key, OverviewItem item) {}

    record Lines(List<ItemSalesDetailResponse.Line> lines, long total) {}

    private record Line(ItemSalesDetailResponse.Line line, long lines) {}

    private final JdbcClient jdbc;

    ItemQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    List<Sold> sold(long businessPkid, String currency, LocalDate from, LocalDate to) {
        return sold("true", params(businessPkid, currency, from, to, null));
    }

    List<Sold> sold(long businessPkid, String currency, LocalDate from, LocalDate to, String name) {
        return sold(NAMED, params(businessPkid, currency, from, to, name));
    }

    private List<Sold> sold(String condition, Map<String, Object> params) {
        return jdbc.sql("""
                        select %1$s as key, (array_agg(btrim(i.item_name) order by d.pkid, i.position))[1] as item,
                               sum(%2$s * i.quantity) as quantity, sum(%2$s * %3$s) as amount,
                               count(distinct d.pkid) as documents
                        from %4$s
                        where %5$s and %6$s
                        group by 1
                        """.formatted(ITEM, SIGN, LINE_NET, LINES, COUNTED, condition))
                .params(params)
                .query((result, rowNumber) -> {
                    String item = result.getString("item");
                    return new Sold(
                            result.getString("key"),
                            new OverviewItem(
                                    item == null || item.isEmpty() ? OTHER_LINES : item,
                                    BillingItemResponse.plain(result.getBigDecimal("quantity")),
                                    result.getBigDecimal("amount"),
                                    result.getLong("documents")));
                })
                .list()
                .stream()
                .sorted(ProfitReport.largestFirst(sold -> sold.item().amount(), sold -> sold.item().item()))
                .toList();
    }

    List<ItemSalesDetailResponse.Client> clients(
            long businessPkid, String currency, LocalDate from, LocalDate to, String name) {
        return jdbc.sql("""
                        select c.id as client_id, coalesce(c.name, %1$s) as client,
                               sum(%2$s * i.quantity) as quantity, sum(%2$s * %3$s) as amount
                        from %4$s
                        left join customers c on c.pkid = d.customer_pkid
                        where %5$s and %6$s
                        group by d.customer_pkid, %1$s, c.id, c.name
                        order by d.customer_pkid, %1$s
                        """.formatted(Overview.SAVED_NAME, SIGN, LINE_NET, LINES, COUNTED, NAMED))
                .params(params(businessPkid, currency, from, to, name))
                .query((result, rowNumber) -> new ItemSalesDetailResponse.Client(
                        result.getObject("client_id", UUID.class),
                        result.getString("client"),
                        BillingItemResponse.plain(result.getBigDecimal("quantity")),
                        result.getBigDecimal("amount")))
                .list()
                .stream()
                .sorted(ProfitReport.largestFirst(
                        ItemSalesDetailResponse.Client::amount,
                        client -> Objects.requireNonNullElse(client.client(), "")))
                .toList();
    }

    Lines lines(long businessPkid, String currency, LocalDate from, LocalDate to, String name, int most) {
        List<Line> rows = jdbc.sql("""
                        select d.id as document_id, d.document_number, d.document_type, d.issue_date,
                               d.customer_name, %1$s * i.quantity as quantity, %1$s * %2$s as amount,
                               count(*) over () as lines
                        from %3$s
                        where %4$s and %5$s
                        order by d.issue_date desc, d.pkid desc, i.position
                        limit :most
                        """.formatted(SIGN, LINE_NET, LINES, COUNTED, NAMED))
                .params(params(businessPkid, currency, from, to, name))
                .param("most", most)
                .query((result, rowNumber) -> new Line(
                        new ItemSalesDetailResponse.Line(
                                result.getObject("document_id", UUID.class),
                                result.getString("document_number"),
                                result.getString("document_type"),
                                result.getObject("issue_date", LocalDate.class),
                                result.getString("customer_name"),
                                BillingItemResponse.plain(result.getBigDecimal("quantity")),
                                result.getBigDecimal("amount")),
                        result.getLong("lines")))
                .list();
        return new Lines(rows.stream().map(Line::line).toList(), rows.isEmpty() ? 0 : rows.get(0).lines());
    }

    private static Map<String, Object> params(
            long businessPkid, String currency, LocalDate from, LocalDate to, String name) {
        Map<String, Object> params = new HashMap<>();
        params.put("business", businessPkid);
        params.put("currency", currency);
        params.put("from", from);
        params.put("to", to);
        params.put("name", name);
        return params;
    }
}
