package com.florin.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.florin.IntegrationTest;
import com.florin.business.BusinessRole;
import com.florin.report.AwayFromUtc;
import com.florin.user.User;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
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

class DocumentListIntegrationTest extends IntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private LocalDate today;
    private User owner;
    private String client;

    @BeforeEach
    void owner() throws Exception {
        owner = createUser();
        client = client(owner);
        today = LocalDate.now(ZoneOffset.UTC);
    }

    private void inZone(ZoneId zone) throws Exception {
        send(put("/api/v1/settings"), """
                {"defaultCurrency": "USD", "paymentTermsDays": 15, "defaultTaxRate": 0,
                 "taxLabel": "Tax", "timeZone": "%s"}
                """.formatted(zone.getId()))
                .andExpect(status().isOk());
        today = LocalDate.now(zone);
    }

    private String client(User user) throws Exception {
        return JsonPath.read(
                body(mockMvc.perform(post("/api/v1/customer/create")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Acme\", \"email\": \"acme-%s@clients.test\", \"phoneNumber\": \"+1\"}"
                                .formatted(UUID.randomUUID())))),
                "$.createdCustomer._id");
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String json) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private static String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private String create(
            String type, String currency, String amount, LocalDate issued, LocalDate due, boolean draft)
            throws Exception {
        return JsonPath.read(
                body(send(post("/api/v1/document/create"), """
                                {"customerId": "%s", "documentType": "%s", "currency": %s, "draft": %s,
                                 "issueDate": "%s", "dueDate": %s,
                                 "billingItems": [{"itemName": "Work", "unitPrice": %s, "quantity": 1}]}
                                """.formatted(
                                        client,
                                        type,
                                        currency == null ? "null" : "\"" + currency + "\"",
                                        draft,
                                        issued,
                                        due == null ? "null" : "\"" + due + "\"",
                                        amount))
                        .andExpect(status().isOk())),
                "$.newDocument._id");
    }

    private String create(String type, String amount, LocalDate issued, LocalDate due, boolean draft)
            throws Exception {
        return create(type, "USD", amount, issued, due, draft);
    }

    private String issue(String type, String amount, LocalDate issued, LocalDate due) throws Exception {
        return create(type, "USD", amount, issued, due, false);
    }

    private String issue(String type, String currency, String amount, LocalDate issued, LocalDate due)
            throws Exception {
        return create(type, currency, amount, issued, due, false);
    }

    private void pay(String document, String amount, String withheld, LocalDate date) throws Exception {
        send(post("/api/v1/document/{id}/payment", document), """
                {"amountPaid": %s, "tdsAmount": %s, "datePaid": "%s", "paymentMethod": "Bank Transfer"}
                """.formatted(amount, withheld, date))
                .andExpect(status().isCreated());
    }

    private void credit(String document, String amount, LocalDate issued) throws Exception {
        String note = JsonPath.read(
                body(send(post("/api/v1/document/{id}/credit-note", document), "")
                        .andExpect(status().isCreated())),
                "$.newDocument._id");
        send(patch("/api/v1/document/{id}", note), """
                {"billingItems": [{"itemName": "Work", "unitPrice": %s, "quantity": 1}]}
                """.formatted(amount))
                .andExpect(status().isOk());
        send(post("/api/v1/document/{id}/issue", note), "{\"issueDate\": \"%s\"}".formatted(issued))
                .andExpect(status().isOk());
    }

    private void refund(String document, String amount, LocalDate date) throws Exception {
        send(post("/api/v1/document/{id}/refund", document), """
                {"amount": %s, "dateRefunded": "%s", "paymentMethod": "Bank Transfer"}
                """.formatted(amount, date))
                .andExpect(status().isCreated());
    }

    private void voided(String document) throws Exception {
        send(post("/api/v1/document/{id}/void", document), "{\"reason\": \"Issued twice\"}")
                .andExpect(status().isOk());
    }

    private void dueAt(String document, LocalDateTime moment, ZoneId zone) {
        jdbcTemplate.update(
                "update documents set due_date = ? where id = ?",
                Timestamp.from(moment.atZone(zone).toInstant()),
                UUID.fromString(document));
    }

    private ResultActions list(String... params) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/v1/document/all").param("size", "100");
        for (int index = 0; index < params.length; index += 2) {
            request.param(params[index], params[index + 1]);
        }
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer(owner)));
    }

    private List<String> csv(String... params) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/v1/document/csv");
        for (int index = 0; index < params.length; index += 2) {
            request.param(params[index], params[index + 1]);
        }
        return body(mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                        .andExpect(status().isOk()))
                .lines()
                .skip(1)
                .map(line -> line.substring(0, line.indexOf(',')))
                .toList();
    }

    private void owedIn(String week, String owed, List<String> invoices, List<LocalDate> days) throws Exception {
        String listed = body(list("outstanding", "true", "due", week, "currency", "USD")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myDocuments[*].documentNumber", containsInAnyOrder(invoices.toArray())))
                .andExpect(jsonPath("$.totalDocuments").value(invoices.size()))
                .andExpect(jsonPath("$.totals[0].documents").value(invoices.size()))
                .andExpect(jsonPath(
                        "$.myDocuments[*].dueDate",
                        containsInAnyOrder(days.stream()
                                .map(day -> day == null ? null : day.toString())
                                .toArray()))));
        assertThat(sum(listed, "$.myDocuments[*].ledger.balanceDue")).isEqualByComparingTo(owed);
        assertThat(sum(listed, "$.totals[*].balanceDue")).isEqualByComparingTo(owed);
    }

    private static BigDecimal sum(String body, String path) {
        List<Object> amounts = JsonPath.read(body, path);
        return amounts.stream()
                .filter(Objects::nonNull)
                .map(amount -> new BigDecimal(amount.toString()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal amount(String body, String path) {
        return new BigDecimal(JsonPath.read(body, path).toString());
    }

    private void books() throws Exception {
        issue("Invoice", "100", today.minusDays(5), today.plusDays(10));
        issue("Invoice", "200", today.minusDays(20), today.minusDays(1));
        pay(issue("Invoice", "300", today.minusDays(15), today.plusDays(5)), "100", "20", today.minusDays(2));
        pay(issue("Invoice", "70", today.minusDays(10), today.plusDays(20)), "70", "0", today.minusDays(1));
        credit(issue("Invoice", "400", today.minusDays(8), today.plusDays(30)), "150", today.minusDays(1));
        credit(issue("Invoice", "50", today.minusDays(8), today.plusDays(30)), "50", today);
        String refunded = issue("Invoice", "200", today.minusDays(30), today.minusDays(15));
        pay(refunded, "200", "0", today.minusDays(30));
        credit(refunded, "80", today.minusDays(4));
        refund(refunded, "80", today.minusDays(2));
        issue("Receipt", "60", today, null);
        issue("Quotation", "900", today.minusDays(2), today.plusDays(10));
        create("Invoice", "500", today.minusDays(1), today.plusDays(10), true);
        voided(issue("Invoice", "600", today.minusDays(1), today.plusDays(10)));
    }

    @Test
    void outstandingListsOwedInvoices() throws Exception {
        books();

        String owed = body(list("outstanding", "true")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDocuments").value(4))
                .andExpect(jsonPath(
                        "$.myDocuments[*].documentNumber",
                        containsInAnyOrder("INV-0001", "INV-0002", "INV-0003", "INV-0005")))
                .andExpect(jsonPath("$.myDocuments[*].documentType", everyItem(is("Invoice")))));
        String overview = body(send(get("/api/v1/report/overview").param("currency", "USD"), "")
                .andExpect(jsonPath("$.owedNow.invoices").value(4))
                .andExpect(jsonPath("$.owedNow.total").value(730.0)));
        assertThat(sum(owed, "$.myDocuments[*].ledger.balanceDue"))
                .isEqualByComparingTo(amount(overview, "$.owedNow.total"));

        list("outstanding", "true", "overdue", "true")
                .andExpect(jsonPath("$.myDocuments[*].documentNumber", containsInAnyOrder("INV-0002")));
        list("outstanding", "true", "status", "Not Fully Paid")
                .andExpect(jsonPath("$.myDocuments[*].documentNumber", containsInAnyOrder("INV-0003")));
        list("outstanding", "true", "q", "inv-0005")
                .andExpect(jsonPath("$.myDocuments[*].documentNumber", containsInAnyOrder("INV-0005")));
        list("outstanding", "true", "sort", "dueDate", "dir", "asc")
                .andExpect(jsonPath(
                        "$.myDocuments[*].documentNumber", contains("INV-0002", "INV-0003", "INV-0001", "INV-0005")));
        list("outstanding", "true", "type", "Receipt").andExpect(jsonPath("$.totalDocuments").value(0));
        list("outstanding", "false").andExpect(jsonPath("$.totalDocuments").value(14));
    }

    @Test
    void filtersByPaymentStatus() throws Exception {
        books();

        list("status", "Not Paid")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDocuments").value(3))
                .andExpect(jsonPath(
                        "$.myDocuments[*].documentNumber", containsInAnyOrder("INV-0001", "INV-0002", "INV-0005")))
                .andExpect(jsonPath("$.totals[0].documents").value(3))
                .andExpect(jsonPath("$.totals[0].billed").value(700.0));
        list("status", "Not Fully Paid")
                .andExpect(jsonPath("$.myDocuments[*].documentNumber", containsInAnyOrder("INV-0003")));
        list("status", "Paid")
                .andExpect(jsonPath(
                        "$.myDocuments[*].documentNumber",
                        containsInAnyOrder("INV-0004", "INV-0006", "INV-0007", "REC-0001")));
        list("status", "Not Paid", "type", "Quotation").andExpect(jsonPath("$.totalDocuments").value(0));
        assertThat(csv("status", "Not Paid", "sort", "number", "dir", "asc"))
                .containsExactly("INV-0001", "INV-0002", "INV-0005");
        assertThat(csv("type", "Quotation")).containsExactly("QUO-0001");
    }

    @Test
    void billedListsIssuedDocuments() throws Exception {
        books();

        list("billed", "true")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDocuments").value(11))
                .andExpect(jsonPath(
                        "$.myDocuments[*].documentNumber",
                        containsInAnyOrder(
                                "INV-0001", "INV-0002", "INV-0003", "INV-0004", "INV-0005", "INV-0006", "INV-0007",
                                "CN-0001", "CN-0002", "CN-0003", "REC-0001")));
        BigDecimal invoices = sum(body(list("billed", "true", "type", "Invoice")), "$.myDocuments[*].total");
        BigDecimal receipts = sum(body(list("billed", "true", "type", "Receipt")), "$.myDocuments[*].total");
        BigDecimal notes = sum(body(list("billed", "true", "type", "Credit Note")), "$.myDocuments[*].total");
        assertThat(invoices.add(receipts).subtract(notes))
                .isEqualByComparingTo(amount(
                        body(send(get("/api/v1/report/overview").param("currency", "USD"), "")),
                        "$.expectedIncome"))
                .isEqualByComparingTo("1100");

        list("billed", "true", "type", "Credit Note")
                .andExpect(jsonPath(
                        "$.myDocuments[*].documentNumber", containsInAnyOrder("CN-0001", "CN-0002", "CN-0003")));
        list("billed", "true", "type", "Quotation").andExpect(jsonPath("$.totalDocuments").value(0));
        list("billed", "true", "state", "draft").andExpect(jsonPath("$.totalDocuments").value(0));
        list("billed", "true", "state", "void").andExpect(jsonPath("$.totalDocuments").value(0));
        list("billed", "true", "outstanding", "true")
                .andExpect(jsonPath(
                        "$.myDocuments[*].documentNumber",
                        containsInAnyOrder("INV-0001", "INV-0002", "INV-0003", "INV-0005")));
    }

    @ParameterizedTest(name = "in {0}")
    @ArgumentsSource(AwayFromUtc.class)
    void periodsFollowBusinessCalendar(ZoneId zone) throws Exception {
        inZone(zone);
        LocalDate year = today.minusMonths(12).plusDays(1);
        issue("Invoice", "10", today, today);
        issue("Invoice", "20", today.minusDays(29), today);
        issue("Invoice", "30", today.minusDays(30), today);
        issue("Invoice", "40", today.minusDays(89), today);
        issue("Invoice", "50", today.minusDays(90), today);
        issue("Invoice", "60", year, today);
        issue("Invoice", "70", year.minusDays(1), today);

        list("period", "30d")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issuedFrom").value(today.minusDays(29).toString()))
                .andExpect(jsonPath("$.issuedTo").value(today.toString()))
                .andExpect(jsonPath("$.myDocuments[*].documentNumber", containsInAnyOrder("INV-0001", "INV-0002")));
        list("period", "90D")
                .andExpect(jsonPath("$.issuedFrom").value(today.minusDays(89).toString()))
                .andExpect(jsonPath(
                        "$.myDocuments[*].documentNumber",
                        containsInAnyOrder("INV-0001", "INV-0002", "INV-0003", "INV-0004")));
        list("period", "12m", "billed", "true")
                .andExpect(jsonPath("$.issuedFrom").value(year.toString()))
                .andExpect(jsonPath("$.issuedTo").value(today.toString()))
                .andExpect(jsonPath(
                        "$.myDocuments[*].documentNumber",
                        containsInAnyOrder("INV-0001", "INV-0002", "INV-0003", "INV-0004", "INV-0005", "INV-0006")));
        list("from", today.minusDays(30).toString())
                .andExpect(jsonPath("$.issuedFrom").value(today.minusDays(30).toString()))
                .andExpect(jsonPath("$.issuedTo").value(nullValue()))
                .andExpect(jsonPath("$.totalDocuments").value(3));
        list().andExpect(jsonPath("$.issuedFrom").value(nullValue())).andExpect(jsonPath("$.issuedTo").value(nullValue()));
        for (String period : List.of("30d", "90d", "12m")) {
            LocalDate from = LocalDate.parse(JsonPath.read(
                    body(send(get("/api/v1/report/overview").param("period", period), "")), "$.period.from"));
            String listed = body(list("period", period));
            List<String> dates = JsonPath.read(listed, "$.myDocuments[*].issueDate");
            assertThat(dates).contains(from.toString()).doesNotContain(from.minusDays(1).toString());
            assertThat((String) JsonPath.read(listed, "$.issuedFrom")).isEqualTo(from.toString());
        }
    }

    @ParameterizedTest(name = "in {0}")
    @ArgumentsSource(AwayFromUtc.class)
    void dueFilterGroupsByWeek(ZoneId zone) throws Exception {
        inZone(zone);
        issue("Invoice", "100", today.minusDays(3), today.minusDays(1));
        issue("Invoice", "200", today.minusDays(3), today);
        issue("Invoice", "50", today.minusDays(3), today.plusDays(6));
        issue("Invoice", "300", today.minusDays(3), today.plusDays(7));
        pay(issue("Invoice", "250", today.minusDays(3), today.plusDays(13)), "100", "0", today);
        issue("Invoice", "30", today.minusDays(3), today.plusDays(14));
        issue("Invoice", "400", today.minusDays(3), today.plusDays(20));
        issue("Invoice", "500", today.minusDays(3), today.plusDays(21));
        String undated = issue("Invoice", "600", today.minusDays(3), today.plusDays(40));
        jdbcTemplate.update("update documents set due_date = null where id = ?", UUID.fromString(undated));
        pay(issue("Invoice", "70", today.minusDays(3), today.plusDays(2)), "70", "0", today);
        issue("Invoice", "EUR", "80", today.minusDays(3), today.plusDays(2));
        issue("Receipt", "60", today, null);
        dueAt(issue("Invoice", "20", today.minusDays(3), today.plusDays(6)), today.plusDays(6).atTime(23, 30), zone);
        dueAt(issue("Invoice", "40", today.minusDays(3), today.plusDays(7)), today.plusDays(7).atTime(0, 30), zone);

        owedIn("this-week", "270", List.of("INV-0002", "INV-0003", "INV-0012"),
                List.of(today, today.plusDays(6), today.plusDays(6)));
        owedIn("next-week", "490", List.of("INV-0004", "INV-0005", "INV-0013"),
                List.of(today.plusDays(7), today.plusDays(13), today.plusDays(7)));
        owedIn("week-after", "430", List.of("INV-0006", "INV-0007"), List.of(today.plusDays(14), today.plusDays(20)));
        owedIn("later", "1100", List.of("INV-0008", "INV-0009"), Arrays.asList(today.plusDays(21), null));
        list("due", "this-week")
                .andExpect(jsonPath(
                        "$.myDocuments[*].documentNumber",
                        containsInAnyOrder("INV-0002", "INV-0003", "INV-0010", "INV-0011", "INV-0012")));
        list("due", "later")
                .andExpect(jsonPath(
                        "$.myDocuments[*].documentNumber", containsInAnyOrder("INV-0008", "INV-0009", "REC-0001")));
        list("outstanding", "true", "due", "This-Week", "currency", "EUR")
                .andExpect(jsonPath("$.myDocuments[*].documentNumber", containsInAnyOrder("INV-0011")));
    }

    @ParameterizedTest(name = "in {0}")
    @ArgumentsSource(AwayFromUtc.class)
    void currentListsNotYetDue(ZoneId zone) throws Exception {
        inZone(zone);
        dueAt(issue("Invoice", "100", today.minusDays(3), today), today.atTime(0, 30), zone);
        issue("Invoice", "200", today.minusDays(3), today.plusDays(40));
        pay(issue("Invoice", "300", today.minusDays(3), today.plusDays(5)), "100", "20", today);
        credit(issue("Invoice", "400", today.minusDays(3), today.plusDays(16)), "150", today);
        String undated = issue("Invoice", "500", today.minusDays(3), today.plusDays(10));
        jdbcTemplate.update("update documents set due_date = null where id = ?", UUID.fromString(undated));
        dueAt(issue("Invoice", "60", today.minusDays(3), today), today.minusDays(1).atTime(23, 30), zone);
        issue("Invoice", "70", today.minusDays(20), today.minusDays(7));
        pay(issue("Invoice", "80", today.minusDays(3), today.plusDays(5)), "80", "0", today);
        issue("Receipt", "90", today, null);
        issue("Quotation", "900", today, today.plusDays(10));
        create("Invoice", "800", today, today.plusDays(10), true);
        voided(issue("Invoice", "700", today.minusDays(1), today.plusDays(10)));
        issue("Invoice", "EUR", "600", today.minusDays(3), today.plusDays(10));

        String dollars = body(list("due", "current", "currency", "USD", "sort", "dueDate", "dir", "asc")
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.myDocuments[*].documentNumber",
                        contains("INV-0001", "INV-0003", "INV-0004", "INV-0002", "INV-0005")))
                .andExpect(jsonPath("$.myDocuments[*].balanceDue", contains(100.0, 180.0, 250.0, 200.0, 500.0)))
                .andExpect(jsonPath("$.myDocuments[4].dueDate").value(nullValue()))
                .andExpect(jsonPath("$.totalDocuments").value(5))
                .andExpect(jsonPath("$.totals.length()").value(1))
                .andExpect(jsonPath("$.totals[0].currency").value("USD"))
                .andExpect(jsonPath("$.totals[0].documents").value(5))
                .andExpect(jsonPath("$.totals[0].billed").value(1500.0))
                .andExpect(jsonPath("$.totals[0].balanceDue").value(1230.0))
                .andExpect(jsonPath("$.totals[0].overdue").value(0.0))
                .andExpect(jsonPath("$.totals[0].overdueDocuments").value(0)));
        for (String currency : List.of("USD", "EUR")) {
            String listed = currency.equals("USD") ? dollars : body(list("due", "current", "currency", currency));
            String overview = body(send(get("/api/v1/report/overview").param("currency", currency), "")
                    .andExpect(jsonPath("$.owedNow.bands[0].key").value("current")));
            assertThat(amount(listed, "$.totals[0].balanceDue"))
                    .as(currency)
                    .isEqualByComparingTo(amount(overview, "$.owedNow.bands[0].amount"))
                    .isEqualByComparingTo(amount(overview, "$.owedNow.notDue"))
                    .isEqualByComparingTo(sum(listed, "$.myDocuments[*].balanceDue"));
            assertThat((Integer) JsonPath.read(listed, "$.totalDocuments"))
                    .as(currency)
                    .isEqualTo(JsonPath.read(overview, "$.owedNow.bands[0].invoices"));
        }
        assertThat(amount(body(list("due", "current", "currency", "EUR")), "$.totals[0].balanceDue"))
                .isEqualByComparingTo("600");
        list("due", "Current")
                .andExpect(jsonPath("$.totalDocuments").value(6))
                .andExpect(jsonPath("$.totals[*].currency", contains("EUR", "USD")))
                .andExpect(jsonPath("$.totals[*].balanceDue", contains(600.0, 1230.0)));
        list("due", "current", "outstanding", "true", "currency", "USD")
                .andExpect(jsonPath(
                        "$.myDocuments[*].documentNumber",
                        containsInAnyOrder("INV-0001", "INV-0002", "INV-0003", "INV-0004", "INV-0005")));
        mockMvc.perform(get("/api/v1/document/all")
                        .param("due", "current")
                        .param("currency", "USD")
                        .param("sort", "dueDate")
                        .param("dir", "asc")
                        .param("size", "2")
                        .param("page", "2")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(jsonPath("$.myDocuments[*].documentNumber", contains("INV-0004", "INV-0002")))
                .andExpect(jsonPath("$.totalDocuments").value(5))
                .andExpect(jsonPath("$.numberOfPages").value(3))
                .andExpect(jsonPath("$.totals[0].balanceDue").value(1230.0));
        assertThat(csv("due", "current", "currency", "USD", "sort", "number", "dir", "asc"))
                .containsExactly("INV-0001", "INV-0002", "INV-0003", "INV-0004", "INV-0005");
        User viewer = createMember(businessOf(owner), BusinessRole.VIEWER);
        mockMvc.perform(get("/api/v1/document/all")
                        .param("due", "current")
                        .param("currency", "USD")
                        .header(HttpHeaders.AUTHORIZATION, bearer(viewer, businessOf(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDocuments").value(5))
                .andExpect(jsonPath("$.totals[0].balanceDue").value(1230.0));
        owner = createUser();
        list("due", "current")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myDocuments").isEmpty())
                .andExpect(jsonPath("$.totals").isEmpty())
                .andExpect(jsonPath("$.totalDocuments").value(0))
                .andExpect(jsonPath("$.numberOfPages").value(0));
    }

    @Test
    void filtersByCurrency() throws Exception {
        issue("Invoice", "USD", "100", today, today);
        issue("Invoice", "EUR", "200", today, today);
        issue("Receipt", "EUR", "50", today, null);
        issue("Invoice", null, "70", today, today);

        list("currency", "EUR")
                .andExpect(jsonPath("$.myDocuments[*].documentNumber", containsInAnyOrder("INV-0002", "REC-0001")));
        list("currency", "USD")
                .andExpect(jsonPath("$.myDocuments[*].documentNumber", containsInAnyOrder("INV-0001", "INV-0003")));
        list("currency", "GBP").andExpect(jsonPath("$.totalDocuments").value(0));
        list("currency", " ").andExpect(jsonPath("$.totalDocuments").value(4));
        list("currency", "EUR", "billed", "true", "type", "Receipt")
                .andExpect(jsonPath("$.myDocuments[*].documentNumber", containsInAnyOrder("REC-0001")));
    }

    @Test
    void balanceDue() throws Exception {
        books();
        String overpaid = issue("Invoice", "90", today.minusDays(3), today.plusDays(3));
        pay(overpaid, "90", "0", today);
        credit(overpaid, "30", today);

        String all = body(list()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myDocuments[?(@.documentNumber == 'INV-0001')].balanceDue", contains(100.0)))
                .andExpect(jsonPath("$.myDocuments[?(@.documentNumber == 'INV-0002')].balanceDue", contains(200.0)))
                .andExpect(jsonPath("$.myDocuments[?(@.documentNumber == 'INV-0003')].balanceDue", contains(180.0)))
                .andExpect(jsonPath("$.myDocuments[?(@.documentNumber == 'INV-0004')].balanceDue", contains(0.0)))
                .andExpect(jsonPath("$.myDocuments[?(@.documentNumber == 'INV-0005')].balanceDue", contains(250.0)))
                .andExpect(jsonPath("$.myDocuments[?(@.documentNumber == 'INV-0006')].balanceDue", contains(0.0)))
                .andExpect(jsonPath("$.myDocuments[?(@.documentNumber == 'INV-0007')].balanceDue", contains(0.0)))
                .andExpect(jsonPath("$.myDocuments[?(@.documentNumber == 'INV-0009')].balanceDue", contains(0.0)))
                .andExpect(jsonPath("$.myDocuments[?(@.documentType != 'Invoice')].balanceDue", hasSize(6)))
                .andExpect(jsonPath("$.myDocuments[?(@.documentType != 'Invoice')].balanceDue", everyItem(nullValue())))
                .andExpect(jsonPath("$.myDocuments[?(@.state != 'issued')].state", containsInAnyOrder("draft", "void")))
                .andExpect(jsonPath("$.myDocuments[?(@.state != 'issued')].balanceDue", everyItem(nullValue())))
                .andExpect(content().string(containsString(
                        "\"totalAmountReceived\":90.00,\"balanceDue\":0.00,\"ledger\":"))));
        assertThat(sum(all, "$.myDocuments[*].balanceDue")).isEqualByComparingTo(sum(all, "$.totals[*].balanceDue"));
    }

    @Test
    void totalsCoverAllPages() throws Exception {
        books();
        pay(issue("Invoice", "EUR", "500", today.minusDays(10), today.minusDays(3)), "200", "0", today.minusDays(1));
        issue("Receipt", "EUR", "40", today.minusDays(1), null);
        issue("Quotation", "EUR", "999", today, today.plusDays(10));
        create("Invoice", "EUR", "800", today, today.plusDays(10), true);
        voided(issue("Invoice", "EUR", "600", today, today.plusDays(10)));
        issue("Invoice", null, "70", today, today.plusDays(5));

        String all = body(list()
                .andExpect(jsonPath("$.totalDocuments").value(20))
                .andExpect(jsonPath("$.totals.length()").value(2))
                .andExpect(jsonPath("$.totals[0].currency").value("EUR"))
                .andExpect(jsonPath("$.totals[0].documents").value(5))
                .andExpect(jsonPath("$.totals[0].billed").value(540.0))
                .andExpect(jsonPath("$.totals[0].balanceDue").value(300.0))
                .andExpect(jsonPath("$.totals[0].overdue").value(300.0))
                .andExpect(jsonPath("$.totals[0].overdueDocuments").value(1))
                .andExpect(jsonPath("$.totals[1].currency").value("USD"))
                .andExpect(jsonPath("$.totals[1].documents").value(15))
                .andExpect(jsonPath("$.totals[1].billed").value(1170.0))
                .andExpect(jsonPath("$.totals[1].balanceDue").value(800.0))
                .andExpect(jsonPath("$.totals[1].overdue").value(200.0))
                .andExpect(jsonPath("$.totals[1].overdueDocuments").value(1))
                .andExpect(content().string(containsString(
                        "{\"currency\":\"EUR\",\"documents\":5,\"billed\":540.00,\"balanceDue\":300.00,"
                                + "\"overdue\":300.00,\"overdueDocuments\":1}"))));
        assertThat(sum(all, "$.myDocuments[*].balanceDue")).isEqualByComparingTo(sum(all, "$.totals[*].balanceDue"));
        String paged = body(mockMvc.perform(get("/api/v1/document/all")
                        .param("size", "1")
                        .param("page", "3")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(jsonPath("$.myDocuments.length()").value(1))
                .andExpect(jsonPath("$.totalDocuments").value(20))
                .andExpect(jsonPath("$.numberOfPages").value(20)));
        assertThat((Object) JsonPath.read(paged, "$.totals")).isEqualTo(JsonPath.read(all, "$.totals"));

        list("outstanding", "true", "currency", "USD")
                .andExpect(jsonPath("$.totals.length()").value(1))
                .andExpect(jsonPath("$.totals[0].documents").value(5))
                .andExpect(jsonPath("$.totals[0].billed").value(1070.0))
                .andExpect(jsonPath("$.totals[0].balanceDue").value(800.0))
                .andExpect(jsonPath("$.totals[0].overdue").value(200.0))
                .andExpect(jsonPath("$.totals[0].overdueDocuments").value(1));
        list("type", "Quotation")
                .andExpect(jsonPath("$.totals[*].currency", contains("EUR", "USD")))
                .andExpect(jsonPath("$.totals[*].documents", contains(1, 1)))
                .andExpect(jsonPath("$.totals[*].billed", contains(0.0, 0.0)))
                .andExpect(jsonPath("$.totals[*].balanceDue", contains(0.0, 0.0)));
        list("type", "Credit Note").andExpect(jsonPath("$.totals[0].billed").value(-280.0));
        list("currency", "GBP")
                .andExpect(jsonPath("$.totals").isEmpty())
                .andExpect(jsonPath("$.totalDocuments").value(0))
                .andExpect(jsonPath("$.numberOfPages").value(0));
    }

    @ParameterizedTest(name = "in {0}")
    @ArgumentsSource(AwayFromUtc.class)
    void addUpToTheOverview(ZoneId zone) throws Exception {
        inZone(zone);
        LocalDate year = today.minusMonths(12).plusDays(1);
        pay(issue("Invoice", "1000", today.minusDays(29), today.minusDays(1)), "400", "50", today);
        issue("Invoice", "300", today.minusDays(30), today);
        credit(issue("Invoice", "800", today.minusDays(89), today.plusDays(10)), "100", today.minusDays(2));
        issue("Invoice", "200", today.minusDays(90), today.minusDays(60));
        String refunded = issue("Invoice", "600", year, today.plusDays(1));
        pay(refunded, "600", "0", year);
        credit(refunded, "150", today);
        refund(refunded, "150", today);
        issue("Invoice", "700", year.minusDays(1), today.plusDays(3));
        issue("Receipt", "90", today, null);
        dueAt(issue("Invoice", "40", today.minusDays(3), today), today.minusDays(1).atTime(23, 30), zone);
        dueAt(issue("Invoice", "60", today.minusDays(3), today), today.atTime(0, 30), zone);
        issue("Quotation", "5000", today, today.plusDays(5));
        create("Invoice", "6000", today, today.plusDays(5), true);
        voided(issue("Invoice", "7000", today, today.plusDays(5)));

        List<String> periods = List.of("30d", "90d", "12m");
        List<String> billed = List.of("940", "2040", "2840");
        for (int index = 0; index < periods.size(); index++) {
            String overview = body(send(
                    get("/api/v1/report/overview").param("currency", "USD").param("period", periods.get(index)),
                    ""));
            String listed = body(list("billed", "true", "period", periods.get(index), "currency", "USD"));
            assertThat(amount(listed, "$.totals[0].billed"))
                    .as(periods.get(index))
                    .isEqualByComparingTo(amount(overview, "$.totals.billed.current"))
                    .isEqualByComparingTo(billed.get(index));
        }
        String owedNow = body(send(get("/api/v1/report/overview").param("currency", "USD"), "")
                .andExpect(jsonPath("$.owedNow.total").value(2550.0))
                .andExpect(jsonPath("$.owedNow.overdue").value(790.0)));
        String outstanding = body(list("outstanding", "true", "currency", "USD"));
        assertThat(amount(outstanding, "$.totals[0].balanceDue"))
                .isEqualByComparingTo(amount(owedNow, "$.owedNow.total"))
                .isEqualByComparingTo(sum(outstanding, "$.myDocuments[*].balanceDue"));
        assertThat(amount(outstanding, "$.totals[0].overdue")).isEqualByComparingTo(amount(owedNow, "$.owedNow.overdue"));
        assertThat((Integer) JsonPath.read(outstanding, "$.totals[0].overdueDocuments"))
                .isEqualTo(JsonPath.read(owedNow, "$.owedNow.overdueInvoices"))
                .isEqualTo(3);
        assertThat((Integer) JsonPath.read(outstanding, "$.totals[0].documents"))
                .isEqualTo(JsonPath.read(owedNow, "$.owedNow.invoices"))
                .isEqualTo(7);
    }

    @Test
    void csvAndIsolation() throws Exception {
        books();
        User other = createUser();
        String theirs = client(other);
        mockMvc.perform(post("/api/v1/document/create")
                        .header(HttpHeaders.AUTHORIZATION, bearer(other))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId": "%s", "documentType": "Invoice", "currency": "USD",
                                 "billingItems": [{"itemName": "Work", "unitPrice": 999, "quantity": 1}]}
                                """.formatted(theirs)))
                .andExpect(status().isOk());

        assertThat(csv("outstanding", "true", "sort", "number", "dir", "asc"))
                .containsExactly("INV-0001", "INV-0002", "INV-0003", "INV-0005");
        assertThat(csv("billed", "true", "type", "Receipt")).containsExactly("REC-0001");
        assertThat(csv("billed", "true", "period", "30d", "currency", "USD", "sort", "number", "dir", "asc"))
                .containsExactly(
                        "CN-0001", "CN-0002", "CN-0003", "INV-0001", "INV-0002", "INV-0003", "INV-0004", "INV-0005",
                        "INV-0006", "REC-0001");
        assertThat(csv("outstanding", "true", "due", "this-week")).containsExactly("INV-0003");
        list("outstanding", "true").andExpect(jsonPath("$.totalDocuments").value(4));
        list("billed", "true", "period", "12m", "currency", "USD").andExpect(jsonPath("$.totalDocuments").value(11));
        owner = other;
        list("outstanding", "true")
                .andExpect(jsonPath("$.totalDocuments").value(1))
                .andExpect(jsonPath("$.myDocuments[0].total").value(999.0))
                .andExpect(jsonPath("$.totals.length()").value(1))
                .andExpect(jsonPath("$.totals[0].documents").value(1))
                .andExpect(jsonPath("$.totals[0].balanceDue").value(999.0));
        list("billed", "true", "period", "30d", "currency", "USD")
                .andExpect(jsonPath("$.totalDocuments").value(1))
                .andExpect(jsonPath("$.totals[0].billed").value(999.0));
    }

    @Test
    void badInput() throws Exception {
        list("outstanding", "maybe")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value for outstanding"));
        list("billed", "sometimes")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value for billed"));
        list("period", "30d", "from", today.minusDays(5).toString())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Choose a period or dates, not both"));
        list("period", "12m", "to", today.toString())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Choose a period or dates, not both"));
        for (String period : List.of("7d", "ytd", "3m", "2025")) {
            list("period", period)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Choose 30 days, 90 days or 12 months"));
        }
        list("due", "yesterday")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unknown due date filter"));
        mockMvc.perform(get("/api/v1/document/csv")
                        .param("period", "ytd")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Choose 30 days, 90 days or 12 months"));
    }
}
