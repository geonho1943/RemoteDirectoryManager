package com.example.fileserver.entry.dto;

public record CreateDirectoryRequest(
        String parentPath,
        String name
) {
}
