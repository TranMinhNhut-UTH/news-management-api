package com.uth.news.entity;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.assertThat;

class NewsTest {
    @Test
    void lifecycleSetsTimestampsAndPreservesCreationTimeOnUpdate() {
        User author = new User("writer", "hash");
        News news = new News("Title", "Content", "Category", null, author);
        news.onCreate();
        LocalDateTime createdAt = news.getCreatedAt();
        assertThat(createdAt).isNotNull();
        assertThat(news.getUpdatedAt()).isEqualTo(createdAt);
        assertThat(news.getAuthor()).isSameAs(author);

        ReflectionTestUtils.setField(news, "updatedAt", LocalDateTime.of(2000, 1, 1, 0, 0));
        news.onUpdate();
        assertThat(news.getCreatedAt()).isEqualTo(createdAt);
        assertThat(news.getUpdatedAt()).isAfterOrEqualTo(createdAt);
    }
}
