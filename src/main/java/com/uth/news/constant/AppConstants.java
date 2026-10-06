package com.uth.news.constant;

import java.util.Set;

public final class AppConstants {

    public static final String API_PREFIX = "/api/v1";
    public static final int DEFAULT_PAGE_SIZE = 10;
    public static final int MAX_PAGE_SIZE = 100;
    public static final String DEFAULT_SORT_BY = "createdAt";
    public static final String DEFAULT_SORT_DIRECTION = "desc";
    public static final Set<String> ALLOWED_SORT_FIELDS = Set.of("title", "createdAt");
    public static final Set<String> ALLOWED_SORT_DIRECTIONS = Set.of("asc", "desc");
    public static final Set<String> ALLOWED_IMAGE_MIME_TYPES =
            Set.of("image/jpeg", "image/png", "image/webp");
    public static final long MAX_IMAGE_SIZE_BYTES = 5L * 1024 * 1024;

    private AppConstants() {
    }
}
