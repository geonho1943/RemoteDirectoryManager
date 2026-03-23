package com.example.fileserver.entry.controller;

import com.example.fileserver.entry.dto.CreateDirectoryRequest;
import com.example.fileserver.entry.dto.CreateDirectoryResponse;
import com.example.fileserver.entry.service.FileCommandService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/directories")
public class DirectoryCommandController {

    private final FileCommandService fileCommandService;

    public DirectoryCommandController(FileCommandService fileCommandService) {
        this.fileCommandService = fileCommandService;
    }

    @PostMapping
    public ResponseEntity<CreateDirectoryResponse> createDirectory(
            @RequestBody CreateDirectoryRequest request
    ) {
        CreateDirectoryResponse response = fileCommandService.createDirectory(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
