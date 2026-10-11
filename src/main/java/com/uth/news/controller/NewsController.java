package com.uth.news.controller;

import com.uth.news.constant.AppConstants;
import com.uth.news.dto.request.NewsRequest;
import com.uth.news.dto.response.*;
import com.uth.news.service.NewsService;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(AppConstants.API_PREFIX + "/news")
public class NewsController {
    private final NewsService service;

    public NewsController(NewsService service) { this.service = service; }

    @GetMapping
    @SecurityRequirements
    public ApiResponse<PageResponse<NewsResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + AppConstants.DEFAULT_PAGE_SIZE) int size) {
        return ApiResponse.success("News retrieved successfully", service.list(page, size));
    }

    @GetMapping("/{id}")
    @SecurityRequirements
    public ApiResponse<NewsResponse> get(@PathVariable Long id) {
        return ApiResponse.success("News retrieved successfully", service.get(id));
    }

    @GetMapping("/search")
    public ApiResponse<PageResponse<NewsResponse>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = AppConstants.DEFAULT_SORT_BY) String sortBy,
            @RequestParam(defaultValue = AppConstants.DEFAULT_SORT_DIRECTION) String direction,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + AppConstants.DEFAULT_PAGE_SIZE) int size) {
        return ApiResponse.success("News retrieved successfully",
                service.search(keyword, category, sortBy, direction, page, size));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<NewsResponse>> create(@Valid @RequestBody NewsRequest request,
                                                            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                "News created successfully", service.create(request, authentication.getName())));
    }

    @PutMapping("/{id}")
    public ApiResponse<NewsResponse> update(@PathVariable Long id, @Valid @RequestBody NewsRequest request,
                                            Authentication authentication) {
        return ApiResponse.success("News updated successfully", service.update(id, request, authentication.getName()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, Authentication authentication) {
        service.delete(id, authentication.getName());
        return ApiResponse.success("News deleted successfully", null);
    }
}
