package com.example.fileserver.entry.dto;

import java.time.LocalDateTime;
import java.util.List;

public record FileEntryDto(
        String entryType,
        String relativePath,
        String parentPath,
        String name,
        String extension,
        String mimeType,
        Long sizeBytes,
        LocalDateTime modifiedAt,
        boolean hidden,
        Long fileId,
        List<TagSummaryDto> tags
) {
}
