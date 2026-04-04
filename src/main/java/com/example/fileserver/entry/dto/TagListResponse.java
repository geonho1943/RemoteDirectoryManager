package com.example.fileserver.entry.dto;

import java.util.List;

public record TagListResponse(
        List<TagSummaryDto> tags
) {
}
