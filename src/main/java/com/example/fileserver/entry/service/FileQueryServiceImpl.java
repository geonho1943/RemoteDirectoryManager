package com.example.fileserver.entry.service;

import com.example.fileserver.common.error.EntryNotFoundException;
import com.example.fileserver.common.error.FileOperationException;
import com.example.fileserver.common.error.NotADirectoryException;
import com.example.fileserver.common.time.FileTimeConverter;
import com.example.fileserver.entry.dto.DirectoryListResponse;
import com.example.fileserver.entry.dto.FileEntryDetailResponse;
import com.example.fileserver.entry.dto.FileEntryDto;
import com.example.fileserver.entry.dto.TagSummaryDto;
import com.example.fileserver.entry.dto.TagSummaryMapper;
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
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

@Service
public class FileQueryServiceImpl implements FileQueryService {

    private static final String ENTRY_TYPE_DIRECTORY = "DIRECTORY";
    private static final String ENTRY_TYPE_FILE = "FILE";
    private static final Logger log = LoggerFactory.getLogger(FileQueryServiceImpl.class);

    private final PathNormalizer pathNormalizer;
    private final PathResolver pathResolver;
    private final FileMetadataService fileMetadataService;

    public FileQueryServiceImpl(
            PathNormalizer pathNormalizer,
            PathResolver pathResolver,
            FileMetadataService fileMetadataService
    ) {
        this.pathNormalizer = pathNormalizer;
        this.pathResolver = pathResolver;
        this.fileMetadataService = fileMetadataService;
    }

    @Override
    public DirectoryListResponse listEntries(String path, boolean includeHidden) {
        String normalizedPath = pathNormalizer.normalizeRelativePath(path);
        Path directoryPath = pathResolver.resolveUnderRoot(normalizedPath);

        if (!Files.exists(directoryPath, LinkOption.NOFOLLOW_LINKS)) {
            throw new EntryNotFoundException("Entry not found: " + normalizedPath);
        }

        if (!Files.isDirectory(directoryPath, LinkOption.NOFOLLOW_LINKS)) {
            throw new NotADirectoryException("Path is not a directory: " + normalizedPath);
        }

        try (Stream<Path> children = Files.list(directoryPath)) {
            List<Path> childPaths = children.toList();
            Map<String, FileEntryEntity> metadataByPath = loadFileMetadata(normalizedPath, childPaths);

            List<FileEntryDto> entries = childPaths.stream()
                    .map(child -> toSafeFileEntryDto(child, normalizedPath, metadataByPath))
                    .flatMap(Optional::stream)
                    .filter(entry -> includeHidden || !entry.hidden())
                    .sorted(directoryFirstComparator())
                    .toList();

            return new DirectoryListResponse(normalizedPath, entries);
        } catch (IOException exception) {
            throw new FileOperationException("Failed to list directory: " + normalizedPath, exception);
        }
    }

    @Override
    public FileEntryDetailResponse getEntryDetail(String path) {
        String normalizedPath = pathNormalizer.normalizeRelativePath(path);
        Path entryPath = pathResolver.resolveUnderRoot(normalizedPath);

        if (!Files.exists(entryPath, LinkOption.NOFOLLOW_LINKS)) {
            throw new EntryNotFoundException("Entry not found: " + normalizedPath);
        }

        return toFileEntryDetailResponse(entryPath, normalizedPath);
    }

    private Map<String, FileEntryEntity> loadFileMetadata(String parentPath, Collection<Path> childPaths) {
        List<String> filePaths = childPaths.stream()
                .filter(child -> Files.isRegularFile(child, LinkOption.NOFOLLOW_LINKS))
                .map(child -> pathNormalizer.join(parentPath, fileName(child)))
                .toList();

        return fileMetadataService.findActiveFilesByPath(filePaths);
    }

