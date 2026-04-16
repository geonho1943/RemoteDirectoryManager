package com.example.fileserver.entry.service;

import com.example.fileserver.common.error.EntryAlreadyExistsException;
import com.example.fileserver.common.error.EntryNotFoundException;
import com.example.fileserver.common.error.FileOperationException;
import com.example.fileserver.common.error.InvalidFileUploadException;
import com.example.fileserver.common.error.MetadataSynchronizationException;
import com.example.fileserver.common.error.InvalidPathException;
import com.example.fileserver.common.error.NotADirectoryException;
import com.example.fileserver.common.error.TransactionSynchronizationUnavailableException;
import com.example.fileserver.entry.ConflictPolicy;
import com.example.fileserver.entry.dto.CreateDirectoryRequest;
import com.example.fileserver.entry.dto.CreateDirectoryResponse;
import com.example.fileserver.entry.dto.DeleteEntryRequest;
import com.example.fileserver.entry.dto.UploadFileResponse;
import com.example.fileserver.filesystem.path.PathNormalizer;
import com.example.fileserver.filesystem.path.PathResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.UUID;

@Service
public class FileCommandServiceImpl implements FileCommandService {

    private static final Logger log = LoggerFactory.getLogger(FileCommandServiceImpl.class);

    private final PathNormalizer pathNormalizer;
    private final PathResolver pathResolver;
    private final FileMetadataService fileMetadataService;

    public FileCommandServiceImpl(
            PathNormalizer pathNormalizer,
            PathResolver pathResolver,
            FileMetadataService fileMetadataService
    ) {
        this.pathNormalizer = pathNormalizer;
        this.pathResolver = pathResolver;
        this.fileMetadataService = fileMetadataService;
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

        stageUploadForTransaction(file, resolvedTarget);

        try {
            fileMetadataService.syncFileRecord(resolvedTarget.relativePath());
        } catch (RuntimeException exception) {
            throw new MetadataSynchronizationException(
                    "Failed to synchronize file metadata after upload: " + resolvedTarget.relativePath(),
                    exception
            );
        }

        return new UploadFileResponse(resolvedTarget.relativePath());
    }

