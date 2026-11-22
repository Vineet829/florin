package com.florin.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.florin.IntegrationTest;
import com.florin.Uncommitted;
import com.jayway.jsonpath.JsonPath;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class CustomerRaceIntegrationTest extends IntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbc;

    private String session;
    private String client;
    private long clientPkid;

    private int send(MockHttpServletRequestBuilder request, String json) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andReturn().getResponse().getStatus();
    }

    @BeforeEach
    void setUp() throws Exception {
        session = bearer(createUser());
        client = JsonPath.read(
                mockMvc.perform(post("/api/v1/customer/create")
                                .header(HttpHeaders.AUTHORIZATION, session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"name": "Beta LLC", "email": "%s@clients.test", "phoneNumber": "+1 555 0100"}
                                        """.formatted(UUID.randomUUID())))
                        .andReturn().getResponse().getContentAsString(),
                "$.createdCustomer._id");
        clientPkid = jdbc.queryForObject(
                "select pkid from customers where id = ?", Long.class, UUID.fromString(client));
    }

    @Test
    void editedMeanwhile() throws Exception {
        int status = Uncommitted.change(
                dataSource, "update customers set phone_number = '+91 98765 43210' where pkid = ?", clientPkid,
                () -> send(patch("/api/v1/customer/{id}", client), "{\"city\": \"Pune\"}"));

        assertThat(status).isEqualTo(200);
        Map<String, Object> row =
                jdbc.queryForMap("select phone_number, city from customers where pkid = ?", clientPkid);
        assertThat(row).containsEntry("phone_number", "+91 98765 43210").containsEntry("city", "Pune");
    }

    @Test
    void editDeletedMeanwhile() throws Exception {
        int status = Uncommitted.change(
                dataSource, "delete from customers where pkid = ?", clientPkid,
                () -> send(patch("/api/v1/customer/{id}", client), "{\"city\": \"Pune\"}"));

        assertThat(status).isEqualTo(404);
    }

    @Test
    void deleteDeletedMeanwhile() throws Exception {
        int status = Uncommitted.change(
                dataSource, "delete from customers where pkid = ?", clientPkid,
                () -> send(delete("/api/v1/customer/{id}", client), ""));

        assertThat(status).isEqualTo(404);
    }
}