    private FileEntryDto toFileEntryDto(Path entryPath, String parentPath, Map<String, FileEntryEntity> metadataByPath) {
        String name = fileName(entryPath);
        String relativePath = pathNormalizer.join(parentPath, name);
        BasicFileAttributes attributes = readAttributes(entryPath, relativePath);
        String entryType = resolveEntryType(attributes, relativePath);
        boolean hidden = isHidden(entryPath);
        FileEntryEntity fileMetadata = ENTRY_TYPE_FILE.equals(entryType) ? metadataByPath.get(relativePath) : null;

        return new FileEntryDto(
                entryType,
                relativePath,
                parentPath,
                name,
                resolveExtension(entryType, name),
                resolveMimeType(entryType, entryPath),
                resolveSize(entryType, attributes),
                FileTimeConverter.toLocalDateTime(attributes.lastModifiedTime()),
                hidden,
                fileMetadata != null ? fileMetadata.getFileId() : null,
                resolveTags(fileMetadata)
        );
    }

    private Optional<FileEntryDto> toSafeFileEntryDto(
            Path entryPath,
            String parentPath,
            Map<String, FileEntryEntity> metadataByPath
    ) {
        try {
            return Optional.of(toFileEntryDto(entryPath, parentPath, metadataByPath));
        } catch (FileOperationException exception) {
            log.warn("Skipping unsupported directory entry while listing {}: {}", entryPath, exception.getMessage());
            return Optional.empty();
        }
    }

    private FileEntryDetailResponse toFileEntryDetailResponse(Path entryPath, String relativePath) {
        BasicFileAttributes attributes = readAttributes(entryPath, relativePath);
        String entryType = resolveEntryType(attributes, relativePath);
        String name = "/".equals(relativePath) ? "/" : pathNormalizer.extractFileName(relativePath);
        FileEntryEntity fileMetadata = ENTRY_TYPE_FILE.equals(entryType)
                ? fileMetadataService.syncFileRecord(relativePath)
                : null;

        return new FileEntryDetailResponse(
                entryType,
                relativePath,
                pathNormalizer.extractParentPath(relativePath),
                name,
                resolveExtension(entryType, name),
                resolveMimeType(entryType, entryPath),
                resolveSize(entryType, attributes),
                FileTimeConverter.toLocalDateTime(attributes.lastModifiedTime()),
                FileTimeConverter.toLocalDateTime(attributes.creationTime()),
                isHidden(entryPath),
                fileMetadata != null ? fileMetadata.getFileId() : null,
                resolveTags(fileMetadata)
        );
    }

    private List<TagSummaryDto> resolveTags(FileEntryEntity fileMetadata) {
        if (fileMetadata == null) {
            return List.of();
        }

        return TagSummaryMapper.toSortedTagSummaries(fileMetadata.getTags());
    }

    private BasicFileAttributes readAttributes(Path entryPath, String relativePath) {
        if (Files.isSymbolicLink(entryPath)) {
            throw new FileOperationException("Symbolic links are not allowed: " + relativePath);
        }

        try {
            return Files.readAttributes(entryPath, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        } catch (IOException exception) {
            throw new FileOperationException("Failed to read file attributes: " + relativePath, exception);
        }
    }

    private String resolveEntryType(BasicFileAttributes attributes, String relativePath) {
        if (attributes.isDirectory()) {
            return ENTRY_TYPE_DIRECTORY;
        }

        if (attributes.isRegularFile()) {
            return ENTRY_TYPE_FILE;
        }

        throw new FileOperationException("Unsupported filesystem entry type: " + relativePath);
    }

    private String resolveExtension(String entryType, String name) {
        if (!ENTRY_TYPE_FILE.equals(entryType)) {
            return null;
        }

        return pathNormalizer.extractExtension(name);
    }

    private String resolveMimeType(String entryType, Path entryPath) {
        if (!ENTRY_TYPE_FILE.equals(entryType)) {
            return null;
        }

        try {
            return Files.probeContentType(entryPath);
        } catch (IOException exception) {
            return null;
        }
    }

    private Long resolveSize(String entryType, BasicFileAttributes attributes) {
        if (!ENTRY_TYPE_FILE.equals(entryType)) {
            return null;
        }

        return attributes.size();
    }

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

    private String fileName(Path entryPath) {
        Path fileName = entryPath.getFileName();
        if (fileName == null) {
            throw new FileOperationException("Failed to determine file name: " + entryPath);
        }

        return fileName.toString();
    }

    private Comparator<FileEntryDto> directoryFirstComparator() {
        return Comparator
                .comparingInt((FileEntryDto entry) -> ENTRY_TYPE_DIRECTORY.equals(entry.entryType()) ? 0 : 1)
                .thenComparing(FileEntryDto::name);
    }
}
