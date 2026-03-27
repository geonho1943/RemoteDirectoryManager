package com.example.fileserver.common.response;

import java.time.LocalDateTime;

public record ErrorResponse(
        String code,
        String message,
        String path,
        LocalDateTime timestamp
) {
}
