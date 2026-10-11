package com.uth.news.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

public record NewsRequest(
        @Schema(example = "Spring Boot news") @NotBlank @Size(max = 255) String title,
        @Schema(example = "News content") @NotBlank String content,
        @Schema(description = "Category string, not a category ID", example = "Tech")
        @NotBlank @Size(max = 100) String category,
        @Schema(description = "Optional URL returned by upload; null removes the URL on PUT", nullable = true,
                example = "http://localhost:8080/uploads/00000000-0000-0000-0000-000000000001.png")
        @Size(max = 500) String imageUrl) {
}
