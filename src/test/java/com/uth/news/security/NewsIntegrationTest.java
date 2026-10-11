package com.uth.news.security;

import com.uth.news.config.*;
import com.uth.news.controller.NewsController;
import com.uth.news.entity.*;
import com.uth.news.exception.GlobalExceptionHandler;
import com.uth.news.repository.*;
import com.uth.news.service.NewsService;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = NewsIntegrationTest.TestApplication.class, properties = {
        "app.jwt.secret=test-only-secret-0123456789-abcdef", "app.jwt.expiration-ms=3600000"})
@AutoConfigureMockMvc
@SuppressWarnings("unchecked")
class NewsIntegrationTest {
    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class, JpaRepositoriesAutoConfiguration.class})
    @Import({SecurityConfig.class, OpenApiConfig.class, NewsController.class, NewsService.class,
            JwtService.class, CustomUserDetailsService.class, SecurityErrorHandler.class, GlobalExceptionHandler.class})
    static class TestApplication { }

    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @MockitoBean NewsRepository news;
    @MockitoBean UserRepository users;
    private News article;
    private String token;
    private static final String BODY = """
            {"title":"New title","content":"New content","category":"Tech","imageUrl":null,"authorId":999}
            """;

    @BeforeEach
    void setup() {
        User author = new User("writer", "hidden-hash");
        ReflectionTestUtils.setField(author, "id", 1L);
        when(users.findByUsername("writer")).thenReturn(Optional.of(author));
        when(users.findByUsername("other")).thenReturn(Optional.of(new User("other", "other-hash")));
        article = new News("Title", "Content", "Tech", "https://example.com/image.png", author);
        ReflectionTestUtils.setField(article, "id", 7L);
        when(news.findById(7L)).thenReturn(Optional.of(article));
        when(news.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        when(news.findAll(any(Pageable.class))).thenAnswer(call ->
                new PageImpl<>(List.of(article), call.getArgument(0), 21));
        when(news.findAll(any(Specification.class), any(Pageable.class))).thenAnswer(call ->
                new PageImpl<>(List.of(article), call.getArgument(1), 21));
        token = "Bearer " + jwt.generateToken("writer");
    }

    @Test
    void publicReadsHaveSafeAuthorAndDefaultPagination() throws Exception {
        mvc.perform(get("/api/v1/news")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(0)).andExpect(jsonPath("$.data.size").value(10))
                .andExpect(jsonPath("$.data.totalElements").value(21)).andExpect(jsonPath("$.data.totalPages").value(3));
        var captor = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(news).findAll(captor.capture());
        assertThat(captor.getValue().getSort().getOrderFor("createdAt").getDirection()).isEqualTo(Sort.Direction.DESC);
        mvc.perform(get("/api/v1/news/7")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.author.id").value(1)).andExpect(jsonPath("$.data.author.username").value("writer"))
                .andExpect(content().string(not(containsString("hidden-hash"))))
                .andExpect(jsonPath("$.data.author.passwordHash").doesNotExist());
    }

    @Test
    void privateRoutesRequireJwtAndInvalidTokenFails() throws Exception {
        mvc.perform(get("/api/v1/news/search")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.success").value(false));
        mvc.perform(post("/api/v1/news").contentType("application/json").content(BODY)).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/v1/news/7").contentType("application/json").content(BODY)).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/v1/news/7")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/news/search").header("Authorization", "Bearer invalid")).andExpect(status().isUnauthorized());
        verifyNoInteractions(news);
    }

    @Test
    void ownerCanCreateUpdateDeleteAndAuthorComesFromToken() throws Exception {
        mvc.perform(post("/api/v1/news").header("Authorization", token).contentType("application/json").content(BODY))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.author.id").value(1));
        mvc.perform(put("/api/v1/news/7").header("Authorization", token).contentType("application/json").content(BODY))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.title").value("New title"))
                .andExpect(jsonPath("$.data.content").value("New content")).andExpect(jsonPath("$.data.imageUrl").doesNotExist());
        mvc.perform(delete("/api/v1/news/7").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        verify(news).delete(article);
        assertThat(article.getAuthor().getUsername()).isEqualTo("writer");
    }

    @Test
    void otherUserCannotUpdateOrDelete() throws Exception {
        String other = "Bearer " + jwt.generateToken("other");
        mvc.perform(put("/api/v1/news/7").header("Authorization", other).contentType("application/json").content(BODY))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.success").value(false));
        mvc.perform(delete("/api/v1/news/7").header("Authorization", other)).andExpect(status().isForbidden());
        verify(news, never()).saveAndFlush(any());
        verify(news, never()).delete(any(News.class));
        assertThat(article.getTitle()).isEqualTo("Title");
    }

    @Test
    void missingNewsReturns404ForReadUpdateDelete() throws Exception {
        mvc.perform(get("/api/v1/news/99")).andExpect(status().isNotFound()).andExpect(jsonPath("$.success").value(false));
        mvc.perform(put("/api/v1/news/99").header("Authorization", token).contentType("application/json").content(BODY))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/news/99").header("Authorization", token)).andExpect(status().isNotFound());
    }

    @Test
    void validatesBodiesAndMalformedInputs() throws Exception {
        for (String method : List.of("POST", "PUT")) {
            String path = method.equals("POST") ? "/api/v1/news" : "/api/v1/news/7";
            mvc.perform(request(org.springframework.http.HttpMethod.valueOf(method), path).header("Authorization", token)
                    .contentType("application/json").content("{\"title\":\" \",\"content\":\"\",\"category\":\"\"}"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.title").isArray())
                    .andExpect(jsonPath("$.errors.content").isArray()).andExpect(jsonPath("$.errors.category").isArray());
        }
        mvc.perform(post("/api/v1/news").header("Authorization", token).contentType("application/json").content("{"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false));
        mvc.perform(get("/api/v1/news/not-a-number")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/news").param("page", "abc")).andExpect(status().isBadRequest());
    }

    @Test
    void rejectsInvalidPaginationAndSort() throws Exception {
        for (String[] pair : List.of(new String[]{"page", "-1"}, new String[]{"size", "0"}, new String[]{"size", "101"})) {
            mvc.perform(get("/api/v1/news").param(pair[0], pair[1])).andExpect(status().isBadRequest());
            mvc.perform(get("/api/v1/news/search").header("Authorization", token).param(pair[0], pair[1]))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false));
        }
        for (String[] pair : List.of(new String[]{"sortBy", "author.passwordHash"}, new String[]{"direction", "invalid"})) {
            mvc.perform(get("/api/v1/news/search").header("Authorization", token).param(pair[0], pair[1]))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void searchPassesAllowedSortAndPagination() throws Exception {
        for (String field : List.of("title", "createdAt")) {
            for (String direction : List.of("asc", "desc")) {
                mvc.perform(get("/api/v1/news/search").header("Authorization", token).param("keyword", "news")
                        .param("category", "Tech").param("sortBy", field).param("direction", direction)
                        .param("page", "1").param("size", "5"))
                        .andExpect(status().isOk()).andExpect(jsonPath("$.data.page").value(1))
                        .andExpect(jsonPath("$.data.size").value(5)).andExpect(jsonPath("$.data.totalPages").value(5));
            }
        }
        var captor = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(news, times(4)).findAll(any(Specification.class), captor.capture());
        assertThat(captor.getAllValues()).extracting(p -> p.getSort().iterator().next().toString())
                .containsExactly("title: ASC", "title: DESC", "createdAt: ASC", "createdAt: DESC");
    }

    @Test
    void imageUrlIsSavedOnCreateAndReturnedByPublicRead() throws Exception {
        String url = "http://localhost:8080/uploads/00000000-0000-0000-0000-000000000001.png";
        String body = BODY.replace("\"imageUrl\":null", "\"imageUrl\":\"" + url + "\"");
        when(news.saveAndFlush(any())).thenAnswer(call -> {
            News saved = call.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 8L);
            when(news.findById(8L)).thenReturn(Optional.of(saved));
            return saved;
        });
        mvc.perform(post("/api/v1/news").header("Authorization", token).contentType("application/json").content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.imageUrl").value(url));
        mvc.perform(get("/api/v1/news/8")).andExpect(status().isOk()).andExpect(jsonPath("$.data.imageUrl").value(url));
    }

    @Test
    void onlyOwnerCanChangeImageUrlAndPublicResponsesReturnUpdatedUrl() throws Exception {
        String url = "http://localhost:8080/uploads/00000000-0000-0000-0000-000000000002.webp";
        String body = BODY.replace("\"imageUrl\":null", "\"imageUrl\":\"" + url + "\"");
        mvc.perform(put("/api/v1/news/7").header("Authorization", "Bearer " + jwt.generateToken("other"))
                        .contentType("application/json").content(body)).andExpect(status().isForbidden());
        assertThat(article.getImageUrl()).isEqualTo("https://example.com/image.png");
        mvc.perform(put("/api/v1/news/7").header("Authorization", token).contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.imageUrl").value(url));
        mvc.perform(get("/api/v1/news/7")).andExpect(status().isOk()).andExpect(jsonPath("$.data.imageUrl").value(url));
        mvc.perform(get("/api/v1/news")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].imageUrl").value(url));
    }

    @Test
    void emptyPageAndMaximumSizeAreSupported() throws Exception {
        when(news.findAll(any(Pageable.class))).thenAnswer(call -> new PageImpl<>(List.of(), call.getArgument(0), 0));
        mvc.perform(get("/api/v1/news").param("size", "100")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty()).andExpect(jsonPath("$.data.totalPages").value(0));
    }
}
