package com.example.fileserver.entry.dto;

import java.util.List;

public record DirectoryListResponse(
        String currentPath,
        List<FileEntryDto> entries
) {
}
