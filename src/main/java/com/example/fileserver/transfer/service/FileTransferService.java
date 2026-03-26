package com.example.fileserver.transfer.service;

import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;

public interface FileTransferService {

    ResponseEntity<Resource> downloadFile(String path);

    ResponseEntity<Resource> streamFile(String path, String rangeHeader);
}
