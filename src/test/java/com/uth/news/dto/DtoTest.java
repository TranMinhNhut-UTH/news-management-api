package com.uth.news.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uth.news.dto.request.*;
import com.uth.news.dto.response.*;
import com.uth.news.entity.*;
import jakarta.validation.*;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.*;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import static org.assertj.core.api.Assertions.assertThat;

class DtoTest {
    private static ValidatorFactory factory;
    private static Validator validator;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @BeforeAll
    static void setup() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void close() { factory.close(); }

    private Set<String> invalidFields(Object request) {
        return validator.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(java.util.stream.Collectors.toSet());
    }

    @Test
    void validRequestsPassIncludingOptionalImage() {
        assertThat(invalidFields(new RegisterRequest("writer", "password123"))).isEmpty();
        assertThat(invalidFields(new LoginRequest("writer", "password123"))).isEmpty();
        assertThat(invalidFields(new NewsRequest("Title", "Content", "Tech", null))).isEmpty();
    }

    @Test
    void blankRequiredFieldsFail() {
        assertThat(invalidFields(new RegisterRequest(" ", " "))).contains("username", "password");
        assertThat(invalidFields(new LoginRequest(null, ""))).contains("username", "password");
        assertThat(invalidFields(new NewsRequest("", null, " ", null)))
                .containsExactlyInAnyOrder("title", "content", "category");
    }

    @Test
    void registerRejectsShortPasswordAndOverlongUsername() {
        assertThat(invalidFields(new RegisterRequest("u".repeat(51), "short")))
                .containsExactlyInAnyOrder("username", "password");
        assertThat(invalidFields(new RegisterRequest("writer", "p".repeat(73)))).contains("password");
    }

    @Test
    void newsLengthsMatchDatabaseLimits() {
        assertThat(invalidFields(new NewsRequest("t".repeat(255), "c", "c".repeat(100), "i".repeat(500))))
                .isEmpty();
        assertThat(invalidFields(new NewsRequest("t".repeat(256), "c", "c".repeat(101), "i".repeat(501))))
                .containsExactlyInAnyOrder("title", "category", "imageUrl");
    }

    @Test
    void responseSerializationContainsAuthorAndNoPassword() throws Exception {
        User user = new User("writer", "secret-hash");
        News news = new News("Title", "Content", "Tech", null, user);
        String json = mapper.writeValueAsString(ApiResponse.success("OK", NewsResponse.from(news)));
        JsonNode tree = mapper.readTree(json);
        assertThat(tree.get("success").asBoolean()).isTrue();
        assertThat(tree.get("data").get("author").get("username").asText()).isEqualTo("writer");
        assertThat(json).doesNotContain("password", "secret-hash");
        assertThat(mapper.writeValueAsString(user)).doesNotContain("password", "secret-hash");
    }

    @Test
    void requestPasswordIsWriteOnlyAndRedactedInToString() throws Exception {
        RegisterRequest request = mapper.readValue(
                "{\"username\":\"writer\",\"password\":\"secret123\"}", RegisterRequest.class);
        assertThat(request.password()).isEqualTo("secret123");
        assertThat(mapper.writeValueAsString(request)).doesNotContain("password", "secret123");
        assertThat(request.toString()).doesNotContain("secret123");
        LoginRequest login = new LoginRequest("writer", "secret123");
        assertThat(mapper.writeValueAsString(login)).doesNotContain("password", "secret123");
        assertThat(login.toString()).doesNotContain("secret123");
    }

    @Test
    void pageResponsePreservesPaginationMetadata() {
        PageResponse<String> response = PageResponse.from(
                new PageImpl<>(List.of("a", "b"), PageRequest.of(1, 2), 5));
        assertThat(response.items()).containsExactly("a", "b");
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(2);
        assertThat(response.totalElements()).isEqualTo(5);
        assertThat(response.totalPages()).isEqualTo(3);
    }

    @Test
    void failureResponseKeepsThreeStandardFields() throws Exception {
        JsonNode tree = mapper.valueToTree(ApiResponse.failure("Not found", null));
        assertThat(tree.size()).isEqualTo(3);
        assertThat(tree.get("success").asBoolean()).isFalse();
        assertThat(tree.get("message").asText()).isEqualTo("Not found");
        assertThat(tree.get("data").isNull()).isTrue();
    }
}
