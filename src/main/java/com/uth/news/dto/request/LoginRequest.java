package com.uth.news.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

public record LoginRequest(
        @Schema(example = "writer") @NotBlank @Size(max = 50) String username,
        @Schema(description = "At most 72 UTF-8 bytes", format = "password", example = "password123")
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        @NotBlank @Size(max = 72) String password) {
    @Override
    public String toString() { return "LoginRequest[username=" + username + ", password=REDACTED]"; }
}
