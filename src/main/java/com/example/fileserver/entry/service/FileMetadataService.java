package com.example.fileserver.entry.service;

import com.example.fileserver.entry.entity.FileEntryEntity;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

public interface FileMetadataService {

    Map<String, FileEntryEntity> findActiveFilesByPath(Collection<String> filePaths);

    Optional<FileEntryEntity> findActiveFileByPath(String filePath);

    FileEntryEntity syncFileRecord(String filePath);

    void deactivateByPathOrDescendant(String path);
}
