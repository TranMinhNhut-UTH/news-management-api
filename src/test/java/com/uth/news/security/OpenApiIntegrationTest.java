package com.uth.news.security;

import com.fasterxml.jackson.databind.*;
import com.uth.news.config.*;
import com.uth.news.controller.*;
import com.uth.news.exception.GlobalExceptionHandler;
import com.uth.news.repository.UserRepository;
import com.uth.news.service.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = OpenApiIntegrationTest.TestApplication.class, properties = {
        "app.jwt.secret=test-only-secret-0123456789-abcdef", "app.jwt.expiration-ms=3600000"})
@AutoConfigureMockMvc
class OpenApiIntegrationTest {
    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class, JpaRepositoriesAutoConfiguration.class})
    @Import({SecurityConfig.class, OpenApiConfig.class, AuthController.class, NewsController.class,
            ImageController.class, ImageDeletionController.class, JwtService.class, CustomUserDetailsService.class,
            SecurityErrorHandler.class, GlobalExceptionHandler.class})
    static class TestApplication { }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockitoBean AuthService auth;
    @MockitoBean NewsService news;
    @MockitoBean ImageService images;
    @MockitoBean ImageDeletionService deletion;
    @MockitoBean UserRepository users;

    private JsonNode document() throws Exception {
        return mapper.readTree(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private JsonNode resolve(JsonNode doc, JsonNode node) {
        return node.has("$ref") ? doc.at(node.path("$ref").asText().substring(1)) : node;
    }

    @Test
    void allOperationsDeclareRealSuccessAndErrorCodesWithResolvableSchemas() throws Exception {
        JsonNode doc = document();
        String[][] operations = {
                {"/api/v1/auth/register", "post", "201", "400", "401", "409", "415", "500"},
                {"/api/v1/auth/login", "post", "200", "400", "401", "415", "500"},
                {"/api/v1/news", "get", "200", "400", "401", "500"},
                {"/api/v1/news/{id}", "get", "200", "400", "401", "404", "500"},
                {"/api/v1/news/search", "get", "200", "400", "401", "500"},
                {"/api/v1/news", "post", "201", "400", "401", "409", "415", "500"},
                {"/api/v1/news/{id}", "put", "200", "400", "401", "403", "404", "409", "415", "500"},
                {"/api/v1/news/{id}", "delete", "200", "400", "401", "403", "404", "409", "500"},
                {"/api/v1/news/upload-image", "post", "201", "400", "401", "413", "415", "500"},
                {"/api/v1/news/images/{filename}", "delete", "200", "400", "401", "404", "409", "500"},
                {"/uploads/{filename}", "get", "200", "400", "401", "404", "500"}
        };
        for (String[] operation : operations) {
            JsonNode responses = doc.path("paths").path(operation[0]).path(operation[1]).path("responses");
            for (String code : Arrays.copyOfRange(operation, 2, operation.length)) {
                assertThat(responses.has(code)).as("%s %s %s", operation[1], operation[0], code).isTrue();
                JsonNode response = resolve(doc, responses.path(code));
                assertThat(response.path("description").asText()).isNotBlank();
                for (JsonNode content : response.path("content")) {
                    assertThat(resolve(doc, content.path("schema")).isMissingNode()).isFalse();
                }
            }
        }
    }

    @Test
    void successSchemasPreserveTypedDataAndExamplesAreEnvelopes() throws Exception {
        JsonNode doc = document();
        for (String[] operation : new String[][]{{"/api/v1/auth/register", "post", "201", "username"},
                {"/api/v1/auth/login", "post", "200", "accessToken"},
                {"/api/v1/news", "post", "201", "author"},
                {"/api/v1/news", "get", "200", "items"},
                {"/api/v1/news/upload-image", "post", "201", "imageUrl"}}) {
            JsonNode media = doc.path("paths").path(operation[0]).path(operation[1]).path("responses")
                    .path(operation[2]).path("content").path("application/json");
            JsonNode schema = resolve(doc, media.path("schema"));
            JsonNode data = resolve(doc, schema.path("properties").path("data"));
            assertThat(data.path("properties").has(operation[3])).as(operation[0]).isTrue();
            JsonNode example = media.has("example") ? media.path("example")
                    : media.path("examples").elements().next().path("value");
            assertThat(example.isObject()).isTrue();
            assertThat(example.path("success").asBoolean()).isTrue();
            assertThat(example.has("message") && example.has("data")).isTrue();
        }
    }

    @Test
    void errorsDocumentValidationAndNullDataIncludingSecurityHeader() throws Exception {
        JsonNode doc = document();
        JsonNode responses = doc.path("components").path("responses");
        JsonNode bad = responses.path("BadRequest").path("content").path("application/json");
        assertThat(bad.path("schema").path("oneOf").size()).isEqualTo(2);
        assertThat(bad.path("examples").path("validation").path("value").path("errors").isObject()).isTrue();
        for (JsonNode response : responses) {
            JsonNode error = response.path("content").path("application/json").path("examples").path("error").path("value");
            assertThat(error.path("success").asBoolean()).isFalse();
            assertThat(error.has("data") && error.path("data").isNull()).isTrue();
        }
        assertThat(responses.path("Unauthorized").path("headers").has("WWW-Authenticate")).isTrue();
    }

    @Test
    void securityDistinguishesPublicAndPrivateAndAuthenticationIsNotARequestParameter() throws Exception {
        JsonNode doc = document();
        assertThat(doc.at("/components/securitySchemes/bearerAuth/scheme").asText()).isEqualTo("bearer");
        for (String[] operation : new String[][]{{"/api/v1/auth/register", "post"}, {"/api/v1/auth/login", "post"},
                {"/api/v1/news", "get"}, {"/api/v1/news/{id}", "get"}, {"/uploads/{filename}", "get"}}) {
            assertThat(doc.path("paths").path(operation[0]).path(operation[1]).path("security").size()).isZero();
        }
        for (String[] operation : new String[][]{{"/api/v1/news/search", "get"}, {"/api/v1/news", "post"},
                {"/api/v1/news/{id}", "put"}, {"/api/v1/news/{id}", "delete"},
                {"/api/v1/news/upload-image", "post"}, {"/api/v1/news/images/{filename}", "delete"}}) {
            JsonNode node = doc.path("paths").path(operation[0]).path(operation[1]);
            JsonNode security = node.has("security") ? node.path("security") : doc.path("security");
            assertThat(security.path(0).has("bearerAuth")).isTrue();
            for (JsonNode parameter : node.path("parameters")) {
                assertThat(parameter.path("name").asText()).isNotEqualTo("authentication");
            }
        }
    }

    @Test
    void requestSchemasAndSearchLimitsMatchCurrentDtosAndService() throws Exception {
        JsonNode doc = document();
        JsonNode schemas = doc.path("components").path("schemas");
        JsonNode request = schemas.path("NewsRequest").path("properties");
        assertThat(request.has("authorId")).isFalse();
        assertThat(request.path("title").path("maxLength").asInt()).isEqualTo(255);
        assertThat(request.path("category").path("maxLength").asInt()).isEqualTo(100);
        assertThat(request.path("imageUrl").path("maxLength").asInt()).isEqualTo(500);
        assertThat(schemas.path("RegisterRequest").path("properties").path("password").path("writeOnly").asBoolean()).isTrue();
        Map<String, JsonNode> parameters = new HashMap<>();
        for (JsonNode parameter : doc.path("paths").path("/api/v1/news/search").path("get").path("parameters")) {
            parameters.put(parameter.path("name").asText(), parameter.path("schema"));
        }
        assertThat(parameters.get("page").path("minimum").asInt()).isZero();
        assertThat(parameters.get("size").path("maximum").asInt()).isEqualTo(100);
        assertThat(parameters.get("size").path("default").asInt()).isEqualTo(10);
        assertThat(parameters.get("sortBy").path("enum").toString()).isEqualTo("[\"title\",\"createdAt\"]");
        assertThat(parameters.get("direction").path("enum").toString()).isEqualTo("[\"asc\",\"desc\"]");
    }

    @Test
    void multipartAndImageResponsesHaveCorrectBinarySchemas() throws Exception {
        JsonNode doc = document();
        JsonNode upload = doc.path("paths").path("/api/v1/news/upload-image").path("post");
        JsonNode schema = resolve(doc, upload.path("requestBody").path("content").path("multipart/form-data").path("schema"));
        assertThat(schema.path("properties").path("file").path("format").asText()).isEqualTo("binary");
        JsonNode content = doc.path("paths").path("/uploads/{filename}").path("get").path("responses")
                .path("200").path("content");
        for (String type : List.of("image/jpeg", "image/png", "image/webp")) {
            assertThat(content.path(type).path("schema").path("format").asText()).isEqualTo("binary");
        }
        assertThat(content.has("application/json")).isFalse();
    }
}
