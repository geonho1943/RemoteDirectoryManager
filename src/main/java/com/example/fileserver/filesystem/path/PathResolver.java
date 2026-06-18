package com.example.fileserver.filesystem.path;

import com.example.fileserver.common.error.ApiException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

import static com.example.fileserver.common.error.ErrorCode.FILE_OPERATION_FAILED;
import static com.example.fileserver.common.error.ErrorCode.INVALID_PATH;

public class PathResolver {

    private final Path rootPath;
    private final PathNormalizer pathNormalizer;

    // 문자열 루트 경로를 Path로 변환해 루트 해석기를 만든다.
    public PathResolver(String rootPath, PathNormalizer pathNormalizer) {
        this(toRootPath(rootPath), pathNormalizer);
    }

    // 루트 경로를 고정하고 심볼릭 링크 루트 사용을 차단한다.
    public PathResolver(Path rootPath, PathNormalizer pathNormalizer) {
        if (rootPath == null) {
            throw new ApiException(INVALID_PATH, "Root path must not be null.");
        }

        this.rootPath = rootPath.toAbsolutePath().normalize();
        this.pathNormalizer = Objects.requireNonNull(pathNormalizer, "PathNormalizer must not be null.");

        if (Files.exists(this.rootPath, LinkOption.NOFOLLOW_LINKS)
                && !Files.isDirectory(this.rootPath, LinkOption.NOFOLLOW_LINKS)) {
            throw new ApiException(INVALID_PATH, "Configured root path must be a directory: " + this.rootPath);
        }

        try {
            Files.createDirectories(this.rootPath);
        } catch (IOException exception) {
            throw new ApiException(FILE_OPERATION_FAILED, "Failed to create storage root: " + this.rootPath, exception);
        }
    }

    // 정규화된 상대 경로를 루트 하위 실제 경로로 해석한다.
    public Path resolveUnderRoot(String relativePath) {
        String normalizedRelativePath = pathNormalizer.normalizeRelativePath(relativePath);
        final Path resolvedPath;
        try {
            resolvedPath = resolveAgainstRoot(normalizedRelativePath).normalize();
        } catch (InvalidPathException exception) {
            throw new ApiException(INVALID_PATH, "Path contains invalid characters: " + relativePath, exception);
        }

        if (!resolvedPath.startsWith(rootPath)) {
            throw new ApiException(INVALID_PATH, "Resolved path escapes the configured root: " + relativePath);
        }

        validateNoSymbolicLinks(resolvedPath);
        return resolvedPath;
    }

    // 루트 경로와 정규화된 상대 경로를 단순 결합한다.
    private Path resolveAgainstRoot(String normalizedRelativePath) {
        if ("/".equals(normalizedRelativePath)) {
            return rootPath;
        }

        return rootPath.resolve(normalizedRelativePath.substring(1));
    }

    // 루트부터 대상 경로까지의 모든 경로 세그먼트에서 심볼릭 링크를 거부한다.
    private void validateNoSymbolicLinks(Path resolvedPath) {
        Path current = rootPath;
        Path relativePart = rootPath.relativize(resolvedPath);

        for (Path segment : relativePart) {
            current = current.resolve(segment);

            // Initial version keeps the policy simple and safe:
            // reject any symlink encountered instead of trying to allow limited cases.
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(current)) {
                throw new ApiException(FILE_OPERATION_FAILED, "Symbolic links are not allowed: " + current);
            }
        }
    }

    // 설정 루트 경로 문자열이 비어 있지 않은지 확인한다.
    private static String requireRootPath(String rootPath) {
        if (rootPath == null || rootPath.isBlank()) {
            throw new ApiException(INVALID_PATH, "Root path must not be null or blank.");
        }

        return rootPath.trim();
    }

    // 설정 루트 경로 문자열을 Path로 변환하고 잘못된 형식을 API 예외로 바꾼다.
    private static Path toRootPath(String rootPath) {
        try {
            return Paths.get(requireRootPath(rootPath));
        } catch (java.nio.file.InvalidPathException exception) {
            throw new ApiException(INVALID_PATH, "Configured root path is invalid: " + rootPath, exception);
        }
    }
}
