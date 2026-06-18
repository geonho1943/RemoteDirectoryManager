package com.example.fileserver.entry.service;

import com.example.fileserver.common.error.ApiException;
import com.example.fileserver.entry.ConflictPolicy;
import com.example.fileserver.entry.dto.PathResponse;
import com.example.fileserver.filesystem.path.PathNormalizer;
import com.example.fileserver.filesystem.path.PathResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
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

import static com.example.fileserver.common.error.ErrorCode.ENTRY_ALREADY_EXISTS;
import static com.example.fileserver.common.error.ErrorCode.ENTRY_NOT_FOUND;
import static com.example.fileserver.common.error.ErrorCode.FILE_OPERATION_FAILED;
import static com.example.fileserver.common.error.ErrorCode.INVALID_ENTRY_NAME;
import static com.example.fileserver.common.error.ErrorCode.INVALID_PATH;
import static com.example.fileserver.common.error.ErrorCode.METADATA_SYNC_FAILED;
import static com.example.fileserver.common.error.ErrorCode.NOT_A_DIRECTORY;
import static com.example.fileserver.common.error.ErrorCode.TRANSACTION_SYNCHRONIZATION_UNAVAILABLE;

@Service
public class FileCommandService {

    private static final Logger log = LoggerFactory.getLogger(FileCommandService.class);
    private static final Runnable NO_AFTER_COMMIT = () -> { };

    private final PathNormalizer pathNormalizer;
    private final PathResolver pathResolver;
    private final FileMetadataService fileMetadataService;
    private final TransactionTemplate transactionTemplate;

    // 파일 명령 처리에 필요한 경로, 메타데이터, 트랜잭션 도구를 주입한다.
    public FileCommandService(
            PathNormalizer pathNormalizer,
            PathResolver pathResolver,
            FileMetadataService fileMetadataService,
            TransactionTemplate transactionTemplate
    ) {
        this.pathNormalizer = pathNormalizer;
        this.pathResolver = pathResolver;
        this.fileMetadataService = fileMetadataService;
        this.transactionTemplate = transactionTemplate;
    }

    // 지정한 부모 경로 아래에 새 디렉터리를 생성한다.
    @Transactional
    public PathResponse createDirectory(String parentPath, String directoryName) {
        parentPath = pathNormalizer.normalizeRelativePath(parentPath);
        String name = pathNormalizer.normalizeChildName(directoryName);
        String targetRelativePath = pathNormalizer.join(parentPath, name);

        Path parentRealPath = pathResolver.resolveUnderRoot(parentPath);
        Path targetRealPath = pathResolver.resolveUnderRoot(targetRelativePath);

        validateParent(parentPath, parentRealPath);
        if (Files.exists(targetRealPath, LinkOption.NOFOLLOW_LINKS)) {
            throw new ApiException(ENTRY_ALREADY_EXISTS, "Entry already exists: " + targetRelativePath);
        }
        createDirectoryOnFilesystem(targetRelativePath, targetRealPath);

        return new PathResponse(targetRelativePath);
    }

    // 업로드 파일을 충돌 정책에 맞게 저장하고 메타데이터를 동기화한다.
    @Transactional
    public PathResponse uploadFile(String parentPath, ConflictPolicy conflictPolicy, MultipartFile file) {
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
            throw new ApiException(METADATA_SYNC_FAILED,
                    "Failed to synchronize file metadata after upload: " + resolvedTarget.relativePath(),
                    exception
            );
        }

