package com.florin.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.florin.IntegrationTest;
import com.florin.business.Business;
import com.florin.business.BusinessRepository;
import com.florin.business.BusinessRole;
import com.florin.customer.CustomerRepository;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

class AccountDeletionIntegrationTest extends IntegrationTest {

    private static final String SHARED = "You own Test User, which others work in. Hand it over or remove them first";

    @Autowired
    private BusinessRepository businessRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Test
    void sharedBusinessOwnerKeepsAccount() throws Exception {
        User owner = withAccountPassword(createUser());
        Business business = businessOf(owner);
        User member = createMember(business, BusinessRole.MEMBER);
        String session = bearer(owner, business);

        mockMvc.perform(get("/api/v1/user/profile").header(HttpHeaders.AUTHORIZATION, session))
                .andExpect(jsonPath("$.userProfile.sharedBusinesses[0]").value("Test User"));
        mockMvc.perform(delete("/api/v1/user/profile")
                        .header(HttpHeaders.AUTHORIZATION, session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CONFIRMATION))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(SHARED));

        mockMvc.perform(delete("/api/v1/business/members/" + member.getId()).header(HttpHeaders.AUTHORIZATION, session))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/user/profile")
                        .header(HttpHeaders.AUTHORIZATION, session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CONFIRMATION))
                .andExpect(status().isOk());
        assertThat(businessRepository.findById(business.getPkid())).isEmpty();
    }

    @Test
    void adminsCannotRemoveSharedOwner() throws Exception {
        User owner = createUser();
        createMember(businessOf(owner), BusinessRole.VIEWER);
        String admin = bearer(createAdmin());
        String refusal = "This account owns Test User, which others work in. "
                + "They must hand it over or remove the others first";

        mockMvc.perform(delete("/api/v1/user/" + owner.getId()).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(refusal));
        mockMvc.perform(patch("/api/v1/user/" + owner.getId() + "/deactivate").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(refusal));
    }

    @Test
    void memberLeavesRecordsBehind() throws Exception {
        User owner = createUser();
        Business business = businessOf(owner);
        User member = withAccountPassword(createMember(business, BusinessRole.MEMBER));
        String memberSession = bearer(member, business);
        String body = mockMvc.perform(post("/api/v1/customer/create")
                        .header(HttpHeaders.AUTHORIZATION, memberSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Acme", "email": "acme-%s@clients.test", "phoneNumber": "555 0100"}
                                """.formatted(UUID.randomUUID())))
                .andReturn().getResponse().getContentAsString();
        UUID client = UUID.fromString(JsonPath.read(body, "$.createdCustomer._id"));

        mockMvc.perform(delete("/api/v1/user/profile")
                        .header(HttpHeaders.AUTHORIZATION, memberSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CONFIRMATION))
                .andExpect(status().isOk());

        assertThat(businessRepository.findById(business.getPkid())).isPresent();
        assertThat(customerRepository.findByExternalId(client)).get()
                .satisfies(customer -> assertThat(customer.getCreatedBy()).isNull());
        mockMvc.perform(get("/api/v1/business/members").header(HttpHeaders.AUTHORIZATION, bearer(owner, business)))
                .andExpect(jsonPath("$.members.length()").value(1));
    }

    @Test
    void asksForThePassword() throws Exception {
        User user = withAccountPassword(createUser());
        String session = bearer(user);

        for (String json : new String[] {"", "{}", "{\"password\": \"Wrong!pass1\"}"}) {
            mockMvc.perform(delete("/api/v1/user/profile")
                            .header(HttpHeaders.AUTHORIZATION, session)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message")
                            .value(json.contains("Wrong") ? "Your password is wrong" : "Enter your password to confirm"));
        }
        assertThat(userRepository.findById(user.getPkid())).isPresent();

        User withoutPassword = createUser();
        mockMvc.perform(delete("/api/v1/user/profile")
                        .header(HttpHeaders.AUTHORIZATION, bearer(withoutPassword))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CONFIRMATION))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Set a password first, with Forgot password on the sign-in page"));
        assertThat(userRepository.findById(withoutPassword.getPkid())).isPresent();
    }
}
