package com.example.fileserver.entry.dto;

import java.time.LocalDateTime;
import java.util.List;

public record FileEntryDetailResponse(
        String entryType,
        String relativePath,
        String parentPath,
        String name,
        String extension,
        String mimeType,
        Long sizeBytes,
        LocalDateTime modifiedAt,
        LocalDateTime createdAtFs,
        boolean hidden,
        Long fileId,
        List<TagSummaryDto> tags
) {
}
