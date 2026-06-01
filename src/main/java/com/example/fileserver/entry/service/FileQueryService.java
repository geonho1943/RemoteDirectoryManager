package com.example.fileserver.entry.service;

import com.example.fileserver.common.error.ApiException;
import com.example.fileserver.common.time.FileTimeConverter;
import com.example.fileserver.entry.dto.DirectoryListResponse;
import com.example.fileserver.entry.dto.FileEntryResponse;
import com.example.fileserver.entry.dto.TagSummaryDto;
import com.example.fileserver.entry.entity.FileEntryEntity;
import com.example.fileserver.filesystem.path.PathNormalizer;
import com.example.fileserver.filesystem.path.PathResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static com.example.fileserver.common.error.ErrorCode.ENTRY_NOT_FOUND;
import static com.example.fileserver.common.error.ErrorCode.FILE_OPERATION_FAILED;
import static com.example.fileserver.common.error.ErrorCode.NOT_A_DIRECTORY;

@Service
public class FileQueryService {

    private static final String ENTRY_TYPE_DIRECTORY = "DIRECTORY";
    private static final String ENTRY_TYPE_FILE = "FILE";
    private static final Comparator<FileEntryResponse> DIRECTORY_FIRST = Comparator
            .comparingInt((FileEntryResponse entry) -> ENTRY_TYPE_DIRECTORY.equals(entry.entryType()) ? 0 : 1)
            .thenComparing(FileEntryResponse::name);
    private static final Logger log = LoggerFactory.getLogger(FileQueryService.class);

    private final PathNormalizer pathNormalizer;
    private final PathResolver pathResolver;
    private final FileMetadataService fileMetadataService;

    // 목록과 상세 조회에 필요한 경로 도구와 메타데이터 서비스를 주입한다.
    public FileQueryService(
            PathNormalizer pathNormalizer,
            PathResolver pathResolver,
            FileMetadataService fileMetadataService
    ) {
        this.pathNormalizer = pathNormalizer;
        this.pathResolver = pathResolver;
        this.fileMetadataService = fileMetadataService;
    }

    // 지정 디렉터리의 자식 엔트리를 파일시스템 기준으로 조회한다.
    public DirectoryListResponse listEntries(String path, boolean includeHidden) {
        String normalizedPath = pathNormalizer.normalizeRelativePath(path);
        Path directoryPath = pathResolver.resolveUnderRoot(normalizedPath);

        if (!Files.exists(directoryPath, LinkOption.NOFOLLOW_LINKS)) {
            throw new ApiException(ENTRY_NOT_FOUND, "Entry not found: " + normalizedPath);
        }

        if (!Files.isDirectory(directoryPath, LinkOption.NOFOLLOW_LINKS)) {
            throw new ApiException(NOT_A_DIRECTORY, "Path is not a directory: " + normalizedPath);
        }

        try (Stream<Path> children = Files.list(directoryPath)) {
            List<Path> childPaths = children.toList();
            Map<String, FileEntryEntity> metadataByPath = loadFileMetadata(normalizedPath, childPaths);

            List<FileEntryResponse> entries = childPaths.stream()
                    .map(child -> toSafeFileEntryResponse(child, normalizedPath, metadataByPath))
                    .flatMap(Optional::stream)
                    .filter(entry -> includeHidden || !entry.hidden())
                    .sorted(DIRECTORY_FIRST)
                    .toList();

            return new DirectoryListResponse(normalizedPath, entries);
        } catch (IOException exception) {
            throw new ApiException(FILE_OPERATION_FAILED, "Failed to list directory: " + normalizedPath, exception);
        }
    }

    // 지정 경로의 단일 엔트리 상세 정보를 조회하고 파일이면 메타데이터를 동기화한다.
    public FileEntryResponse getEntryDetail(String path) {
        String normalizedPath = pathNormalizer.normalizeRelativePath(path);
        Path entryPath = pathResolver.resolveUnderRoot(normalizedPath);

        if (!Files.exists(entryPath, LinkOption.NOFOLLOW_LINKS)) {
            throw new ApiException(ENTRY_NOT_FOUND, "Entry not found: " + normalizedPath);
        }

        return toDetailResponse(entryPath, normalizedPath);
    }

    // 목록에 포함된 일반 파일들의 활성 메타데이터를 한 번에 조회한다.
    private Map<String, FileEntryEntity> loadFileMetadata(String parentPath, Collection<Path> childPaths) {
        List<String> filePaths = childPaths.stream()
                .filter(child -> Files.isRegularFile(child, LinkOption.NOFOLLOW_LINKS))
                .map(child -> pathNormalizer.join(parentPath, fileName(child)))
                .toList();

        return fileMetadataService.findActiveFilesByPath(filePaths);
    }

