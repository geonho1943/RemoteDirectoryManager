package com.example.fileserver.entry.service;

import com.example.fileserver.common.error.EntryNotFoundException;
import com.example.fileserver.common.error.FileOperationException;
import com.example.fileserver.common.error.NotADirectoryException;
import com.example.fileserver.entry.dto.DirectoryListResponse;
import com.example.fileserver.entry.dto.FileEntryDetailResponse;
import com.example.fileserver.entry.dto.FileEntryDto;
import com.example.fileserver.filesystem.path.PathNormalizer;
import com.example.fileserver.filesystem.path.PathResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@Service
public class FileQueryServiceImpl implements FileQueryService {

    private static final String ENTRY_TYPE_DIRECTORY = "DIRECTORY";
    private static final String ENTRY_TYPE_FILE = "FILE";

    private final PathNormalizer pathNormalizer;
    private final PathResolver pathResolver;

    public FileQueryServiceImpl(PathNormalizer pathNormalizer, PathResolver pathResolver) {
        this.pathNormalizer = pathNormalizer;
        this.pathResolver = pathResolver;
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
            List<FileEntryDto> entries = children
                    .map(child -> toFileEntryDto(child, normalizedPath))
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

    private FileEntryDto toFileEntryDto(Path entryPath, String parentPath) {
        String name = fileName(entryPath);
        String relativePath = pathNormalizer.join(parentPath, name);
        BasicFileAttributes attributes = readAttributes(entryPath, relativePath);
        String entryType = resolveEntryType(attributes, relativePath);
        boolean hidden = isHidden(entryPath);

        return new FileEntryDto(
                entryType,
                relativePath,
                parentPath,
                name,
                resolveExtension(entryType, name),
                resolveMimeType(entryType, entryPath),
                resolveSize(entryType, attributes),
                toLocalDateTime(attributes.lastModifiedTime()),
                hidden
        );
    }

    private FileEntryDetailResponse toFileEntryDetailResponse(Path entryPath, String relativePath) {
        BasicFileAttributes attributes = readAttributes(entryPath, relativePath);
        String entryType = resolveEntryType(attributes, relativePath);
        String name = "/".equals(relativePath) ? "/" : pathNormalizer.extractFileName(relativePath);

        return new FileEntryDetailResponse(
                entryType,
                relativePath,
                pathNormalizer.extractParentPath(relativePath),
                name,
                resolveExtension(entryType, name),
                resolveMimeType(entryType, entryPath),
                resolveSize(entryType, attributes),
                toLocalDateTime(attributes.lastModifiedTime()),
                toLocalDateTime(attributes.creationTime()),
                isHidden(entryPath)
        );
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

    private LocalDateTime toLocalDateTime(FileTime fileTime) {
        if (fileTime == null) {
            return null;
        }

        return LocalDateTime.ofInstant(fileTime.toInstant(), ZoneId.systemDefault());
    }

}
