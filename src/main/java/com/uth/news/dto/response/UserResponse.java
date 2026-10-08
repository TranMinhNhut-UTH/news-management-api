package com.uth.news.dto.response;

import com.uth.news.entity.User;

public record UserResponse(Long id, String username) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getUsername());
    }
}
