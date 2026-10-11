package com.uth.news.e2e;

import com.fasterxml.jackson.databind.*;
import com.uth.news.ImageFixtures;
import com.uth.news.NewsManagementApplication;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = NewsManagementApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"debug=false", "trace=false", "logging.level.root=OFF", "logging.level.org.springframework=OFF",
                "logging.level.com.uth.news=OFF", "spring.main.banner-mode=off"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class NewsApiE2EIT {
    private static Path uploadDirectory;

    @DynamicPropertySource
    static void isolatedDatabase(DynamicPropertyRegistry registry) {
        String url = System.getenv("DB_URL");
        if (!"true".equals(System.getenv("NEWS_E2E_ENABLED")) || url == null
                || !url.matches("^jdbc:mysql://[^/]+/news_management_e2e(?:\\?.*)?$")) {
            throw new IllegalStateException("E2E stopped: explicit opt-in and exact news_management_e2e URL required");
        }
        // This happens before Spring/Hibernate can create or update tables.
        try (Connection connection = DriverManager.getConnection(url,
                System.getenv("DB_USERNAME"), System.getenv("DB_PASSWORD"));
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT DATABASE()")) {
            if (!result.next() || !"news_management_e2e".equals(result.getString(1))) {
                throw new IllegalStateException("Wrong database");
            }
            uploadDirectory = Files.createTempDirectory("news-api-e2e-");
        } catch (Exception exception) {
            // Do not attach driver errors/connection properties to test reports.
            throw new IllegalStateException("E2E stopped: cannot connect to verified news_management_e2e database");
        }
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> System.getenv("DB_USERNAME"));
        registry.add("spring.datasource.password", () -> System.getenv("DB_PASSWORD"));
        registry.add("app.jwt.secret", () -> System.getenv("JWT_SECRET"));
        registry.add("app.upload-dir", () -> uploadDirectory.toString());
        registry.add("app.base-url", () -> "http://localhost");
        registry.add("app.jwt.expiration-ms", () -> 3600000);
    }

    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    private final String marker = "e2e_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
    private final String password = UUID.randomUUID().toString();
    private final Map<Long, String> createdUsers = new LinkedHashMap<>();
    private String writerToken, otherToken, imageUrl, filename;
    private long writerId, newsId;

    @BeforeAll
    void silenceHttpAndSqlLoggingBeforeCredentialsAreUsed() {
        // Some local logging configurations override Spring log levels. Disable
        // all Logback loggers before HTTP auth, so token bodies never enter reports.
        var context = (ch.qos.logback.classic.LoggerContext) org.slf4j.LoggerFactory.getILoggerFactory();
        for (var logger : context.getLoggerList()) { logger.setLevel(ch.qos.logback.classic.Level.OFF); }
    }

    private JsonNode request(HttpMethod method, String path, Object body, String token, int expected) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) { headers.setBearerAuth(token); }
        if (body != null) { headers.setContentType(MediaType.APPLICATION_JSON); }
        var response = http.exchange(path, method, new HttpEntity<>(body, headers), String.class);
        assertThat(response.getStatusCode().value()).as("%s %s status", method, path).isEqualTo(expected);
        JsonNode json = mapper.readTree(response.getBody());
        assertThat(json.path("success").asBoolean()).isEqualTo(expected < 400);
        assertThat(json.has("message") && json.has("data")).isTrue();
        return json.path("data");
    }

    private String registerAndLogin(String username) throws Exception {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE username = ?", Long.class, username)).isZero();
        var credentials = Map.of("username", username, "password", password);
        JsonNode user = request(HttpMethod.POST, "/api/v1/auth/register", credentials, null, 201);
        long id = user.path("id").asLong();
        createdUsers.put(id, username);
        assertThat(id).isPositive();
        assertThat(user.has("passwordHash")).isFalse();
        JsonNode login = request(HttpMethod.POST, "/api/v1/auth/login", credentials, null, 200);
        // Never include the token or password in assertions/report messages.
        assertThat(login.path("accessToken").asText().isBlank()).isFalse();
        assertThat(login.path("tokenType").asText()).isEqualTo("Bearer");
        return login.path("accessToken").asText();
    }

    private Map<String, Object> article(String title, String url) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", title);
        body.put("content", marker + " content");
        body.put("category", marker);
        body.put("imageUrl", url);
        return body;
    }

    @Test @Order(1)
    void authPersistsUsersAndChecksCredentials() throws Exception {
        assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class)).isEqualTo("news_management_e2e");
        writerToken = registerAndLogin(marker + "_writer");
        otherToken = registerAndLogin(marker + "_other");
        writerId = createdUsers.keySet().iterator().next();
        String storedHash = jdbc.queryForObject("SELECT password_hash FROM users WHERE id = ?", String.class, writerId);
        assertThat(storedHash != null && storedHash.startsWith("$2") && !storedHash.equals(password)).isTrue();
        request(HttpMethod.POST, "/api/v1/auth/register", Map.of("username", marker + "_writer", "password", password), null, 409);
        request(HttpMethod.POST, "/api/v1/auth/login", Map.of("username", marker + "_writer", "password", UUID.randomUUID().toString()), null, 401);
    }

    @Test @Order(2)
    void uploadAndCreatePersistImageAuthorAndNews() throws Exception {
        byte[] bytes = ImageFixtures.image("png");
        var file = new ByteArrayResource(bytes) {
            @Override public String getFilename() { return "e2e.png"; }
        };
        var parts = new LinkedMultiValueMap<String, Object>();
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.IMAGE_PNG);
        parts.add("file", new HttpEntity<>(file, fileHeaders));
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(writerToken);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        var uploaded = http.postForEntity("/api/v1/news/upload-image", new HttpEntity<>(parts, headers), String.class);
        assertThat(uploaded.getStatusCode().value()).isEqualTo(201);
        imageUrl = mapper.readTree(uploaded.getBody()).path("data").path("imageUrl").asText();
        filename = java.net.URI.create(imageUrl).getPath().substring("/uploads/".length());
        assertThat(Files.readAllBytes(uploadDirectory.resolve(filename))).isEqualTo(bytes);
        var read = http.getForEntity("/uploads/" + filename, byte[].class);
        assertThat(read.getStatusCode().value()).isEqualTo(200);
        assertThat(read.getBody()).isEqualTo(bytes);
        JsonNode news = request(HttpMethod.POST, "/api/v1/news", article(marker + " title", imageUrl), writerToken, 201);
        newsId = news.path("id").asLong();
        assertThat(news.path("author").path("id").asLong()).isEqualTo(writerId);
        assertThat(jdbc.queryForObject("SELECT image_url FROM news WHERE id = ?", String.class, newsId)).isEqualTo(imageUrl);
        assertThat(jdbc.queryForObject("SELECT author_id FROM news WHERE id = ?", Long.class, newsId)).isEqualTo(writerId);
        assertThat(jdbc.queryForObject("SELECT created_at FROM news WHERE id = ?", Timestamp.class, newsId)).isNotNull();
        request(HttpMethod.DELETE, "/api/v1/news/images/" + filename, null, writerToken, 409);
        assertThat(uploadDirectory.resolve(filename)).exists();
    }

    @Test @Order(3)
    void publicReadSearchSortPaginationAndOwnershipUseRealDatabase() throws Exception {
        JsonNode detail = request(HttpMethod.GET, "/api/v1/news/" + newsId, null, null, 200);
        assertThat(detail.path("imageUrl").asText()).isEqualTo(imageUrl);
        request(HttpMethod.POST, "/api/v1/news", article(marker + " z", null), writerToken, 201);
        JsonNode page = request(HttpMethod.GET, "/api/v1/news/search?keyword=" + marker
                + "&category=" + marker + "&sortBy=title&direction=asc&page=0&size=1", null, writerToken, 200);
        assertThat(page.path("totalElements").asInt()).isEqualTo(2);
        assertThat(page.path("totalPages").asInt()).isEqualTo(2);
        assertThat(page.path("items").path(0).path("id").asLong()).isEqualTo(newsId);
        JsonNode second = request(HttpMethod.GET, "/api/v1/news/search?category=" + marker
                + "&sortBy=title&direction=asc&page=1&size=1", null, writerToken, 200);
        assertThat(second.path("items").path(0).path("title").asText()).isEqualTo(marker + " z");
        request(HttpMethod.GET, "/api/v1/news", null, null, 200);
        request(HttpMethod.GET, "/api/v1/news/search", null, null, 401);
        request(HttpMethod.POST, "/api/v1/news", article(marker, null), null, 401);
        request(HttpMethod.GET, "/api/v1/news/search", null, "invalid", 401);
        request(HttpMethod.PUT, "/api/v1/news/" + newsId, article(marker + " forbidden", null), otherToken, 403);
        request(HttpMethod.DELETE, "/api/v1/news/" + newsId, null, otherToken, 403);
        request(HttpMethod.DELETE, "/api/v1/news/images/" + filename, null, null, 401);
        assertThat(jdbc.queryForObject("SELECT title FROM news WHERE id = ?", String.class, newsId)).isEqualTo(marker + " title");
    }

    @Test @Order(4)
    void updateRemovesReferenceAndImageCanBeDeleted() throws Exception {
        request(HttpMethod.PUT, "/api/v1/news/" + newsId, article(marker + " updated", null), writerToken, 200);
        assertThat(jdbc.queryForObject("SELECT title FROM news WHERE id = ?", String.class, newsId)).isEqualTo(marker + " updated");
        assertThat(jdbc.queryForObject("SELECT image_url FROM news WHERE id = ?", String.class, newsId)).isNull();
        assertThat(jdbc.queryForObject("SELECT updated_at >= created_at FROM news WHERE id = ?", Boolean.class, newsId)).isTrue();
        JsonNode detail = request(HttpMethod.GET, "/api/v1/news/" + newsId, null, null, 200);
        assertThat(detail.path("title").asText()).isEqualTo(marker + " updated");
        request(HttpMethod.DELETE, "/api/v1/news/images/" + filename, null, writerToken, 200);
        assertThat(uploadDirectory.resolve(filename)).doesNotExist();
        request(HttpMethod.GET, "/uploads/" + filename, null, null, 404);
        request(HttpMethod.DELETE, "/api/v1/news/images/" + filename, null, writerToken, 404);
    }

    @Test @Order(5)
    void deleteNewsAndValidationReturnCorrectStatuses() throws Exception {
        request(HttpMethod.GET, "/api/v1/news/search?size=101", null, writerToken, 400);
        request(HttpMethod.POST, "/api/v1/news", article("", null), writerToken, 400);
        request(HttpMethod.DELETE, "/api/v1/news/images/not-an-image", null, writerToken, 400);
        request(HttpMethod.DELETE, "/api/v1/news/" + newsId, null, writerToken, 200);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM news WHERE id = ?", Long.class, newsId)).isZero();
        request(HttpMethod.GET, "/api/v1/news/" + newsId, null, null, 404);
    }

    @AfterAll
    void cleanupOnlyThisRunsData() throws Exception {
        // Never truncate tables or delete arbitrary pre-existing E2E data.
        assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class)).isEqualTo("news_management_e2e");
        for (var user : createdUsers.entrySet()) {
            String actual = jdbc.queryForObject("SELECT username FROM users WHERE id = ?", String.class, user.getKey());
            assertThat(actual).isEqualTo(user.getValue());
            jdbc.update("DELETE FROM news WHERE author_id = ?", user.getKey());
            jdbc.update("DELETE FROM users WHERE id = ? AND username = ?", user.getKey(), user.getValue());
        }
        if (uploadDirectory != null) {
            try (var files = Files.list(uploadDirectory)) {
                for (Path file : files.toList()) { Files.delete(file); }
            }
            Files.delete(uploadDirectory);
        }
    }
}
