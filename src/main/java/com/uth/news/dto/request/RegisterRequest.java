package com.uth.news.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

public record RegisterRequest(
        @Schema(example = "writer") @NotBlank @Size(max = 50) String username,
        @Schema(description = "8–72 characters and at most 72 UTF-8 bytes", format = "password", example = "password123")
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        @NotBlank @Size(min = 8, max = 72) String password) {
    @Override
    public String toString() { return "RegisterRequest[username=" + username + ", password=REDACTED]"; }
}
