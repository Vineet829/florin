package com.florin.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.florin.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class SellerSnapshotIntegrationTest extends IntegrationTest {

    private String session;
    private String client;

    private ResultActions send(MockHttpServletRequestBuilder request, String json) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private static String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }

    private void taxCalled(String label) throws Exception {
        send(put("/api/v1/settings"), """
                {"defaultCurrency": "USD", "paymentTermsDays": 7, "defaultTaxRate": 18,
                 "taxLabel": "%s", "timeZone": "UTC"}
                """.formatted(label)).andExpect(status().isOk());
    }

    @BeforeEach
    void setUp() throws Exception {
        session = bearer(createUser());
        send(patch("/api/v1/business"), """
                {"name": "Apte Analytics", "legalName": "Apte Analytics Private Limited",
                 "address": "12 MG Road", "city": "Pune", "taxId": "27AAPFU0939F1ZV"}
                """).andExpect(status().isOk());
        taxCalled("GST");
        client = JsonPath.read(
                body(send(post("/api/v1/customer/create"), """
                        {"name": "Beta LLC", "email": "%s@clients.test", "phoneNumber": "+1"}
                        """.formatted(UUID.randomUUID()))),
                "$.createdCustomer._id");
    }

    private String created() throws Exception {
        return created(false);
    }

    private String created(boolean draft) throws Exception {
        return JsonPath.read(
                body(send(post("/api/v1/document/create"), """
                        {"customerId": "%s", "documentType": "Invoice", "currency": "USD", "rates": "18",
                         "draft": %s, "billingItems": [{"itemName": "Work", "unitPrice": 100, "quantity": 1}]}
                        """.formatted(client, draft)).andExpect(status().isOk())),
                "$.newDocument._id");
    }

    private void businessChanges() throws Exception {
        send(patch("/api/v1/business"), """
                {"name": "Borealis Studio", "legalName": "", "address": "1 Hill Road", "city": "Mumbai",
                 "taxId": ""}
                """).andExpect(status().isOk());
        taxCalled("VAT");
    }

    private String pdf(String url, Object... variables) throws Exception {
        return PdfText.of(mockMvc.perform(get(url, variables).header(HttpHeaders.AUTHORIZATION, session))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray());
    }

    @Test
    void issuedKeepsSellerSnapshot() throws Exception {
        String id = created();
        send(post("/api/v1/document/{id}/payment", id), "{\"amountPaid\": 50}").andExpect(status().isCreated());
        businessChanges();

        send(get("/api/v1/document/{id}", id), "")
                .andExpect(jsonPath("$.document.seller.name").value("Apte Analytics"))
                .andExpect(jsonPath("$.document.seller.legalName").value("Apte Analytics Private Limited"))
                .andExpect(jsonPath("$.document.seller.city").value("Pune"))
                .andExpect(jsonPath("$.document.seller.taxId").value("27AAPFU0939F1ZV"))
                .andExpect(jsonPath("$.document.taxLabel").value("GST"));
        assertThat(pdf("/api/v1/document/{id}/pdf", id))
                .contains("Apte Analytics Private Limited", "12 MG Road, Pune", "Tax ID 27AAPFU0939F1ZV", "GST (18%)")
                .doesNotContain("Borealis", "Hill Road", "VAT");
        String payment = JsonPath.<List<String>>read(
                        body(send(get("/api/v1/document/{id}", id), "")), "$.document.paymentRecords[*]._id")
                .get(0);
        assertThat(pdf("/api/v1/document/{id}/payment/{payment}/receipt", id, payment))
                .contains("Apte Analytics", "12 MG Road, Pune")
                .doesNotContain("Borealis");
        String url = JsonPath.read(body(send(post("/api/v1/document/{id}/share", id), "")), "$.url");
        mockMvc.perform(get("/api/v1/public/share/{token}", url.substring(url.lastIndexOf('/') + 1)))
                .andExpect(jsonPath("$.business.name").value("Apte Analytics"))
                .andExpect(jsonPath("$.business.lines", hasItem("Tax ID 27AAPFU0939F1ZV")))
                .andExpect(jsonPath("$.taxLabel").value("GST"));
    }

    @Test
    void laterDocumentsShowCurrentSeller() throws Exception {
        created();
        businessChanges();

        String newer = created();
        send(get("/api/v1/document/{id}", newer), "")
                .andExpect(jsonPath("$.document.seller.name").value("Borealis Studio"))
                .andExpect(jsonPath("$.document.taxLabel").value("VAT"));
        assertThat(pdf("/api/v1/document/{id}/pdf", newer))
                .contains("Borealis Studio", "1 Hill Road, Mumbai", "VAT (18%)")
                .doesNotContain("Apte Analytics", "GST");
        assertThat(pdf("/api/v1/customer/{id}/statement", client))
                .contains("Borealis Studio")
                .doesNotContain("Apte Analytics");
    }

    @Test
    void draftSnapshotsOnIssue() throws Exception {
        String draft = created(true);
        send(get("/api/v1/document/{id}", draft), "")
                .andExpect(jsonPath("$.document.seller").doesNotExist())
                .andExpect(jsonPath("$.document.taxLabel").value(nullValue()));
        businessChanges();

        assertThat(pdf("/api/v1/document/{id}/pdf", draft))
                .contains("Borealis Studio", "VAT (18%)")
                .doesNotContain("Apte Analytics", "GST");
        send(post("/api/v1/document/{id}/issue", draft), "{}")
                .andExpect(jsonPath("$.updatedDocument.seller.name").value("Borealis Studio"))
                .andExpect(jsonPath("$.updatedDocument.taxLabel").value("VAT"));
        send(patch("/api/v1/business"), "{\"name\": \"Cygnus Labs\"}").andExpect(status().isOk());
        assertThat(pdf("/api/v1/document/{id}/pdf", draft)).contains("Borealis Studio").doesNotContain("Cygnus");
    }
}
