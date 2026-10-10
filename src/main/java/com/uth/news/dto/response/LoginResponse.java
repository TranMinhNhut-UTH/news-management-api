package com.uth.news.dto.response;

public record LoginResponse(String accessToken, String tokenType, long expiresIn) {
}
