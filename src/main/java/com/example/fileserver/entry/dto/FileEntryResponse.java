package com.example.fileserver.entry.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;

public record FileEntryResponse(
        String entryType,
        String relativePath,
        String parentPath,
        String name,
        String extension,
        String mimeType,
        Long sizeBytes,
        LocalDateTime modifiedAt,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        LocalDateTime createdAtFs,
        boolean hidden,
        Long fileId,
        List<TagSummaryDto> tags
) {
}
