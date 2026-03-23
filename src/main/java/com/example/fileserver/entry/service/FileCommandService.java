package com.example.fileserver.entry.service;

import com.example.fileserver.entry.dto.CreateDirectoryRequest;
import com.example.fileserver.entry.dto.CreateDirectoryResponse;

public interface FileCommandService {

    CreateDirectoryResponse createDirectory(CreateDirectoryRequest request);
}
