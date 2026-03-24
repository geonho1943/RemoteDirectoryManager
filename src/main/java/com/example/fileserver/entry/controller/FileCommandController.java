package com.example.fileserver.entry.controller;

import com.example.fileserver.entry.ConflictPolicy;
import com.example.fileserver.entry.dto.UploadFileResponse;
import com.example.fileserver.entry.service.FileCommandService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/files")
public class FileCommandController {

    private final FileCommandService fileCommandService;

    public FileCommandController(FileCommandService fileCommandService) {
        this.fileCommandService = fileCommandService;
    }

    @PostMapping("/upload")
    public ResponseEntity<UploadFileResponse> uploadFile(
            @RequestParam("parentPath") String parentPath,
            @RequestParam("conflictPolicy") ConflictPolicy conflictPolicy,
            @RequestPart("file") MultipartFile file
    ) {
        UploadFileResponse response = fileCommandService.uploadFile(parentPath, conflictPolicy, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