    // 목록 조회용 파일/디렉터리 응답 객체를 만든다.
    private FileEntryResponse toListEntryResponse(
            Path entryPath,
            String parentPath,
            Map<String, FileEntryEntity> metadataByPath
    ) {
        String name = fileName(entryPath);
        String relativePath = pathNormalizer.join(parentPath, name);
        BasicFileAttributes attributes = readAttributes(entryPath, relativePath);
        String entryType = resolveEntryType(attributes, relativePath);
        FileEntryEntity fileMetadata = ENTRY_TYPE_FILE.equals(entryType) ? metadataByPath.get(relativePath) : null;

        return toFileEntryResponse(entryPath, relativePath, parentPath, name, attributes, entryType, null, fileMetadata);
    }

    // 목록 중 읽을 수 없거나 지원하지 않는 엔트리를 건너뛸 수 있게 감싼다.
    private Optional<FileEntryResponse> toSafeFileEntryResponse(
            Path entryPath,
            String parentPath,
            Map<String, FileEntryEntity> metadataByPath
    ) {
        try {
            return Optional.of(toListEntryResponse(entryPath, parentPath, metadataByPath));
        } catch (ApiException exception) {
            log.warn("Skipping unsupported directory entry while listing {}: {}", entryPath, exception.getMessage());
            return Optional.empty();
        }
    }

    // 상세 조회용 응답을 만들고 파일이면 최신 메타데이터를 반영한다.
    private FileEntryResponse toDetailResponse(Path entryPath, String relativePath) {
        BasicFileAttributes attributes = readAttributes(entryPath, relativePath);
        String entryType = resolveEntryType(attributes, relativePath);
        String name = "/".equals(relativePath) ? "/" : pathNormalizer.extractFileName(relativePath);
        FileEntryEntity fileMetadata = ENTRY_TYPE_FILE.equals(entryType)
                ? fileMetadataService.syncFileRecord(relativePath)
                : null;

        return toFileEntryResponse(
                entryPath,
                relativePath,
                pathNormalizer.extractParentPath(relativePath),
                name,
                attributes,
                entryType,
                FileTimeConverter.toLocalDateTime(attributes.creationTime()),
                fileMetadata
        );
    }

    // 심볼릭 링크를 거부하고 파일시스템 속성을 읽는다.
    private BasicFileAttributes readAttributes(Path entryPath, String relativePath) {
        if (Files.isSymbolicLink(entryPath)) {
            throw new ApiException(FILE_OPERATION_FAILED, "Symbolic links are not allowed: " + relativePath);
        }

        try {
            return Files.readAttributes(entryPath, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        } catch (IOException exception) {
            throw new ApiException(FILE_OPERATION_FAILED, "Failed to read file attributes: " + relativePath, exception);
        }
    }

    // 파일시스템 속성으로 엔트리가 파일인지 디렉터리인지 판별한다.
    private String resolveEntryType(BasicFileAttributes attributes, String relativePath) {
        if (attributes.isDirectory()) {
            return ENTRY_TYPE_DIRECTORY;
        }

        if (attributes.isRegularFile()) {
            return ENTRY_TYPE_FILE;
        }

        throw new ApiException(FILE_OPERATION_FAILED, "Unsupported filesystem entry type: " + relativePath);
    }

    // 공통 파일/디렉터리 응답 구조를 조립한다.
    private FileEntryResponse toFileEntryResponse(
            Path entryPath,
            String relativePath,
            String parentPath,
            String name,
            BasicFileAttributes attributes,
            String entryType,
            LocalDateTime createdAtFs,
            FileEntryEntity fileMetadata
    ) {
        boolean file = ENTRY_TYPE_FILE.equals(entryType);
        return new FileEntryResponse(
                entryType,
                relativePath,
                parentPath,
                name,
                file ? pathNormalizer.extractExtension(name) : null,
                file ? probeContentType(entryPath) : null,
                file ? attributes.size() : null,
                FileTimeConverter.toLocalDateTime(attributes.lastModifiedTime()),
                createdAtFs,
                isHidden(entryPath),
                fileMetadata != null ? fileMetadata.getFileId() : null,
                fileMetadata != null ? TagSummaryDto.sortedFrom(fileMetadata.getTags()) : List.of()
        );
    }

    // 시스템 MIME 탐지를 시도하고 실패하면 null로 둔다.
    private String probeContentType(Path entryPath) {
        try {
            return Files.probeContentType(entryPath);
        } catch (IOException exception) {
            return null;
        }
    }

    // 운영체제 숨김 속성과 점 파일 규칙을 함께 확인한다.
    private boolean isHidden(Path entryPath) {
        try {
            if (Files.isHidden(entryPath)) {
                return true;
            }
        } catch (IOException ignored) {
        }

        Path fileName = entryPath.getFileName();
        return fileName != null && fileName.toString().startsWith(".");
    }

    // Path에서 마지막 파일명 세그먼트를 추출한다.
    private String fileName(Path entryPath) {
        Path fileName = entryPath.getFileName();
        if (fileName == null) {
            throw new ApiException(FILE_OPERATION_FAILED, "Failed to determine file name: " + entryPath);
        }

        return fileName.toString();
    }
}
