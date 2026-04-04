package com.example.fileserver.entry.dto;

import java.util.List;

public record AssignTagsRequest(
        String path,
        List<Long> tagIds,
        List<String> tagNames
) {
}
