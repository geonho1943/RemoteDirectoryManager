package com.example.fileserver.transfer.controller;

import com.example.fileserver.transfer.service.FileTransferService;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/files")
public class FileTransferController {

    private final FileTransferService fileTransferService;

    public FileTransferController(FileTransferService fileTransferService) {
        this.fileTransferService = fileTransferService;
    }

    @GetMapping("/download")
    public ResponseEntity<Resource> downloadFile(@RequestParam("path") String path) {
        return fileTransferService.downloadFile(path);
    }

    @GetMapping("/stream")
    public ResponseEntity<Resource> streamFile(
            @RequestParam("path") String path,
            @RequestHeader(value = "Range", required = false) String rangeHeader
    ) {
        return fileTransferService.streamFile(path, rangeHeader);
    }
}
