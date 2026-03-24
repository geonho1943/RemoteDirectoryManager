package com.example.fileserver.entry.service;

import com.example.fileserver.common.error.EntryAlreadyExistsException;
import com.example.fileserver.common.error.EntryNotFoundException;
import com.example.fileserver.common.error.FileOperationException;
import com.example.fileserver.common.error.InvalidFileUploadException;
import com.example.fileserver.common.error.NotADirectoryException;
import com.example.fileserver.entry.ConflictPolicy;
import com.example.fileserver.entry.dto.CreateDirectoryRequest;
import com.example.fileserver.entry.dto.CreateDirectoryResponse;
import com.example.fileserver.entry.dto.UploadFileResponse;
import com.example.fileserver.entry.entity.FileEntryEntity;
import com.example.fileserver.entry.entity.FileEntryType;
import com.example.fileserver.entry.repository.FileEntryRepository;
import com.example.fileserver.filesystem.path.PathNormalizer;
import com.example.fileserver.filesystem.path.PathNormalizerImpl;
import com.example.fileserver.filesystem.path.PathResolver;
import com.example.fileserver.filesystem.path.PathResolverImpl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
public class FileCommandServiceImpl implements FileCommandService {

    private static final String DEFAULT_FILESYSTEM_ID = "default";

    private final PathNormalizer pathNormalizer;
    private final PathResolver pathResolver;
    private final FileEntryRepository fileEntryRepository;

    public FileCommandServiceImpl(
            @Value("${app.filesystem.root-path}") String rootPath,
            FileEntryRepository fileEntryRepository
    ) {
        this(createPathNormalizer(), rootPath, fileEntryRepository);
    }

    FileCommandServiceImpl(
            PathNormalizer pathNormalizer,
            String rootPath,
            FileEntryRepository fileEntryRepository
    ) {
        this(pathNormalizer, new PathResolverImpl(rootPath, pathNormalizer), fileEntryRepository);
    }

    FileCommandServiceImpl(
            PathNormalizer pathNormalizer,
            PathResolver pathResolver,
            FileEntryRepository fileEntryRepository
    ) {
        this.pathNormalizer = pathNormalizer;
        this.pathResolver = pathResolver;
        this.fileEntryRepository = fileEntryRepository;
    }

    @Override
    @Transactional
    public CreateDirectoryResponse createDirectory(CreateDirectoryRequest request) {
        String parentPath = pathNormalizer.normalizeRelativePath(request.parentPath());
        String name = pathNormalizer.normalizeChildName(request.name());
        String targetRelativePath = pathNormalizer.join(parentPath, name);

        Path parentRealPath = pathResolver.resolveUnderRoot(parentPath);
        Path targetRealPath = pathResolver.resolveUnderRoot(targetRelativePath);

        validateParent(parentPath, parentRealPath);
        ensureTargetDoesNotExist(targetRelativePath, targetRealPath);
        createDirectoryOnFilesystem(targetRelativePath, targetRealPath);

        BasicFileAttributes attributes = readAttributes(targetRealPath, targetRelativePath);
        boolean hidden = detectHidden(targetRealPath, name);

        FileEntryEntity entity = fileEntryRepository.findByRelativePath(targetRelativePath)
                .orElseGet(FileEntryEntity::new);

        populateDirectoryEntity(entity, parentPath, name, targetRelativePath, attributes, hidden);

        try {
            fileEntryRepository.saveAndFlush(entity);
        } catch (DataAccessException exception) {
            throw new FileOperationException("Failed to persist directory metadata: " + targetRelativePath, exception);
        }

        return new CreateDirectoryResponse(targetRelativePath);
    }

    @Override
    @Transactional
    public UploadFileResponse uploadFile(String parentPath, ConflictPolicy conflictPolicy, MultipartFile file) {
        String normalizedParentPath = pathNormalizer.normalizeRelativePath(parentPath);
        validateMultipartFile(file);

        Path parentRealPath = pathResolver.resolveUnderRoot(normalizedParentPath);
        validateParent(normalizedParentPath, parentRealPath);

        String originalFilename = pathNormalizer.normalizeChildName(file.getOriginalFilename());
        ResolvedUploadTarget resolvedTarget = resolveUploadTarget(
                normalizedParentPath,
                originalFilename,
                requireConflictPolicy(conflictPolicy)
        );

        storeFile(file, resolvedTarget);

        BasicFileAttributes attributes = readAttributes(resolvedTarget.realPath(), resolvedTarget.relativePath());
        boolean hidden = detectHidden(resolvedTarget.realPath(), resolvedTarget.fileName());
        String mimeType = detectMimeType(resolvedTarget.realPath());

        FileEntryEntity entity = fileEntryRepository.findByRelativePath(resolvedTarget.relativePath())
                .orElseGet(FileEntryEntity::new);

        populateFileEntity(entity, normalizedParentPath, resolvedTarget, attributes, mimeType, hidden);

        try {
            fileEntryRepository.saveAndFlush(entity);
        } catch (DataAccessException exception) {
            throw new FileOperationException("Failed to persist file metadata: " + resolvedTarget.relativePath(), exception);
        }

        return new UploadFileResponse(resolvedTarget.relativePath());
    }

