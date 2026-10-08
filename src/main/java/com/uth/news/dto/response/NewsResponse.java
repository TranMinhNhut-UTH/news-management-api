package com.uth.news.dto.response;

import com.uth.news.entity.News;
import java.time.LocalDateTime;

public record NewsResponse(Long id, String title, String content, String category,
                           String imageUrl, UserResponse author,
                           LocalDateTime createdAt, LocalDateTime updatedAt) {
    public static NewsResponse from(News news) {
        return new NewsResponse(news.getId(), news.getTitle(), news.getContent(),
                news.getCategory(), news.getImageUrl(), UserResponse.from(news.getAuthor()),
                news.getCreatedAt(), news.getUpdatedAt());
    }
}
