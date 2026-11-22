package com.florin.upload;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.florin.IntegrationTest;
import com.florin.config.AppProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;

class UploadServingIntegrationTest extends IntegrationTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13};

    @Autowired
    private AppProperties properties;

    private Path image;
    private Path page;

    @BeforeEach
    void setUp() throws Exception {
        Path dir = Path.of(properties.upload().dir());
        Files.createDirectories(dir);
        image = Files.write(dir.resolve(UUID.randomUUID() + ".png"), PNG);
        page = Files.writeString(dir.resolve(UUID.randomUUID() + ".html"), "<p>Not an image</p>");
    }

    @AfterEach
    void tearDown() throws Exception {
        Files.deleteIfExists(image);
        Files.deleteIfExists(page);
    }

    @Test
    void servesImagesOnly() throws Exception {
        mockMvc.perform(get("/uploads/{name}", image.getFileName().toString()))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(header().string("Content-Security-Policy", "sandbox"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        mockMvc.perform(get("/uploads/{name}", page.getFileName().toString())).andExpect(status().isNotFound());
    }

    @Test
    void imagesAreKept() throws Exception {
        mockMvc.perform(get("/uploads/{name}", image.getFileName().toString()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "max-age=31536000, public, immutable"))
                .andExpect(header().doesNotExist(HttpHeaders.PRAGMA))
                .andExpect(header().doesNotExist(HttpHeaders.EXPIRES));
        mockMvc.perform(get("/uploads/{name}", UUID.randomUUID() + ".png"))
                .andExpect(status().isNotFound())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")));
    }

    @Test
    void refusesPagesSentAsImages() throws Exception {
        MockMultipartFile page = new MockMultipartFile(
                "logo", "page.html", "image/png", "<p>Not an image</p>".getBytes());
        mockMvc.perform(multipart(HttpMethod.PATCH, "/api/v1/upload")
                        .file(page)
                        .header(HttpHeaders.AUTHORIZATION, bearer(createUser())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Logos can be PNG, JPEG or GIF images"));
    }
}
