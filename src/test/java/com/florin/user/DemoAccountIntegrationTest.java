package com.florin.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.florin.IntegrationTest;
import com.florin.upload.TestImages;
import com.jayway.jsonpath.JsonPath;
import jakarta.mail.internet.MimeMessage;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

class DemoAccountIntegrationTest extends IntegrationTest {

    @Test
    void profileFlag() throws Exception {
        mockMvc.perform(get("/api/v1/user/profile").header(HttpHeaders.AUTHORIZATION, bearer(demoUser())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userProfile.demo").value(true));
        mockMvc.perform(get("/api/v1/user/profile").header(HttpHeaders.AUTHORIZATION, bearer(createUser())))
                .andExpect(jsonPath("$.userProfile.demo").value(false));
    }

    @Test
    void profileStays() throws Exception {
        User demo = demoUser();
        String name = demo.getFirstName();

        mockMvc.perform(patch("/api/v1/user/profile")
                        .header(HttpHeaders.AUTHORIZATION, bearer(demo))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\": \"Renamed\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message")
                        .value("This is a shared demo account, so changing its profile is turned off"));
        assertThat(userRepository.findByExternalId(demo.getId()).orElseThrow().getFirstName()).isEqualTo(name);
        mockMvc.perform(patch("/api/v1/user/profile")
                        .header(HttpHeaders.AUTHORIZATION, bearer(createUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\": \"Renamed\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void businessStays() throws Exception {
        String demo = bearer(demoUser());
        String name = businessOf(demoUser()).getName();

        mockMvc.perform(patch("/api/v1/business")
                        .header(HttpHeaders.AUTHORIZATION, demo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Renamed\", \"logo\": \"/uploads/elsewhere.png\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message")
                        .value("This is a shared demo account, so changing the business's details is turned off"));
        mockMvc.perform(put("/api/v1/settings/payment-details")
                        .header(HttpHeaders.AUTHORIZATION, demo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"upiId\": \"someone@upi\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message")
                        .value("This is a shared demo account, so changing the payment details is turned off"));
        mockMvc.perform(multipart(HttpMethod.PATCH, "/api/v1/upload")
                        .file(new MockMultipartFile("logo", "logo.png", "image/png", TestImages.image("png", 1, 1)))
                        .header(HttpHeaders.AUTHORIZATION, demo))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message")
                        .value("This is a shared demo account, so uploading a logo is turned off"));
        assertThat(businessOf(demoUser()).getName()).isEqualTo(name);

        mockMvc.perform(post("/api/v1/customer/create")
                        .header(HttpHeaders.AUTHORIZATION, demo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Visitor Co\", \"email\": \"%s@clients.test\", \"phoneNumber\": \"+1\"}"
                                .formatted(UUID.randomUUID())))
                .andExpect(status().isOk());
    }

    @Test
    void cannotDeleteItself() throws Exception {
        User demo = demoUser();

        mockMvc.perform(delete("/api/v1/user/profile").header(HttpHeaders.AUTHORIZATION, bearer(demo)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("This is a shared demo account, so deleting it is turned off"));
        assertThat(userRepository.findByExternalId(demo.getId())).isPresent();
    }

    @Test
    void cannotEmail() throws Exception {
        User demo = demoUser();
        String customer = mockMvc.perform(post("/api/v1/customer/create")
                        .header(HttpHeaders.AUTHORIZATION, bearer(demo))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Anyone\", \"email\": \"anyone-%s@clients.test\", \"phoneNumber\": \"+1 555 0100\"}"
                                .formatted(UUID.randomUUID())))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String document = mockMvc.perform(post("/api/v1/document/create")
                        .header(HttpHeaders.AUTHORIZATION, bearer(demo))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\": \"%s\", \"billingItems\": [{\"itemName\": \"Work\", \"unitPrice\": 1, \"quantity\": 1}]}"
                                .formatted((String) JsonPath.read(customer, "$.createdCustomer._id"))))
                .andReturn()
                .getResponse()
                .getContentAsString();

        mockMvc.perform(post("/api/v1/document/{id}/email", (String) JsonPath.read(document, "$.newDocument._id"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(demo)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("This is a shared demo account, so emailing is turned off"));
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void adminCannotRemoveIt() throws Exception {
        User demo = demoUser();
        String admin = bearer(createAdmin());

        mockMvc.perform(delete("/api/v1/user/{id}", demo.getId()).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("The shared demo account can't be deleted"));
        mockMvc.perform(patch("/api/v1/user/{id}/deactivate", demo.getId()).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("The shared demo account can't be deactivated"));
        assertThat(userRepository.findByExternalId(demo.getId()).orElseThrow().isActive()).isTrue();
    }
}
