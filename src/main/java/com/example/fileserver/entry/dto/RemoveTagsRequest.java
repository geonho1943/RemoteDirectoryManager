package com.example.fileserver.entry.dto;

import java.util.List;

public record RemoveTagsRequest(
        String path,
        List<Long> tagIds
) {
}
