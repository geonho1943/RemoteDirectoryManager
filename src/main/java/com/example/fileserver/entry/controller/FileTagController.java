package com.example.fileserver.entry.controller;

import com.example.fileserver.entry.dto.AssignTagsRequest;
import com.example.fileserver.entry.dto.FileTagsResponse;
import com.example.fileserver.entry.dto.RemoveTagsRequest;
import com.example.fileserver.entry.service.FileTagService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/files/tags")
public class FileTagController {

    private final FileTagService fileTagService;

    public FileTagController(FileTagService fileTagService) {
        this.fileTagService = fileTagService;
    }

    @PostMapping
    public ResponseEntity<FileTagsResponse> assignTags(@RequestBody AssignTagsRequest request) {
        return ResponseEntity.ok(fileTagService.assignTags(request));
    }

    @DeleteMapping
    public ResponseEntity<FileTagsResponse> removeTags(@RequestBody RemoveTagsRequest request) {
        return ResponseEntity.ok(fileTagService.removeTags(request));
    }
}
