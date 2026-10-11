package com.uth.news.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uth.news.ImageFixtures;
import com.uth.news.config.*;
import com.uth.news.constant.AppConstants;
import com.uth.news.controller.ImageController;
import com.uth.news.entity.User;
import com.uth.news.exception.GlobalExceptionHandler;
import com.uth.news.repository.UserRepository;
import com.uth.news.service.ImageService;
import java.nio.file.*;
import java.util.Optional;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.LinkedMultiValueMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = ImageIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.jwt.secret=test-only-secret-0123456789-abcdef", "app.jwt.expiration-ms=3600000"})
@AutoConfigureMockMvc
class ImageIntegrationTest {
    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class, JpaRepositoriesAutoConfiguration.class})
    @Import({SecurityConfig.class, OpenApiConfig.class, ImageController.class, ImageService.class,
            JwtService.class, CustomUserDetailsService.class, SecurityErrorHandler.class, GlobalExceptionHandler.class})
    static class TestApplication { }

    @TempDir static Path directory;
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.upload-dir", () -> directory.toString());
        registry.add("app.base-url", () -> "http://localhost:8080");
    }

    @Autowired MockMvc mvc;
    @Autowired TestRestTemplate http;
    @Autowired ObjectMapper mapper;
    @Autowired JwtService jwt;
    @MockitoBean UserRepository users;
    private String token;
    private static final String UPLOAD = "/api/v1/news/upload-image";

    @BeforeEach
    void setup() {
        when(users.findByUsername("writer")).thenReturn(Optional.of(new User("writer", "hidden-hash")));
        token = "Bearer " + jwt.generateToken("writer");
    }

    @AfterEach
    void clearStoredTestImages() throws Exception {
        try (var files = Files.list(directory)) {
            for (Path path : files.toList()) { Files.delete(path); }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"jpeg", "png", "webp"})
    void uploadsReturnUrlAndImagesCanBeReadPublicly(String format) throws Exception {
        byte[] bytes = ImageFixtures.image(format);
        String body = mvc.perform(multipart(UPLOAD).file(new MockMultipartFile("file", "../../file.exe", "image/" + format, bytes))
                        .header("Authorization", token))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.imageUrl").isString()).andReturn().getResponse().getContentAsString();
        String url = mapper.readTree(body).path("data").path("imageUrl").asText();
        String publicPath = java.net.URI.create(url).getPath();
        mvc.perform(get(publicPath)).andExpect(status().isOk()).andExpect(content().bytes(bytes))
                .andExpect(content().contentType("image/" + format))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void missingAndInvalidJwtCannotUpload() throws Exception {
        var file = new MockMultipartFile("file", "file.png", "image/png", ImageFixtures.image("png"));
        mvc.perform(multipart(UPLOAD).file(file)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
        mvc.perform(multipart(UPLOAD).file(file).header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
        try (var files = Files.list(directory)) { assertThat(files).isEmpty(); }
    }

    @Test
    void badUploadsReturnConsistentErrors() throws Exception {
        mvc.perform(multipart(UPLOAD).header("Authorization", token)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
        mvc.perform(multipart(UPLOAD).file(new MockMultipartFile("file", new byte[0])).header("Authorization", token))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false));
        mvc.perform(multipart(UPLOAD).file(new MockMultipartFile("file", "fake.jpg", "image/jpeg", "not an image".getBytes()))
                        .header("Authorization", token)).andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.success").value(false));
        mvc.perform(multipart(UPLOAD).file(new MockMultipartFile("file", "spoof.jpg", "image/jpeg", ImageFixtures.image("png")))
                        .header("Authorization", token)).andExpect(status().isUnsupportedMediaType());
        mvc.perform(multipart(UPLOAD).file(new MockMultipartFile("file", "large.png", "image/png",
                        new byte[(int) AppConstants.MAX_IMAGE_SIZE_BYTES + 1])).header("Authorization", token))
                .andExpect(status().isPayloadTooLarge()).andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void realServletMultipartSizeLimitsReturn413WithEnvelope() throws Exception {
        for (int length : new int[]{(int) AppConstants.MAX_IMAGE_SIZE_BYTES + 1, 7 * 1024 * 1024}) {
            var file = new ByteArrayResource(new byte[length]) {
                @Override public String getFilename() { return "large.png"; }
            };
            var parts = new LinkedMultiValueMap<String, Object>();
            HttpHeaders fileHeaders = new HttpHeaders();
            fileHeaders.setContentType(MediaType.IMAGE_PNG);
            parts.add("file", new HttpEntity<>(file, fileHeaders));
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.set("Authorization", token);
            var response = http.postForEntity(UPLOAD, new HttpEntity<>(parts, headers), String.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
            assertThat(mapper.readTree(response.getBody()).path("success").asBoolean()).isFalse();
            assertThat(mapper.readTree(response.getBody()).path("message").asText()).isEqualTo("Payload Too Large");
        }
    }

    @Test
    void unknownAndNonImagePathsAreNotExposed() throws Exception {
        Files.writeString(directory.resolve("secret.txt"), "hidden");
        mvc.perform(get("/uploads/secret.txt")).andExpect(status().isNotFound()).andExpect(jsonPath("$.success").value(false));
        mvc.perform(get("/uploads/00000000-0000-0000-0000-000000000000.png"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.success").value(false));
        mvc.perform(get("/uploads/%2e%2e%2fsecret.txt")).andExpect(status().isBadRequest());
    }

    @Test
    void swaggerDescribesMultipartUploadWithJwtAndPublicRead() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/news/upload-image'].post.requestBody.content['multipart/form-data']").exists())
                .andExpect(jsonPath("$.security[0].bearerAuth").isArray())
                .andExpect(jsonPath("$.paths['/uploads/{filename}'].get.security").isEmpty());
    }
}