        return new PathResponse(resolvedTarget.relativePath());
    }

    // 삭제 대상을 staging으로 옮기고 메타데이터 커밋 후 실제 파일을 제거한다.
    public void deleteEntry(String path) {
        String relativePath = pathNormalizer.normalizeRelativePath(path);
        validateDeleteTarget(relativePath);

        Path deleteStagingPath = transactionTemplate.execute(status -> {
            Path targetRealPath = pathResolver.resolveUnderRoot(relativePath);
            if (!Files.exists(targetRealPath, LinkOption.NOFOLLOW_LINKS)) {
                throw new ApiException(ENTRY_NOT_FOUND, "Entry not found: " + relativePath);
            }

            Path movedStagingPath = moveEntryToDeleteStaging(relativePath, targetRealPath);

            try {
                fileMetadataService.deactivateByPathOrDescendant(relativePath);
            } catch (RuntimeException exception) {
                throw new ApiException(METADATA_SYNC_FAILED,
                        "Failed to synchronize file metadata after delete: " + relativePath,
                        exception
                );
            }

            return movedStagingPath;
        });

        deleteStagedEntryAfterMetadataCommit(relativePath, deleteStagingPath);
    }

    // 부모 경로가 존재하는 디렉터리인지 확인한다.
    private void validateParent(String parentPath, Path parentRealPath) {
        if (!Files.exists(parentRealPath, LinkOption.NOFOLLOW_LINKS)) {
            throw new ApiException(ENTRY_NOT_FOUND, "Parent path not found: " + parentPath);
        }

        if (!Files.isDirectory(parentRealPath, LinkOption.NOFOLLOW_LINKS)) {
            throw new ApiException(NOT_A_DIRECTORY, "Parent path is not a directory: " + parentPath);
        }
    }

    // 업로드 요청에 실제 파일 파트가 포함되어 있는지 검증한다.
    private void validateMultipartFile(MultipartFile file) {
        if (file == null) {
            throw new ApiException(INVALID_ENTRY_NAME, "File part is required.");
        }

        if (file.isEmpty()) {
            throw new ApiException(INVALID_ENTRY_NAME, "Uploaded file must not be empty.");
        }
    }

    // 업로드 충돌 정책이 명시되었는지 확인한다.
    private ConflictPolicy requireConflictPolicy(ConflictPolicy conflictPolicy) {
        if (conflictPolicy == null) {
            throw new ApiException(INVALID_ENTRY_NAME, "Conflict policy is required.");
        }

        return conflictPolicy;
    }

    // 루트 경로처럼 삭제할 수 없는 대상을 차단한다.
    private void validateDeleteTarget(String relativePath) {
        if ("/".equals(relativePath)) {
            throw new ApiException(INVALID_PATH, "Root path cannot be deleted.");
        }
    }

    // 충돌 정책에 따라 실제 저장 대상 경로를 결정한다.
    private ResolvedUploadTarget resolveUploadTarget(
            String parentPath,
            String originalFilename,
            ConflictPolicy conflictPolicy
    ) {
        String initialRelativePath = pathNormalizer.join(parentPath, originalFilename);
        Path initialRealPath = pathResolver.resolveUnderRoot(initialRelativePath);

        return switch (conflictPolicy) {
            case FAIL -> resolveNamedTarget(initialRelativePath, initialRealPath, false);
            case OVERWRITE -> resolveNamedTarget(initialRelativePath, initialRealPath, true);
            case AUTO_RENAME -> resolveAutoRenameTarget(parentPath, originalFilename);
        };
    }

    // 원래 이름을 그대로 쓰는 업로드 대상의 충돌 가능성을 처리한다.
    private ResolvedUploadTarget resolveNamedTarget(
            String relativePath,
            Path realPath,
            boolean allowOverwrite
    ) {
        if (!Files.exists(realPath, LinkOption.NOFOLLOW_LINKS)) {
            return new ResolvedUploadTarget(relativePath, realPath, false);
        }

        if (!allowOverwrite) {
            throw new ApiException(ENTRY_ALREADY_EXISTS, "Entry already exists: " + relativePath);
        }

        if (Files.isDirectory(realPath, LinkOption.NOFOLLOW_LINKS)) {
            throw new ApiException(ENTRY_ALREADY_EXISTS, "Directory already exists: " + relativePath);
        }

        if (!Files.isRegularFile(realPath, LinkOption.NOFOLLOW_LINKS)) {
            throw new ApiException(FILE_OPERATION_FAILED, "Unsupported filesystem entry type: " + relativePath);
        }

        return new ResolvedUploadTarget(relativePath, realPath, true);
    }

    // 사용 가능한 이름이 나올 때까지 자동 번호를 붙인 업로드 대상을 찾는다.
    private ResolvedUploadTarget resolveAutoRenameTarget(String parentPath, String originalFilename) {
        String candidateName = originalFilename;
        int sequence = 1;

        while (true) {
            String candidateRelativePath = pathNormalizer.join(parentPath, candidateName);
            Path candidateRealPath = pathResolver.resolveUnderRoot(candidateRelativePath);

            if (!Files.exists(candidateRealPath, LinkOption.NOFOLLOW_LINKS)) {
                return new ResolvedUploadTarget(candidateRelativePath, candidateRealPath, false);
            }

            candidateName = appendAutoRenameSuffix(originalFilename, sequence++);
        }
    }

    // 파일명과 확장자 사이에 자동 이름 변경 번호를 붙인다.
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

    // 업로드 파일을 임시 위치와 백업 위치를 거쳐 트랜잭션 롤백 가능 상태로 저장한다.
    private void stageUploadForTransaction(MultipartFile file, ResolvedUploadTarget resolvedTarget) {
        ensureTransactionSynchronizationAvailable();

        Path tempUploadPath = null;
        Path backupPath = null;
        boolean uploadMovedToTarget = false;

        try (InputStream inputStream = file.getInputStream()) {
            Path targetPath = resolvedTarget.realPath();
            tempUploadPath = Files.createTempFile(targetPath.getParent(), ".rdm-upload-", ".tmp");
            Files.copy(inputStream, tempUploadPath, StandardCopyOption.REPLACE_EXISTING);

            if (resolvedTarget.replacesExistingFile()) {
                backupPath = createSiblingStagingPath(targetPath, "backup");
                Files.move(targetPath, backupPath);
            }

            Files.move(tempUploadPath, targetPath);
            uploadMovedToTarget = true;
            Path stagedBackupPath = backupPath;
            registerTransactionSynchronization(
                    "upload",
                    () -> deleteQuietly(stagedBackupPath, "upload backup"),
                    () -> rollbackStagedUpload(targetPath, stagedBackupPath, true)
            );
        } catch (FileAlreadyExistsException exception) {
            rollbackStagedUpload(resolvedTarget.realPath(), backupPath, uploadMovedToTarget);
            throw new ApiException(ENTRY_ALREADY_EXISTS, "Entry already exists: " + resolvedTarget.relativePath(), exception);
        } catch (IOException exception) {
            rollbackStagedUpload(resolvedTarget.realPath(), backupPath, uploadMovedToTarget);
            throw new ApiException(FILE_OPERATION_FAILED, "Failed to store file: " + resolvedTarget.relativePath(), exception);
        } catch (RuntimeException exception) {
            rollbackStagedUpload(resolvedTarget.realPath(), backupPath, uploadMovedToTarget);
            throw exception;
        } finally {
            deleteQuietly(tempUploadPath, "temporary upload file");
        }
    }

    // 삭제 대상을 원래 경로에서 숨겨진 staging 경로로 이동한다.
    private Path moveEntryToDeleteStaging(String relativePath, Path targetRealPath) {
        ensureTransactionSynchronizationAvailable();

        Path deleteStagingPath = createSiblingStagingPath(targetRealPath, "delete");

        try {
            Files.move(targetRealPath, deleteStagingPath);
            registerTransactionSynchronization(
                    "delete",
                    NO_AFTER_COMMIT,
                    () -> rollbackStagedDeletion(targetRealPath, deleteStagingPath)
            );
            return deleteStagingPath;
        } catch (IOException exception) {
            rollbackStagedDeletion(targetRealPath, deleteStagingPath);
            throw new ApiException(FILE_OPERATION_FAILED, "Failed to delete entry: " + relativePath, exception);
        } catch (RuntimeException exception) {
            rollbackStagedDeletion(targetRealPath, deleteStagingPath);
            throw exception;
        }
    }

    // 트랜잭션 커밋 또는 롤백 이후 실행할 파일시스템 보정 작업을 등록한다.
    private void registerTransactionSynchronization(
            String operation,
            Runnable afterCommit,
            Runnable afterRollback
    ) {
        try {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                // DB 커밋 성공 후 후처리 작업을 실행한다.
                @Override
                public void afterCommit() {
                    afterCommit.run();
                }

                // DB 롤백 시 staging된 파일시스템 변경을 되돌린다.
                @Override
                public void afterCompletion(int status) {
                    if (status == STATUS_ROLLED_BACK) {
                        afterRollback.run();
                    }
                }
            });
        } catch (IllegalStateException exception) {
            throw new ApiException(TRANSACTION_SYNCHRONIZATION_UNAVAILABLE,
                    "Failed to register " + operation + " transaction synchronization.",
                    exception
            );
        }
    }

    // 업로드 실패나 DB 롤백 시 새 파일을 제거하고 기존 파일 백업을 복구한다.
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

                Files.move(backupPath, targetPath);
            }
        } catch (IOException exception) {
            log.error("Failed to roll back staged upload for {}", targetPath, exception);
        }
    }

    // 삭제 트랜잭션 롤백 시 staging 경로의 항목을 원래 위치로 복구한다.
    private void rollbackStagedDeletion(Path targetPath, Path stagedDeletionPath) {
        try {
            if (stagedDeletionPath != null && Files.exists(stagedDeletionPath, LinkOption.NOFOLLOW_LINKS)) {
                if (Files.exists(targetPath, LinkOption.NOFOLLOW_LINKS)) {
                    log.error("Cannot restore deleted entry because target path already exists: {}", targetPath);
                    return;
                }

                Files.move(stagedDeletionPath, targetPath);
            }
        } catch (IOException exception) {
            log.error("Failed to restore staged deletion for {}", targetPath, exception);
        }
    }

    // 원본과 같은 디렉터리에 충돌 없는 숨김 staging 경로를 만든다.
    private Path createSiblingStagingPath(Path targetPath, String purpose) {
        Path parentPath = targetPath.getParent();
        String fileName = targetPath.getFileName() != null ? targetPath.getFileName().toString() : "entry";

        for (int attempt = 0; attempt < 10; attempt++) {
            Path candidate = parentPath.resolve("." + fileName + ".rdm-" + purpose + "-" + UUID.randomUUID());
            if (!Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)) {
                return candidate;
            }
        }

        throw new ApiException(FILE_OPERATION_FAILED, "Failed to allocate staging path for: " + targetPath);
    }

    // 파일시스템 보정 작업을 등록할 수 있는 트랜잭션 동기화 상태인지 확인한다.
    private void ensureTransactionSynchronizationAvailable() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new ApiException(TRANSACTION_SYNCHRONIZATION_UNAVAILABLE,
                    "Transaction synchronization is required for coordinated filesystem changes."
            );
        }
    }

    // 실패해도 주 흐름을 중단하지 않아야 하는 임시 파일을 조용히 삭제한다.
    private void deleteQuietly(Path path, String description) {
        if (path == null) {
            return;
        }

        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            log.warn("Failed to delete {} at {}", description, path, exception);
        }
    }

    // 메타데이터 커밋이 끝난 뒤 staging에 남은 삭제 대상을 실제로 제거한다.
    private void deleteStagedEntryAfterMetadataCommit(String relativePath, Path deleteStagingPath) {
        if (deleteStagingPath == null) {
            throw new ApiException(FILE_OPERATION_FAILED, "Failed to determine staged delete path: " + relativePath);
        }

        try {
            deleteRecursively(deleteStagingPath);
        } catch (IOException exception) {
            throw new ApiException(FILE_OPERATION_FAILED,
                    "Entry was removed from its original path, but physical cleanup failed: " + relativePath,
                    exception
            );
        }
    }

    // 디렉터리 트리를 순회하며 심볼릭 링크를 거부하고 파일과 디렉터리를 삭제한다.
    private void deleteRecursively(Path rootPath) throws IOException {
        Files.walkFileTree(rootPath, new SimpleFileVisitor<>() {
            // 디렉터리 진입 전 심볼릭 링크 여부를 확인한다.
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attrs) throws IOException {
                if (attrs.isSymbolicLink()) {
                    throw new ApiException(FILE_OPERATION_FAILED, "Symbolic links are not allowed: " + directory);
                }

                return FileVisitResult.CONTINUE;
            }

            // 심볼릭 링크를 따라가지 않고 링크 자체를 일반 파일처럼 삭제한다.
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            // 자식 항목 삭제가 끝난 디렉터리를 제거한다.
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

    // 파일시스템에 실제 디렉터리를 생성하고 충돌과 I/O 실패를 API 예외로 바꾼다.
    private void createDirectoryOnFilesystem(String targetRelativePath, Path targetRealPath) {
        try {
            Files.createDirectory(targetRealPath);
        } catch (FileAlreadyExistsException exception) {
            throw new ApiException(ENTRY_ALREADY_EXISTS, "Entry already exists: " + targetRelativePath, exception);
        } catch (IOException exception) {
            throw new ApiException(FILE_OPERATION_FAILED, "Failed to create directory: " + targetRelativePath, exception);
        }
    }

    private record ResolvedUploadTarget(
            String relativePath,
            Path realPath,
            boolean replacesExistingFile
    ) {
    }
}
