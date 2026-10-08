package com.uth.news.exception;

import com.uth.news.dto.request.NewsRequest;
import jakarta.validation.Valid;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GlobalExceptionHandlerTest {
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new FixtureController())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    static Stream<Arguments> errors() {
        return Stream.of(
                Arguments.of("bad", 400, "Bad input"),
                Arguments.of("missing", 404, "News not found"),
                Arguments.of("conflict", 409, "Username already exists"),
                Arguments.of("forbidden", 403, "Forbidden"),
                Arguments.of("database", 409, "Data conflicts with an existing record or database constraint"),
                Arguments.of("unexpected", 500, "Internal server error"));
    }

    @ParameterizedTest
    @MethodSource("errors")
    void mapsExceptionsWithoutExposingInternalDetails(String kind, int statusCode, String message)
            throws Exception {
        mvc.perform(get("/fixture/errors/" + kind))
                .andExpect(status().is(statusCode))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(message))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(content().string(not(containsString("secret database details"))));
    }

    @Test
    void validationReturnsFieldErrors() throws Exception {
        mvc.perform(post("/fixture/news").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\",\"content\":\"\",\"category\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors.title").isArray())
                .andExpect(jsonPath("$.errors.content").isArray())
                .andExpect(jsonPath("$.errors.category").isArray());
    }

    @Test
    void malformedJsonIsBadRequestWithStandardEnvelope() throws Exception {
        mvc.perform(post("/fixture/news").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Bad Request"));
    }

    @Test
    void invalidPathTypeIsBadRequest() throws Exception {
        mvc.perform(get("/fixture/id/not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void unsupportedMethodPreservesStatusAndAllowHeader() throws Exception {
        mvc.perform(delete("/fixture/news"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().exists("Allow"))
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void unsupportedMediaTypeIs415() throws Exception {
        mvc.perform(post("/fixture/news").contentType(MediaType.TEXT_PLAIN).content("text"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.success").value(false));
    }

    // Test-only endpoints; no production controller or API is implemented in Phase 2.
    @RestController
    static class FixtureController {
        @PostMapping("/fixture/news")
        String validate(@Valid @RequestBody NewsRequest request) { return "ok"; }

        @GetMapping("/fixture/id/{id}")
        Long id(@PathVariable Long id) { return id; }

        @GetMapping("/fixture/errors/{kind}")
        String error(@PathVariable String kind) {
            throw switch (kind) {
                case "bad" -> new BadRequestException("Bad input");
                case "missing" -> new ResourceNotFoundException("News not found");
                case "conflict" -> new ConflictException("Username already exists");
                case "forbidden" -> new ForbiddenException("Forbidden");
                case "database" -> new DataIntegrityViolationException("secret database details");
                default -> new IllegalStateException("secret database details");
            };
        }
    }
}
