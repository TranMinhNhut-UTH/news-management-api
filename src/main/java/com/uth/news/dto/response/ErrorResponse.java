package com.uth.news.dto.response;

import java.util.List;
import java.util.Map;

public record ErrorResponse(boolean success, String message, Object data,
                            Map<String, List<String>> errors) {
    public static ErrorResponse validation(Map<String, List<String>> errors) {
        return new ErrorResponse(false, "Validation failed", null, errors);
    }
}
