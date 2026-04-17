package com.example.fileserver.entry.service;

import com.example.fileserver.common.error.EntryNotFoundException;
import com.example.fileserver.common.error.FileOperationException;
import com.example.fileserver.common.error.NotAFileException;
import com.example.fileserver.common.time.FileTimeConverter;
import com.example.fileserver.entry.entity.FileEntryEntity;
import com.example.fileserver.entry.repository.FileEntryRepository;
import com.example.fileserver.filesystem.path.PathNormalizer;
import com.example.fileserver.filesystem.path.PathResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class FileMetadataServiceImpl implements FileMetadataService {

    private final PathNormalizer pathNormalizer;
    private final PathResolver pathResolver;
    private final FileEntryRepository fileEntryRepository;

    public FileMetadataServiceImpl(
            PathNormalizer pathNormalizer,
            PathResolver pathResolver,
            FileEntryRepository fileEntryRepository
    ) {
        this.pathNormalizer = pathNormalizer;
        this.pathResolver = pathResolver;
        this.fileEntryRepository = fileEntryRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, FileEntryEntity> findActiveFilesByPath(Collection<String> filePaths) {
        if (filePaths == null || filePaths.isEmpty()) {
            return Map.of();
        }

        List<FileEntryEntity> files = fileEntryRepository.findByFilePathInAndActiveTrue(filePaths);
        Map<String, FileEntryEntity> filesByPath = new LinkedHashMap<>();
        for (FileEntryEntity file : files) {
            filesByPath.put(file.getFilePath(), file);
        }
        return filesByPath;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<FileEntryEntity> findActiveFileByPath(String filePath) {
        return fileEntryRepository.findByFilePathAndActiveTrue(pathNormalizer.normalizeRelativePath(filePath));
    }

    @Override
    @Transactional
    public FileEntryEntity syncFileRecord(String filePath) {
        String normalizedPath = pathNormalizer.normalizeRelativePath(filePath);
        Path realPath = pathResolver.resolveUnderRoot(normalizedPath);

        if (!Files.exists(realPath, LinkOption.NOFOLLOW_LINKS)) {
            throw new EntryNotFoundException("Entry not found: " + normalizedPath);
        }

        if (!Files.isRegularFile(realPath, LinkOption.NOFOLLOW_LINKS)) {
            throw new NotAFileException("Path is not a file: " + normalizedPath);
        }

        String fileName = pathNormalizer.extractFileName(normalizedPath);
        BasicFileAttributes attributes = readAttributes(realPath, normalizedPath);

        FileEntryEntity entity = fileEntryRepository.findByFilePath(normalizedPath)
                .orElseGet(FileEntryEntity::new);

        if (!entity.isActive()) {
            // A deleted file may later be recreated at the same path.
            // In that case we reactivate the record but start with fresh tag/thumbnail associations.
            entity.getTags().clear();
            entity.getThumbnails().clear();
        }

        updateFileRecord(entity, normalizedPath, fileName, attributes);

        fileEntryRepository.save(entity);

        return fileEntryRepository.findByFilePathAndActiveTrue(normalizedPath)
                .orElse(entity);
    }

    @Override
    @Transactional
    public void deactivateByPathOrDescendant(String path) {
        String normalizedPath = pathNormalizer.normalizeRelativePath(path);
        fileEntryRepository.deactivateByFilePathOrDescendant(normalizedPath);
    }

    private BasicFileAttributes readAttributes(Path realPath, String normalizedPath) {
        try {
            return Files.readAttributes(realPath, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        } catch (IOException exception) {
            throw new FileOperationException("Failed to read file metadata: " + normalizedPath, exception);
        }
    }

    private void updateFileRecord(
            FileEntryEntity entity,
            String normalizedPath,
            String fileName,
            BasicFileAttributes attributes
    ) {
        entity.setFilePath(normalizedPath);
        entity.setFileName(fileName);
        entity.setFileExtension(pathNormalizer.extractExtension(fileName));
        entity.setCreatedAt(FileTimeConverter.toLocalDateTime(attributes.creationTime()));
        entity.setModifiedAt(FileTimeConverter.toLocalDateTime(attributes.lastModifiedTime()));
        entity.setActive(true);
    }
}
