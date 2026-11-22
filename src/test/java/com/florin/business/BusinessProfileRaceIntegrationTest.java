package com.florin.business;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

import com.florin.IntegrationTest;
import com.florin.Uncommitted;
import com.florin.user.User;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

class BusinessProfileRaceIntegrationTest extends IntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void savedMeanwhile() throws Exception {
        User owner = createUser();
        Business business = businessOf(owner);
        String session = bearer(owner);

        int status = Uncommitted.change(
                dataSource, "update businesses set phone_number = '+91 20 5555 0199' where pkid = ?",
                business.getPkid(),
                () -> mockMvc.perform(patch("/api/v1/business")
                                .header(HttpHeaders.AUTHORIZATION, session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"city\": \"Pune\"}"))
                        .andReturn().getResponse().getStatus());

        assertThat(status).isEqualTo(200);
        Map<String, Object> row =
                jdbc.queryForMap("select phone_number, city from businesses where pkid = ?", business.getPkid());
        assertThat(row).containsEntry("phone_number", "+91 20 5555 0199").containsEntry("city", "Pune");
    }
}
