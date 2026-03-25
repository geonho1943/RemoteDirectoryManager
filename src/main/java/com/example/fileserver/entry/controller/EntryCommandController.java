package com.example.fileserver.entry.controller;

import com.example.fileserver.entry.dto.DeleteEntryRequest;
import com.example.fileserver.entry.service.FileCommandService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/entries")
public class EntryCommandController {

    private final FileCommandService fileCommandService;

    public EntryCommandController(FileCommandService fileCommandService) {
        this.fileCommandService = fileCommandService;
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteEntry(@RequestBody DeleteEntryRequest request) {
        fileCommandService.deleteEntry(request);
        return ResponseEntity.noContent().build();
    }
}
