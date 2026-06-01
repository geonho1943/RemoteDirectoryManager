package com.example.fileserver.entry.service;

import com.example.fileserver.common.error.ApiException;
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

import static com.example.fileserver.common.error.ErrorCode.ENTRY_NOT_FOUND;
import static com.example.fileserver.common.error.ErrorCode.FILE_OPERATION_FAILED;
import static com.example.fileserver.common.error.ErrorCode.NOT_A_FILE;

@Service
public class FileMetadataService {

    private final PathNormalizer pathNormalizer;
    private final PathResolver pathResolver;
    private final FileEntryRepository fileEntryRepository;

    // 메타데이터 동기화에 필요한 경로 도구와 저장소를 주입한다.
    public FileMetadataService(
            PathNormalizer pathNormalizer,
            PathResolver pathResolver,
            FileEntryRepository fileEntryRepository
    ) {
        this.pathNormalizer = pathNormalizer;
        this.pathResolver = pathResolver;
        this.fileEntryRepository = fileEntryRepository;
    }

    // 주어진 경로들의 활성 파일 메타데이터를 경로별 Map으로 반환한다.
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

    // 실제 파일 상태를 읽어 DB 파일 메타데이터를 생성하거나 갱신한다.
    @Transactional
    public FileEntryEntity syncFileRecord(String filePath) {
        String normalizedPath = pathNormalizer.normalizeRelativePath(filePath);
        Path realPath = pathResolver.resolveUnderRoot(normalizedPath);

        if (!Files.exists(realPath, LinkOption.NOFOLLOW_LINKS)) {
            throw new ApiException(ENTRY_NOT_FOUND, "Entry not found: " + normalizedPath);
        }

        if (!Files.isRegularFile(realPath, LinkOption.NOFOLLOW_LINKS)) {
            throw new ApiException(NOT_A_FILE, "Path is not a file: " + normalizedPath);
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

        FileEntryEntity savedEntity = fileEntryRepository.save(entity);
        savedEntity.getTags().size();

        return savedEntity;
    }

    // 지정 경로와 그 하위 파일 메타데이터를 비활성화한다.
    @Transactional
    public void deactivateByPathOrDescendant(String path) {
        String normalizedPath = pathNormalizer.normalizeRelativePath(path);
        fileEntryRepository.deactivateByFilePathOrDescendant(normalizedPath);
    }

    // 파일시스템에서 기본 파일 속성을 읽고 실패를 API 예외로 바꾼다.
    private BasicFileAttributes readAttributes(Path realPath, String normalizedPath) {
        try {
            return Files.readAttributes(realPath, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        } catch (IOException exception) {
            throw new ApiException(FILE_OPERATION_FAILED, "Failed to read file metadata: " + normalizedPath, exception);
        }
    }

    // 파일 엔티티에 경로, 이름, 확장자, 시간, 활성 상태를 반영한다.
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
