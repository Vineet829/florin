package com.florin.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.florin.IntegrationTest;
import com.florin.config.DemoProperties;
import com.florin.user.User;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

class DemoResetIntegrationTest extends IntegrationTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    private static final List<String> BY_BUSINESS = List.of(
            "documents", "customers", "catalog_items", "expenses", "email_templates", "business_settings",
            "notifications", "payment_claims", "portal_links", "recurring_schedules");
    private static final List<String> BY_DOCUMENT = List.of(
            "payments", "refunds", "share_links", "document_events", "document_billing_items",
            "document_tax_summaries");

    @Autowired
    private DemoDataSeeder seeder;

    @Autowired
    private DemoReset reset;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private DemoSeedRunner runner(User demo, boolean renew) {
        return new DemoSeedRunner(seeder, reset, new DemoProperties(demo.getEmail(), renew), transactionTemplate);
    }

    private ResultActions send(User user, MockHttpServletRequestBuilder request, String json) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private static String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }

    private void visit(User user) throws Exception {
        String client = JsonPath.read(
                body(send(user, post("/api/v1/customer/create"), """
                                {"name": "Visitor Ltd", "email": "%s@clients.test", "phoneNumber": "+1"}
                                """.formatted(UUID.randomUUID()))
                        .andExpect(status().isOk())),
                "$.createdCustomer._id");
        String invoice = JsonPath.read(
                body(send(user, post("/api/v1/document/create"), """
                                {"customerId": "%s", "documentType": "Invoice", "currency": "INR",
                                 "billingItems": [{"itemName": "Visit", "unitPrice": 250, "quantity": 2}]}
                                """.formatted(client))
                        .andExpect(status().isOk())),
                "$.newDocument._id");
        send(user, post("/api/v1/document/{id}/payment", invoice), """
                {"amountPaid": 100, "datePaid": "%s", "paymentMethod": "Cash"}
                """.formatted(LocalDate.now(ZONE)))
                .andExpect(status().isCreated());
        send(user, post("/api/v1/document/{id}/share", invoice), "").andExpect(status().is2xxSuccessful());
        send(user, post("/api/v1/document/{id}/credit-note", invoice), "").andExpect(status().isCreated());
        send(user, post("/api/v1/customer/{id}/portal", client), "").andExpect(status().is2xxSuccessful());
        send(user, post("/api/v1/expense"), """
                {"date": "%s", "category": "Snacks", "currency": "USD", "amount": 12, "taxAmount": 1,
                 "taxReclaimable": false}
                """.formatted(LocalDate.now(ZONE)))
                .andExpect(status().isCreated());
        send(user, put("/api/v1/settings"), """
                {"defaultCurrency": "EUR", "paymentTermsDays": 30, "defaultTaxRate": 20,
                 "taxLabel": "VAT", "timeZone": "Europe/London"}
                """)
                .andExpect(status().isOk());
    }

    private Map<String, Object> snapshot(Long business) {
        Map<String, Object> figures = new LinkedHashMap<>();
        for (String table : BY_BUSINESS) {
            figures.put(table, count("select count(*) from " + table + " where business_pkid = ?", business));
        }
        for (String table : BY_DOCUMENT) {
            figures.put(table, count(
                    "select count(*) from " + table
                            + " where document_pkid in (select pkid from documents where business_pkid = ?)",
                    business));
        }
        figures.put("recurring_runs", count("""
                select count(*) from recurring_runs
                where schedule_pkid in (select pkid from recurring_schedules where business_pkid = ?)
                """, business));
        figures.put("billed", amount("select sum(total) from documents where business_pkid = ?", business));
        figures.put("paid", amount("""
                select sum(p.amount_paid) from payments p join documents d on d.pkid = p.document_pkid
                where d.business_pkid = ?
                """, business));
        figures.put("spent", amount("select sum(amount + tax_amount) from expenses where business_pkid = ?", business));
        figures.put("numbers", jdbcTemplate.queryForList(
                "select coalesce(document_number, '') from documents where business_pkid = ? order by 1",
                String.class,
                business));
        figures.put("settings", jdbcTemplate.queryForList("""
                select concat_ws(' ', default_currency, time_zone, tax_label, next_invoice_number,
                                 next_quotation_number, next_receipt_number, next_credit_note_number)
                from business_settings where business_pkid = ?
                """, String.class, business));
        return figures;
    }

    private List<Long> auditEvents(Long business) {
        return jdbcTemplate.queryForList("select pkid from audit_events where business_pkid = ?", Long.class, business);
    }

    private int count(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Integer.class, args);
    }

    private BigDecimal amount(String sql, Long business) {
        BigDecimal amount = jdbcTemplate.queryForObject(sql, BigDecimal.class, business);
        return amount == null ? BigDecimal.ZERO : amount;
    }

    @Test
    @SuppressWarnings("unchecked")
    void renewRestoresFreshData() throws Exception {
        User demo = createUser();
        Long business = businessOf(demo).getPkid();
        DemoSeedRunner runner = runner(demo, true);
        runner.run(null);
        Map<String, Object> fresh = snapshot(business);
        assertThat(fresh)
                .containsEntry("documents", 51)
                .containsEntry("expenses", 14)
                .containsEntry("recurring_schedules", 3);
        assertThat((List<Object>) fresh.get("settings")).singleElement().asString().startsWith(
                "INR Asia/Kolkata GST ");
        assertThat((List<Object>) fresh.get("numbers")).contains("INV-1001", "QUO-0201", "REC-0301");

        User other = createUser();
        Long theirs = businessOf(other).getPkid();
        visit(other);
        Map<String, Object> before = snapshot(theirs);
        visit(demo);
        assertThat(snapshot(business)).isNotEqualTo(fresh);
        List<Long> audit = auditEvents(business);

        runner.renew();

        assertThat(snapshot(business)).isEqualTo(fresh);
        assertThat(snapshot(theirs)).isEqualTo(before);
        assertThat(count("select count(*) from businesses where pkid = ?", business)).isOne();
        assertThat(count("select count(*) from business_members where business_pkid = ?", business)).isOne();
        assertThat(userRepository.findById(demo.getPkid())).isPresent();
        assertThat(auditEvents(business)).containsAll(audit);
        visit(demo);
        runner.run(null);
        assertThat(snapshot(business)).isEqualTo(fresh);
    }

    @Test
    void clearsOnlyTheDemoBusiness() throws Exception {
        User demo = createUser();
        User other = createUser();
        Long business = businessOf(demo).getPkid();
        Long theirs = businessOf(other).getPkid();
        runner(demo, false).run(null);
        visit(demo);
        visit(other);
        Map<String, Object> before = snapshot(theirs);

        DemoReset.Result result = reset.clear(demo.getEmail());

        assertThat(result.cleared()).isTrue();
        assertThat(result.records()).isPositive();
        assertThat(snapshot(business))
                .allSatisfy((kind, figure) -> assertThat(figure).as(kind).isIn(0, BigDecimal.ZERO, List.of()));
        assertThat(snapshot(theirs)).isEqualTo(before);
        assertThat(reset.clear("nobody@florin.test"))
                .isEqualTo(new DemoReset.Result(false, "the demo account nobody@florin.test does not exist", 0));
    }

    @Test
    void realAccountLeftAlone() throws Exception {
        User someone = createUser();
        Long business = businessOf(someone).getPkid();
        visit(someone);
        Map<String, Object> theirs = snapshot(business);

        assertThat(reset.clear(someone.getEmail())).isEqualTo(new DemoReset.Result(
                false, "the account " + someone.getEmail() + " holds no sample data", 0));
        runner(someone, true).run(null);
        runner(someone, true).renew();

        assertThat(snapshot(business)).isEqualTo(theirs);
    }

    @Test
    void sampleClientsChanged() throws Exception {
        User demo = createUser();
        Long business = businessOf(demo).getPkid();
        DemoSeedRunner runner = runner(demo, true);
        runner.run(null);
        Map<String, Object> fresh = snapshot(business);

        List<UUID> clients = jdbcTemplate.queryForList(
                "select id from customers where business_pkid = ? order by pkid", UUID.class, business);
        send(demo, delete("/api/v1/customer/{id}", clients.getFirst()), "").andExpect(status().isOk());
        for (UUID client : clients.subList(1, clients.size())) {
            send(demo, patch("/api/v1/customer/{id}", client), """
                            {"email": "%s@clients.test"}
                            """.formatted(UUID.randomUUID()))
                    .andExpect(status().isOk());
        }

        runner.renew();

        assertThat(snapshot(business)).isEqualTo(fresh);
    }

    @Test
    void ownClientsOnly() throws Exception {
        User someone = createUser();
        Long business = businessOf(someone).getPkid();
        send(someone, post("/api/v1/customer/create"), """
                        {"name": "Own Ltd", "email": "%s@clients.test", "phoneNumber": "+1"}
                        """.formatted(UUID.randomUUID()))
                .andExpect(status().isOk());
        Map<String, Object> theirs = snapshot(business);

        runner(someone, false).run(null);
        runner(someone, true).renew();

        assertThat(snapshot(business)).isEqualTo(theirs);
    }

    @Test
    void resetOff() throws Exception {
        User demo = createUser();
        Long business = businessOf(demo).getPkid();
        runner(demo, false).run(null);
        visit(demo);
        Map<String, Object> visited = snapshot(business);

        runner(demo, false).run(null);

        assertThat(snapshot(business)).isEqualTo(visited);
    }
}
