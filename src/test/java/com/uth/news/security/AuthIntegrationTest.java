package com.uth.news.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uth.news.config.*;
import com.uth.news.controller.AuthController;
import com.uth.news.entity.User;
import com.uth.news.exception.GlobalExceptionHandler;
import com.uth.news.repository.UserRepository;
import com.uth.news.service.AuthService;
import io.jsonwebtoken.Jwts;
import java.util.*;
import java.nio.charset.StandardCharsets;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.*;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = AuthIntegrationTest.TestApplication.class, properties = {
        "app.jwt.secret=test-only-secret-0123456789-abcdef",
        "app.jwt.expiration-ms=3600000"
})
@AutoConfigureMockMvc
class AuthIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtService jwtService;
    @MockitoBean UserRepository repository;

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class, JpaRepositoriesAutoConfiguration.class})
    @Import({SecurityConfig.class, OpenApiConfig.class, AuthController.class, AuthService.class,
            JwtService.class, CustomUserDetailsService.class, SecurityErrorHandler.class,
            GlobalExceptionHandler.class, PrivateFixture.class})
    static class TestApplication {
    }

    @RestController
    static class PrivateFixture {
        @GetMapping("/test/private")
        Map<String, String> privateEndpoint(Authentication authentication) {
            return Map.of("username", authentication.getName());
        }
    }

    @BeforeEach
    void setup() {
        when(repository.findByUsername("writer")).thenReturn(Optional.of(
                new User("writer", encoder.encode("password123"))));
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder jsonPost(String path, String body) {
        return post(path).contentType("application/json").content(body);
    }

    @Test
    void publicRegisterReturns201WithoutPasswordOrSession() throws Exception {
        mvc.perform(jsonPost("/api/v1/auth/register", "{\"username\":\"writer\",\"password\":\"password123\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.username").value("writer"))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                .andExpect(content().string(not(containsString("password123"))))
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    @Test
    void duplicateRegisterReturns409() throws Exception {
        when(repository.existsByUsername("writer")).thenReturn(true);
        mvc.perform(jsonPost("/api/v1/auth/register", "{\"username\":\"writer\",\"password\":\"password123\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void loginTokenAuthenticatesPrivateRequestAndDoesNotCreateSession() throws Exception {
        String body = mvc.perform(jsonPost("/api/v1/auth/login",
                        "{\"username\":\"writer\",\"password\":\"password123\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresIn").value(3600))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                .andExpect(content().string(not(containsString("password123"))))
                .andReturn().getResponse().getContentAsString();
        String token = mapper.readTree(body).path("data").path("accessToken").asText();
        assertThat(jwtService.extractUsername(token)).isEqualTo("writer");
        var result = mvc.perform(get("/test/private").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.username").value("writer"))
                .andExpect(header().doesNotExist("Set-Cookie")).andReturn();
        assertThat(result.getRequest().getSession(false)).isNull();
        mvc.perform(get("/test/private")).andExpect(status().isUnauthorized());
    }

    @Test
    void invalidCredentialsReturn401() throws Exception {
        for (String username : List.of("writer", "missing")) {
            mvc.perform(jsonPost("/api/v1/auth/login",
                            "{\"username\":\"" + username + "\",\"password\":\"wrong\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("Invalid username or password"));
        }
    }

    @Test
    void invalidRequestsAndMalformedJsonReturn400() throws Exception {
        mvc.perform(jsonPost("/api/v1/auth/register", "{\"username\":\"\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.password").isArray());
        mvc.perform(jsonPost("/api/v1/auth/login", "{\"username\":\"writer\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.password").isArray());
        mvc.perform(jsonPost("/api/v1/auth/login", "{"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void missingMalformedWrongSignatureAndDeletedUserTokensReturn401() throws Exception {
        mvc.perform(get("/test/private")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false)).andExpect(header().string("WWW-Authenticate", "Bearer"));
        String wrongSignature = new JwtService("other-test-secret-0123456789-abcdef", 3600000)
                .generateToken("writer");
        for (String header : List.of("Bearer bad", "Bearer ", "Basic abc",
                "Bearer " + wrongSignature, "Bearer " + jwtService.generateToken("deleted"))) {
            mvc.perform(get("/test/private").header("Authorization", header))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.success").value(false));
        }
    }

    @Test
    void expiredTokenReturns401() throws Exception {
        String expired = Jwts.builder().subject("writer")
                .issuedAt(new Date(System.currentTimeMillis() - 120000))
                .expiration(new Date(System.currentTimeMillis() - 60000))
                .signWith(new SecretKeySpec("test-only-secret-0123456789-abcdef"
                        .getBytes(StandardCharsets.UTF_8), "HmacSHA256"), Jwts.SIG.HS256).compact();
        mvc.perform(get("/test/private").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("Token expired"));
    }

    @Test
    void swaggerAndOpenApiArePublicAndDeclareBearerScheme() throws Exception {
        mvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post.security").isEmpty());
        mvc.perform(get("/v3/api-docs/swagger-config")).andExpect(status().isOk());
    }

    @Test
    void newsAndImagePathsRemainPrivateAndHaveNoProductionImplementation() throws Exception {
        mvc.perform(get("/api/v1/news")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/images")).andExpect(status().isUnauthorized());
    }
}