    private void validateParent(String parentPath, Path parentRealPath) {
        if (!Files.exists(parentRealPath, LinkOption.NOFOLLOW_LINKS)) {
            throw new EntryNotFoundException("Parent path not found: " + parentPath);
        }

        if (!Files.isDirectory(parentRealPath, LinkOption.NOFOLLOW_LINKS)) {
            throw new NotADirectoryException("Parent path is not a directory: " + parentPath);
        }
    }

    private void ensureTargetDoesNotExist(String targetRelativePath, Path targetRealPath) {
        if (Files.exists(targetRealPath, LinkOption.NOFOLLOW_LINKS)) {
            throw new EntryAlreadyExistsException("Entry already exists: " + targetRelativePath);
        }
    }

    private void validateMultipartFile(MultipartFile file) {
        if (file == null) {
            throw new InvalidFileUploadException("File part is required.");
        }

        if (file.isEmpty()) {
            throw new InvalidFileUploadException("Uploaded file must not be empty.");
        }
    }

    private ConflictPolicy requireConflictPolicy(ConflictPolicy conflictPolicy) {
        if (conflictPolicy == null) {
            throw new InvalidFileUploadException("Conflict policy is required.");
        }

        return conflictPolicy;
    }

    private ResolvedUploadTarget resolveUploadTarget(
            String parentPath,
            String originalFilename,
            ConflictPolicy conflictPolicy
    ) {
        String initialRelativePath = pathNormalizer.join(parentPath, originalFilename);
        Path initialRealPath = pathResolver.resolveUnderRoot(initialRelativePath);

        return switch (conflictPolicy) {
            case FAIL -> resolveFailTarget(originalFilename, initialRelativePath, initialRealPath);
            case OVERWRITE -> resolveOverwriteTarget(originalFilename, initialRelativePath, initialRealPath);
            case AUTO_RENAME -> resolveAutoRenameTarget(parentPath, originalFilename);
        };
    }

    private ResolvedUploadTarget resolveFailTarget(
            String fileName,
            String relativePath,
            Path realPath
    ) {
        if (Files.exists(realPath, LinkOption.NOFOLLOW_LINKS)) {
            throw new EntryAlreadyExistsException("Entry already exists: " + relativePath);
        }

        return new ResolvedUploadTarget(relativePath, realPath, fileName, false);
    }

    private ResolvedUploadTarget resolveOverwriteTarget(
            String fileName,
            String relativePath,
            Path realPath
    ) {
        if (Files.exists(realPath, LinkOption.NOFOLLOW_LINKS)) {
            if (Files.isDirectory(realPath, LinkOption.NOFOLLOW_LINKS)) {
                throw new EntryAlreadyExistsException("Directory already exists: " + relativePath);
            }

            if (!Files.isRegularFile(realPath, LinkOption.NOFOLLOW_LINKS)) {
                throw new FileOperationException("Unsupported filesystem entry type: " + relativePath);
            }

            return new ResolvedUploadTarget(relativePath, realPath, fileName, true);
        }

        return new ResolvedUploadTarget(relativePath, realPath, fileName, false);
    }

    private ResolvedUploadTarget resolveAutoRenameTarget(String parentPath, String originalFilename) {
        String candidateName = originalFilename;
        int sequence = 1;

        while (true) {
            String candidateRelativePath = pathNormalizer.join(parentPath, candidateName);
            Path candidateRealPath = pathResolver.resolveUnderRoot(candidateRelativePath);

            if (!Files.exists(candidateRealPath, LinkOption.NOFOLLOW_LINKS)) {
                return new ResolvedUploadTarget(candidateRelativePath, candidateRealPath, candidateName, false);
            }

            candidateName = appendAutoRenameSuffix(originalFilename, sequence++);
        }
    }

