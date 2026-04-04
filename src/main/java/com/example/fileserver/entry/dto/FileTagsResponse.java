package com.example.fileserver.entry.dto;

import java.util.List;

public record FileTagsResponse(
        Long fileId,
        String filePath,
        List<TagSummaryDto> tags
) {
}
