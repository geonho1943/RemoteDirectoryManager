package com.example.fileserver.entry.controller;

import com.example.fileserver.entry.dto.TagListResponse;
import com.example.fileserver.entry.service.FileTagService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tags")
public class TagQueryController {

    private final FileTagService fileTagService;

    public TagQueryController(FileTagService fileTagService) {
        this.fileTagService = fileTagService;
    }

    @GetMapping
    public TagListResponse listTags() {
        return fileTagService.listTags();
    }
}