    private String appendAutoRenameSuffix(String originalFilename, int sequence) {
        String extension = pathNormalizer.extractExtension(originalFilename);
        String suffix = " (" + sequence + ")";

        if (extension == null) {
            return originalFilename + suffix;
        }

        int extensionIndex = originalFilename.length() - extension.length() - 1;
        String baseName = originalFilename.substring(0, extensionIndex);
        return baseName + suffix + "." + extension;
    }

    private void storeFile(MultipartFile file, ResolvedUploadTarget resolvedTarget) {
        try (InputStream inputStream = file.getInputStream()) {
            if (resolvedTarget.overwrite()) {
                Files.copy(inputStream, resolvedTarget.realPath(), StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.copy(inputStream, resolvedTarget.realPath());
            }
        } catch (FileAlreadyExistsException exception) {
            throw new EntryAlreadyExistsException("Entry already exists: " + resolvedTarget.relativePath(), exception);
        } catch (IOException exception) {
            throw new FileOperationException("Failed to store file: " + resolvedTarget.relativePath(), exception);
        }
    }

    private void createDirectoryOnFilesystem(String targetRelativePath, Path targetRealPath) {
        try {
            Files.createDirectory(targetRealPath);
        } catch (FileAlreadyExistsException exception) {
            throw new EntryAlreadyExistsException("Entry already exists: " + targetRelativePath, exception);
        } catch (IOException exception) {
            throw new FileOperationException("Failed to create directory: " + targetRelativePath, exception);
        }
    }

    private BasicFileAttributes readAttributes(Path targetRealPath, String targetRelativePath) {
        try {
            return Files.readAttributes(targetRealPath, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        } catch (IOException exception) {
            throw new FileOperationException("Failed to read directory attributes: " + targetRelativePath, exception);
        }
    }

    private boolean detectHidden(Path targetRealPath, String name) {
        try {
            if (Files.isHidden(targetRealPath)) {
                return true;
            }
        } catch (IOException ignored) {
        }

        return name.startsWith(".");
    }

    private String detectMimeType(Path targetRealPath) {
        try {
            return Files.probeContentType(targetRealPath);
        } catch (IOException exception) {
            return null;
        }
    }

    private void populateDirectoryEntity(
            FileEntryEntity entity,
            String parentPath,
            String name,
            String targetRelativePath,
            BasicFileAttributes attributes,
            boolean hidden
    ) {
        LocalDateTime now = LocalDateTime.now();

        entity.setFilesystemId(DEFAULT_FILESYSTEM_ID);
        entity.setEntryType(FileEntryType.DIRECTORY);
        entity.setRelativePath(targetRelativePath);
        entity.setParentPath(parentPath);
        entity.setName(name);
        entity.setExtension(null);
        entity.setMimeType(null);
        entity.setSizeBytes(null);
        entity.setModifiedAt(toLocalDateTime(attributes.lastModifiedTime()));
        entity.setCreatedAtFs(toLocalDateTime(attributes.creationTime()));
        entity.setHidden(hidden);
        entity.setChecksumSha256(null);
        entity.setLastScannedAt(now);
    }

    private void populateFileEntity(
            FileEntryEntity entity,
            String parentPath,
            ResolvedUploadTarget resolvedTarget,
            BasicFileAttributes attributes,
            String mimeType,
            boolean hidden
    ) {
        LocalDateTime now = LocalDateTime.now();

        entity.setFilesystemId(DEFAULT_FILESYSTEM_ID);
        entity.setEntryType(FileEntryType.FILE);
        entity.setRelativePath(resolvedTarget.relativePath());
        entity.setParentPath(parentPath);
        entity.setName(resolvedTarget.fileName());
        entity.setExtension(pathNormalizer.extractExtension(resolvedTarget.fileName()));
        entity.setMimeType(mimeType);
        entity.setSizeBytes(attributes.size());
        entity.setModifiedAt(toLocalDateTime(attributes.lastModifiedTime()));
        entity.setCreatedAtFs(toLocalDateTime(attributes.creationTime()));
        entity.setHidden(hidden);
        entity.setChecksumSha256(null);
        entity.setLastScannedAt(now);
    }

    private LocalDateTime toLocalDateTime(FileTime fileTime) {
        if (fileTime == null) {
            return null;
        }

        return LocalDateTime.ofInstant(fileTime.toInstant(), ZoneId.systemDefault());
    }

    private static PathNormalizer createPathNormalizer() {
        return new PathNormalizerImpl();
    }

    private record ResolvedUploadTarget(
            String relativePath,
            Path realPath,
            String fileName,
            boolean overwrite
    ) {
    }
}
