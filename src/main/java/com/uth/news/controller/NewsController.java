package com.uth.news.controller;

import com.uth.news.constant.AppConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Parameter;

import com.uth.news.dto.request.NewsRequest;
import com.uth.news.dto.response.*;
import com.uth.news.service.NewsService;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Tag(name = "News")
@RestController
@RequestMapping(AppConstants.API_PREFIX + "/news")
public class NewsController {
    private final NewsService service;

    public NewsController(NewsService service) { this.service = service; }

    @GetMapping
    @SecurityRequirements
    @Operation(summary = "List public news, newest first")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "News retrieved successfully", useReturnTypeSchema = true,
                content = @Content(mediaType = "application/json", examples = @ExampleObject(value = "{\"success\":true,\"message\":\"News retrieved successfully\",\"data\":{\"items\":[{\"id\":1,\"title\":\"Spring Boot news\",\"content\":\"News content\",\"category\":\"Tech\",\"imageUrl\":\"http://localhost:8080/uploads/00000000-0000-0000-0000-000000000001.png\",\"author\":{\"id\":1,\"username\":\"writer\"},\"createdAt\":\"2026-10-11T10:00:00\",\"updatedAt\":\"2026-10-11T10:00:00\"}],\"page\":0,\"size\":10,\"totalElements\":1,\"totalPages\":1}}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", ref = "#/components/responses/InternalServerError")
    })
    public ApiResponse<PageResponse<NewsResponse>> list(
            @Parameter(description = "Zero-based page", schema = @Schema(minimum = "0", defaultValue = "0"))
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size; default 10, maximum 100", schema = @Schema(minimum = "1", maximum = "100", defaultValue = "" + AppConstants.DEFAULT_PAGE_SIZE))
            @RequestParam(defaultValue = "" + AppConstants.DEFAULT_PAGE_SIZE) int size) {
        return ApiResponse.success("News retrieved successfully", service.list(page, size));
    }

    @GetMapping("/{id}")
    @SecurityRequirements
    @Operation(summary = "Read public news detail")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "News retrieved successfully", useReturnTypeSchema = true,
                content = @Content(mediaType = "application/json", examples = @ExampleObject(value = "{\"success\":true,\"message\":\"News retrieved successfully\",\"data\":{\"id\":1,\"title\":\"Spring Boot news\",\"content\":\"News content\",\"category\":\"Tech\",\"imageUrl\":\"http://localhost:8080/uploads/00000000-0000-0000-0000-000000000001.png\",\"author\":{\"id\":1,\"username\":\"writer\"},\"createdAt\":\"2026-10-11T10:00:00\",\"updatedAt\":\"2026-10-11T10:00:00\"}}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", ref = "#/components/responses/InternalServerError")
    })
    public ApiResponse<NewsResponse> get(@PathVariable Long id) {
        return ApiResponse.success("News retrieved successfully", service.get(id));
    }

    @GetMapping("/search")
    @Operation(summary = "Search title/content, filter category and paginate")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "News retrieved successfully", useReturnTypeSchema = true,
                content = @Content(mediaType = "application/json", examples = @ExampleObject(value = "{\"success\":true,\"message\":\"News retrieved successfully\",\"data\":{\"items\":[{\"id\":1,\"title\":\"Spring Boot news\",\"content\":\"News content\",\"category\":\"Tech\",\"imageUrl\":\"http://localhost:8080/uploads/00000000-0000-0000-0000-000000000001.png\",\"author\":{\"id\":1,\"username\":\"writer\"},\"createdAt\":\"2026-10-11T10:00:00\",\"updatedAt\":\"2026-10-11T10:00:00\"}],\"page\":0,\"size\":10,\"totalElements\":1,\"totalPages\":1}}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", ref = "#/components/responses/InternalServerError")
    })
    public ApiResponse<PageResponse<NewsResponse>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @Parameter(schema = @Schema(allowableValues = {"title", "createdAt"}, defaultValue = AppConstants.DEFAULT_SORT_BY))
            @RequestParam(defaultValue = AppConstants.DEFAULT_SORT_BY) String sortBy,
            @Parameter(schema = @Schema(allowableValues = {"asc", "desc"}, defaultValue = AppConstants.DEFAULT_SORT_DIRECTION))
            @RequestParam(defaultValue = AppConstants.DEFAULT_SORT_DIRECTION) String direction,
            @Parameter(description = "Zero-based page", schema = @Schema(minimum = "0", defaultValue = "0"))
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size; default 10, maximum 100", schema = @Schema(minimum = "1", maximum = "100", defaultValue = "" + AppConstants.DEFAULT_PAGE_SIZE))
            @RequestParam(defaultValue = "" + AppConstants.DEFAULT_PAGE_SIZE) int size) {
        return ApiResponse.success("News retrieved successfully",
                service.search(keyword, category, sortBy, direction, page, size));
    }

    @PostMapping
    @Operation(summary = "Create news with author from JWT")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "News created successfully", useReturnTypeSchema = true,
                content = @Content(mediaType = "application/json", examples = @ExampleObject(value = "{\"success\":true,\"message\":\"News created successfully\",\"data\":{\"id\":1,\"title\":\"Spring Boot news\",\"content\":\"News content\",\"category\":\"Tech\",\"imageUrl\":\"http://localhost:8080/uploads/00000000-0000-0000-0000-000000000001.png\",\"author\":{\"id\":1,\"username\":\"writer\"},\"createdAt\":\"2026-10-11T10:00:00\",\"updatedAt\":\"2026-10-11T10:00:00\"}}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "415", ref = "#/components/responses/UnsupportedMediaType"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", ref = "#/components/responses/InternalServerError")
    })
    public ResponseEntity<ApiResponse<NewsResponse>> create(@Valid @RequestBody NewsRequest request,
                                                            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                "News created successfully", service.create(request, authentication.getName())));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Replace news fields; author only")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "News updated successfully", useReturnTypeSchema = true,
                content = @Content(mediaType = "application/json", examples = @ExampleObject(value = "{\"success\":true,\"message\":\"News updated successfully\",\"data\":{\"id\":1,\"title\":\"Spring Boot news\",\"content\":\"News content\",\"category\":\"Tech\",\"imageUrl\":\"http://localhost:8080/uploads/00000000-0000-0000-0000-000000000001.png\",\"author\":{\"id\":1,\"username\":\"writer\"},\"createdAt\":\"2026-10-11T10:00:00\",\"updatedAt\":\"2026-10-11T10:00:00\"}}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "415", ref = "#/components/responses/UnsupportedMediaType"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", ref = "#/components/responses/InternalServerError")
    })
    public ApiResponse<NewsResponse> update(@PathVariable Long id, @Valid @RequestBody NewsRequest request,
                                            Authentication authentication) {
        return ApiResponse.success("News updated successfully", service.update(id, request, authentication.getName()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete news; author only")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "News deleted successfully", useReturnTypeSchema = true,
                content = @Content(mediaType = "application/json", examples = @ExampleObject(value = "{\"success\":true,\"message\":\"News deleted successfully\",\"data\":null}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", ref = "#/components/responses/InternalServerError")
    })
    public ApiResponse<Void> delete(@PathVariable Long id, Authentication authentication) {
        service.delete(id, authentication.getName());
        return ApiResponse.success("News deleted successfully", null);
    }
}
