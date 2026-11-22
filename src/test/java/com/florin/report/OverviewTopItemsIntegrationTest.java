package com.florin.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.florin.IntegrationTest;
import com.florin.user.User;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class OverviewTopItemsIntegrationTest extends IntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User owner;
    private LocalDate today;
    private String client;

    @BeforeEach
    void owner() throws Exception {
        owner = createUser();
        today = LocalDate.now(ZoneOffset.UTC);
        client = JsonPath.read(
                body(send(post("/api/v1/customer/create"), """
                        {"name": "Acme", "email": "%s@clients.test", "phoneNumber": "+1"}
                        """.formatted(UUID.randomUUID()))),
                "$.createdCustomer._id");
    }

    private void inZone(ZoneId zone) throws Exception {
        send(put("/api/v1/settings"), """
                {"defaultCurrency": "USD", "paymentTermsDays": 15, "defaultTaxRate": 0,
                 "taxLabel": "Tax", "timeZone": "%s"}
                """.formatted(zone.getId()))
                .andExpect(status().isOk());
        today = LocalDate.now(zone);
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String json) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private static String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }

    private static String line(String name, String price, String quantity, String discount, String rate) {
        return """
                {"itemName": "%s", "unitPrice": %s, "quantity": %s, "discount": "%s", "taxRate": "%s"}"""
                .formatted(name, price, quantity, discount, rate);
    }

    private static String line(String name, String price, String quantity) {
        return line(name, price, quantity, "0", "0");
    }

    private String create(String type, String currency, LocalDate issued, boolean draft, String... lines)
            throws Exception {
        return JsonPath.read(
                body(send(post("/api/v1/document/create"), """
                                {"customerId": "%s", "documentType": "%s", "currency": "%s", "draft": %s,
                                 "issueDate": "%s", "billingItems": [%s]}
                                """.formatted(client, type, currency, draft, issued, String.join(", ", lines)))
                        .andExpect(status().isOk())),
                "$.newDocument._id");
    }

    private String issue(String type, LocalDate issued, String... lines) throws Exception {
        return create(type, "USD", issued, false, lines);
    }

    private void credit(String document, LocalDate issued, String... lines) throws Exception {
        String note = JsonPath.read(
                body(send(post("/api/v1/document/{id}/credit-note", document), "").andExpect(status().isCreated())),
                "$.newDocument._id");
        send(patch("/api/v1/document/{id}", note), "{\"billingItems\": [%s]}".formatted(String.join(", ", lines)))
                .andExpect(status().isOk());
        send(post("/api/v1/document/{id}/issue", note), "{\"issueDate\": \"%s\"}".formatted(issued))
                .andExpect(status().isOk());
    }

    private void voided(String document) throws Exception {
        send(post("/api/v1/document/{id}/void", document), "{\"reason\": \"Issued twice\"}")
                .andExpect(status().isOk());
    }

    private void rename(String document, int position, String name) {
        jdbcTemplate.update(
                """
                update document_billing_items set item_name = ?
                where position = ? and document_pkid = (select pkid from documents where id = ?)
                """,
                name,
                position,
                UUID.fromString(document));
    }

    private ResultActions overview(String... params) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/v1/report/overview");
        for (int index = 0; index < params.length; index += 2) {
            request.param(params[index], params[index + 1]);
        }
        return send(request, "");
    }

    private static BigDecimal sum(String body, String path) {
        List<Object> amounts = JsonPath.read(body, path);
        return amounts.stream()
                .map(amount -> new BigDecimal(amount.toString()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @ParameterizedTest(name = "in {0}")
    @ArgumentsSource(AwayFromUtc.class)
    void topFive(ZoneId zone) throws Exception {
        inZone(zone);
        String invoice = issue(
                "Invoice",
                today.minusDays(1),
                line("Website design", "450", "3", "0", "10"),
                line("Hosting", "120", "2"),
                line("Copywriting", "150", "4.5", "10", "0"));
        issue("Receipt", today.minusDays(10), line("Logo", "3200", "1"), line("website design", "450", "1"));
        issue("Invoice", today.minusDays(89), line("Consulting", "140", "10"));
        credit(invoice, today, line("Website design", "450", "1", "0", "10"));
        issue("Invoice", today, line("Photography", "900", "1"), line("Social kit", "700", "1"));
        issue("Invoice", today.minusDays(90), line("Logo", "3200", "1"));
        issue("Quotation", today, line("Logo", "9999", "1"));
        create("Invoice", "USD", today, true, line("Logo", "8888", "1"));
        voided(issue("Invoice", today, line("Logo", "7777", "1")));
        create("Invoice", "EUR", today, false, line("Logo", "6666", "1"));

        overview("period", "90d")
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.topItems[*].item",
                        contains("Logo", "Consulting", "Website design", "Photography", "Social kit")))
                .andExpect(jsonPath("$.topItems[*].amount", contains(3200.0, 1400.0, 1350.0, 900.0, 700.0)))
                .andExpect(jsonPath("$.topItems[*].quantity", contains(1, 10, 3, 1, 1)))
                .andExpect(jsonPath("$.topItems[*].documents", contains(1, 1, 3, 1, 1)))
                .andExpect(jsonPath("$.itemsTotal").value(8397.5))
                .andExpect(content().string(containsString(
                        "{\"item\":\"Logo\",\"quantity\":1,\"amount\":3200.00,\"documents\":1}")));
        overview("period", "30d")
                .andExpect(jsonPath(
                        "$.topItems[*].item",
                        contains("Logo", "Website design", "Photography", "Social kit", "Copywriting")))
                .andExpect(jsonPath("$.topItems[*].amount", contains(3200.0, 1350.0, 900.0, 700.0, 607.5)))
                .andExpect(jsonPath("$.topItems[*].quantity", contains(1, 3, 1, 1, 4.5)))
                .andExpect(jsonPath("$.itemsTotal").value(6997.5));
        overview("period", "12m")
                .andExpect(jsonPath(
                        "$.topItems[*].item",
                        contains("Logo", "Consulting", "Website design", "Photography", "Social kit")))
                .andExpect(jsonPath("$.topItems[*].amount", contains(6400.0, 1400.0, 1350.0, 900.0, 700.0)))
                .andExpect(jsonPath("$.topItems[*].quantity", contains(2, 10, 3, 1, 1)))
                .andExpect(jsonPath("$.topItems[*].documents", contains(2, 1, 3, 1, 1)))
                .andExpect(jsonPath("$.itemsTotal").value(11597.5));
    }

    @ParameterizedTest(name = "in {0}")
    @ArgumentsSource(AwayFromUtc.class)
    void addsUpToIncome(ZoneId zone) throws Exception {
        inZone(zone);
        LocalDate year = today.minusMonths(12).plusDays(1);
        LocalDate january = today.withDayOfYear(1);
        List<LocalDate> days = List.of(
                today,
                today.minusDays(29),
                today.minusDays(30),
                today.minusDays(89),
                today.minusDays(90),
                year,
                year.minusDays(1),
                january,
                january.minusDays(1));
        for (int index = 0; index < days.size(); index++) {
            LocalDate day = days.get(index);
            String invoice = issue(
                    "Invoice",
                    day,
                    line("Design", "33.3333", "1.5", "12.5", "18"),
                    line("Hours", "19.99", "2.333", "7.25", "5"),
                    line("Support", "0.005" + index, "1"));
            issue("Receipt", day, line("Kit", "1234567.8912", "3.3333", "33.3333", "0"), line("Hours", "45", "0.5"));
            if (index % 2 == 0) {
                credit(invoice, day, line("Hours", "19.99", "1.111", "7.25", "5"));
            }
        }
        String unnamed = issue("Invoice", today, line("Kept", "60.125", "1"), line("Gone", "15.0049", "3"));
        rename(unnamed, 0, null);
        rename(unnamed, 1, " ");

        for (String period : List.of("30d", "90d", "12m", "ytd")) {
            String overview = body(overview("period", period).andExpect(status().isOk()));
            BigDecimal income = sum(overview, "$.trend[*].income");
            assertThat(income).as(period).isPositive();
            assertThat(new BigDecimal(JsonPath.read(overview, "$.itemsTotal").toString()))
                    .as(period)
                    .isEqualByComparingTo(income);
            assertThat(new BigDecimal(JsonPath.read(overview, "$.totals.profit.current").toString()))
                    .as(period)
                    .isEqualByComparingTo(income);
            assertThat(JsonPath.<List<String>>read(overview, "$.topItems[*].item"))
                    .as(period)
                    .containsExactlyInAnyOrder("Kit", "Hours", "Design", "Support", "Other lines");
            assertThat(sum(overview, "$.topItems[*].amount")).as(period).isEqualByComparingTo(income);
        }
    }

    @Test
    void spellingsAndTies() throws Exception {
        issue("Invoice", today, line("Consulting", "100", "1"), line("beta", "100", "1"));
        issue("Invoice", today, line("CONSULTING", "100", "2"), line("Alpha", "100", "1"));
        issue(
                "Receipt",
                today,
                line("consulting", "50", "1"),
                line("gamma", "100", "1"),
                line("Delta", "100", "1"),
                line("epsilon", "100", "1"));
        String unnamed = issue("Invoice", today, line("Kept", "160", "1"), line("Gone", "40", "1"));
        rename(unnamed, 0, null);
        rename(unnamed, 1, "");

        overview()
                .andExpect(jsonPath(
                        "$.topItems[*].item", contains("Consulting", "Other lines", "Alpha", "beta", "Delta")))
                .andExpect(jsonPath("$.topItems[*].amount", contains(350.0, 200.0, 100.0, 100.0, 100.0)))
                .andExpect(jsonPath("$.topItems[*].quantity", contains(4, 2, 1, 1, 1)))
                .andExpect(jsonPath("$.topItems[*].documents", contains(3, 1, 1, 1, 1)))
                .andExpect(jsonPath("$.itemsTotal").value(1050.0));
    }

    @Test
    void noPrice() throws Exception {
        String invoice = JsonPath.read(
                body(send(post("/api/v1/document/create"), """
                                {"customerId": "%s", "documentType": "Invoice", "currency": "USD", "issueDate": "%s",
                                 "billingItems": [{"itemName": "Gift", "quantity": 2}, %s]}
                                """.formatted(client, today, line("Design", "500", "1")))
                        .andExpect(status().isOk())),
                "$.newDocument._id");

        overview()
                .andExpect(jsonPath("$.topItems[*].item", contains("Design", "Gift")))
                .andExpect(jsonPath("$.topItems[*].amount", contains(500.0, 0.0)))
                .andExpect(jsonPath("$.topItems[*].quantity", contains(1, 2)))
                .andExpect(jsonPath("$.topItems[*].documents", contains(1, 1)))
                .andExpect(jsonPath("$.itemsTotal").value(500.0))
                .andExpect(content().string(containsString("{\"item\":\"Gift\",\"quantity\":2,\"amount\":0.00,")));
        send(get("/api/v1/document/{id}", invoice), "")
                .andExpect(jsonPath("$.document.subTotal").value(500.0))
                .andExpect(jsonPath("$.document.billingItems[0].lineTotal").value(0.0));
    }

    @Test
    void currencyAndEmpty() throws Exception {
        overview().andExpect(content().string(containsString("\"topItems\":[],\"itemsTotal\":0.00")));

        issue("Invoice", today, line("Design", "500", "1"));
        create("Invoice", "EUR", today, false, line("Design", "300", "1"), line("Hosting", "100", "1"));
        issue("Invoice", today.minusDays(40), line("Hosting", "120", "1"));

        overview()
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.topItems[*].item", contains("Design", "Hosting")))
                .andExpect(jsonPath("$.topItems[*].amount", contains(500.0, 120.0)))
                .andExpect(jsonPath("$.itemsTotal").value(620.0));
        overview("currency", "EUR")
                .andExpect(jsonPath("$.topItems[*].item", contains("Design", "Hosting")))
                .andExpect(jsonPath("$.topItems[*].amount", contains(300.0, 100.0)))
                .andExpect(jsonPath("$.itemsTotal").value(400.0));
        overview("period", "30d", "currency", "USD")
                .andExpect(jsonPath("$.topItems[*].item", contains("Design")))
                .andExpect(jsonPath("$.itemsTotal").value(500.0));

        owner = createUser();
        client = JsonPath.read(
                body(send(post("/api/v1/customer/create"), """
                        {"name": "Birch", "email": "%s@clients.test", "phoneNumber": "+1"}
                        """.formatted(UUID.randomUUID()))),
                "$.createdCustomer._id");
        issue("Invoice", today.minusDays(40), line("Design", "500", "1"));
        overview("period", "30d")
                .andExpect(content().string(containsString("\"topItems\":[],\"itemsTotal\":0.00")));
    }
}
