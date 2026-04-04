package com.example.fileserver.entry.controller;

import com.example.fileserver.entry.dto.DirectoryListResponse;
import com.example.fileserver.entry.dto.FileEntryDetailResponse;
import com.example.fileserver.entry.service.FileQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/entries")
public class EntryQueryController {

    private final FileQueryService fileQueryService;

    public EntryQueryController(FileQueryService fileQueryService) {
        this.fileQueryService = fileQueryService;
    }

    @GetMapping
    public DirectoryListResponse listEntries(
            @RequestParam("path") String path,
            @RequestParam(name = "includeHidden", defaultValue = "true") boolean includeHidden
    ) {
        return fileQueryService.listEntries(path, includeHidden);
    }

    @GetMapping("/detail")
    public FileEntryDetailResponse getEntryDetail(@RequestParam("path") String path) {
        return fileQueryService.getEntryDetail(path);
    }
}
