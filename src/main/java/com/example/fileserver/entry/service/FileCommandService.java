package com.example.fileserver.entry.service;

import com.example.fileserver.entry.ConflictPolicy;
import com.example.fileserver.entry.dto.CreateDirectoryRequest;
import com.example.fileserver.entry.dto.CreateDirectoryResponse;
import com.example.fileserver.entry.dto.UploadFileResponse;
import org.springframework.web.multipart.MultipartFile;

public interface FileCommandService {

    CreateDirectoryResponse createDirectory(CreateDirectoryRequest request);

    UploadFileResponse uploadFile(String parentPath, ConflictPolicy conflictPolicy, MultipartFile file);
}