    @Override
    @Transactional
    public void deleteEntry(DeleteEntryRequest request) {
        String relativePath = pathNormalizer.normalizeRelativePath(request.path());
        validateDeleteTarget(relativePath);

        Path targetRealPath = pathResolver.resolveUnderRoot(relativePath);
        if (!Files.exists(targetRealPath, LinkOption.NOFOLLOW_LINKS)) {
            throw new EntryNotFoundException("Entry not found: " + relativePath);
        }

        stageDeleteForTransaction(relativePath, targetRealPath);

        try {
            fileMetadataService.deactivateByPathOrDescendant(relativePath);
        } catch (RuntimeException exception) {
            throw new MetadataSynchronizationException(
                    "Failed to synchronize file metadata after delete: " + relativePath,
                    exception
            );
        }
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

    private void validateDeleteTarget(String relativePath) {
        if ("/".equals(relativePath)) {
            throw new InvalidPathException("Root path cannot be deleted.");
        }
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

    private void stageUploadForTransaction(MultipartFile file, ResolvedUploadTarget resolvedTarget) {
        ensureTransactionSynchronizationAvailable();

        Path tempUploadPath = null;
        Path backupPath = null;
        boolean targetWritten = false;

        try (InputStream inputStream = file.getInputStream()) {
            Path targetPath = resolvedTarget.realPath();
            tempUploadPath = Files.createTempFile(targetPath.getParent(), ".rdm-upload-", ".tmp");
            Files.copy(inputStream, tempUploadPath, StandardCopyOption.REPLACE_EXISTING);

            if (resolvedTarget.overwrite()) {
                backupPath = createSiblingTransactionPath(targetPath, "backup");
                movePath(targetPath, backupPath);
            }

            movePath(tempUploadPath, targetPath);
            targetWritten = true;
            registerUploadSynchronization(targetPath, backupPath);
        } catch (FileAlreadyExistsException exception) {
            rollbackStagedUpload(resolvedTarget.realPath(), backupPath, targetWritten);
            throw new EntryAlreadyExistsException("Entry already exists: " + resolvedTarget.relativePath(), exception);
        } catch (IOException exception) {
            rollbackStagedUpload(resolvedTarget.realPath(), backupPath, targetWritten);
            throw new FileOperationException("Failed to store file: " + resolvedTarget.relativePath(), exception);
        } catch (RuntimeException exception) {
            rollbackStagedUpload(resolvedTarget.realPath(), backupPath, targetWritten);
            throw exception;
        } finally {
            deleteQuietly(tempUploadPath, "temporary upload file");
        }
    }

    private void stageDeleteForTransaction(String relativePath, Path targetRealPath) {
        ensureTransactionSynchronizationAvailable();

        Path stagedDeletionPath = createSiblingTransactionPath(targetRealPath, "delete");

        try {
            movePath(targetRealPath, stagedDeletionPath);
            registerDeleteSynchronization(targetRealPath, stagedDeletionPath);
        } catch (IOException exception) {
            rollbackStagedDeletion(targetRealPath, stagedDeletionPath);
            throw new FileOperationException("Failed to delete entry: " + relativePath, exception);
        } catch (RuntimeException exception) {
            rollbackStagedDeletion(targetRealPath, stagedDeletionPath);
            throw exception;
        }
    }

    private void registerUploadSynchronization(Path targetPath, Path backupPath) {
        try {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deleteQuietly(backupPath, "upload backup");
                }

                @Override
                public void afterCompletion(int status) {
                    if (status == STATUS_ROLLED_BACK) {
                        rollbackStagedUpload(targetPath, backupPath, true);
                    }
                }
            });
        } catch (IllegalStateException exception) {
            throw new TransactionSynchronizationUnavailableException(
                    "Failed to register upload transaction synchronization.",
                    exception
            );
        }
    }

    private void registerDeleteSynchronization(Path targetPath, Path stagedDeletionPath) {
        try {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deleteRecursivelyQuietly(stagedDeletionPath);
                }

                @Override
                public void afterCompletion(int status) {
                    if (status == STATUS_ROLLED_BACK) {
                        rollbackStagedDeletion(targetPath, stagedDeletionPath);
                    }
                }
            });
        } catch (IllegalStateException exception) {
            throw new TransactionSynchronizationUnavailableException(
                    "Failed to register delete transaction synchronization.",
                    exception
            );
        }
    }

    private void rollbackStagedUpload(Path targetPath, Path backupPath, boolean targetWritten) {
        try {
            if (targetWritten) {
                deleteQuietly(targetPath, "uploaded file");
            }

            if (backupPath != null && Files.exists(backupPath, LinkOption.NOFOLLOW_LINKS)) {
                if (Files.exists(targetPath, LinkOption.NOFOLLOW_LINKS)) {
                    log.error("Cannot restore overwritten file because target path already exists: {}", targetPath);
                    return;
                }

                movePath(backupPath, targetPath);
            }
        } catch (IOException exception) {
            log.error("Failed to roll back staged upload for {}", targetPath, exception);
        }
    }

    private void rollbackStagedDeletion(Path targetPath, Path stagedDeletionPath) {
        try {
            if (stagedDeletionPath != null && Files.exists(stagedDeletionPath, LinkOption.NOFOLLOW_LINKS)) {
                if (Files.exists(targetPath, LinkOption.NOFOLLOW_LINKS)) {
                    log.error("Cannot restore deleted entry because target path already exists: {}", targetPath);
                    return;
                }

                movePath(stagedDeletionPath, targetPath);
            }
        } catch (IOException exception) {
            log.error("Failed to restore staged deletion for {}", targetPath, exception);
        }
    }

    private void movePath(Path sourcePath, Path targetPath) throws IOException {
        Files.move(sourcePath, targetPath);
    }

    private Path createSiblingTransactionPath(Path targetPath, String purpose) {
        Path parentPath = targetPath.getParent();
        String fileName = targetPath.getFileName() != null ? targetPath.getFileName().toString() : "entry";

        for (int attempt = 0; attempt < 10; attempt++) {
            Path candidate = parentPath.resolve("." + fileName + ".rdm-" + purpose + "-" + UUID.randomUUID());
            if (!Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)) {
                return candidate;
            }
        }

        throw new FileOperationException("Failed to allocate transaction path for: " + targetPath);
    }

    private void ensureTransactionSynchronizationAvailable() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new TransactionSynchronizationUnavailableException(
                    "Transaction synchronization is required for coordinated filesystem changes."
            );
        }
    }

    private void deleteQuietly(Path path, String description) {
        if (path == null) {
            return;
        }

        try {
            if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
                Files.delete(path);
            }
        } catch (IOException exception) {
            log.warn("Failed to delete {} at {}", description, path, exception);
        }
    }

    private void deleteRecursivelyQuietly(Path rootPath) {
        if (rootPath == null || !Files.exists(rootPath, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }

        try {
            if (Files.isDirectory(rootPath, LinkOption.NOFOLLOW_LINKS)) {
                deleteRecursively(rootPath);
                return;
            }

            Files.delete(rootPath);
        } catch (IOException exception) {
            log.error("Failed to delete committed staged entry {}", rootPath, exception);
        }
    }

    private void deleteRecursively(Path rootPath) throws IOException {
        Files.walkFileTree(rootPath, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attrs) throws IOException {
                if (attrs.isSymbolicLink()) {
                    throw new FileOperationException("Symbolic links are not allowed: " + directory);
                }

                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (attrs.isSymbolicLink()) {
                    throw new FileOperationException("Symbolic links are not allowed: " + file);
                }

                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException exception) throws IOException {
                if (exception != null) {
                    throw exception;
                }

                Files.delete(directory);
                return FileVisitResult.CONTINUE;
            }
        });
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

    private record ResolvedUploadTarget(
            String relativePath,
            Path realPath,
            String fileName,
            boolean overwrite
    ) {
    }
}
