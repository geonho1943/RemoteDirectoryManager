package com.example.fileserver.filesystem.path;

import com.example.fileserver.common.error.FileOperationException;
import com.example.fileserver.common.error.InvalidPathException;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

public class PathResolverImpl implements PathResolver {

    private final Path rootPath;
    private final PathNormalizer pathNormalizer;

    public PathResolverImpl(String rootPath, PathNormalizer pathNormalizer) {
        this(toRootPath(rootPath), pathNormalizer);
    }

    public PathResolverImpl(Path rootPath, PathNormalizer pathNormalizer) {
        if (rootPath == null) {
            throw new InvalidPathException("Root path must not be null.");
        }

        this.rootPath = rootPath.toAbsolutePath().normalize();
        this.pathNormalizer = Objects.requireNonNull(pathNormalizer, "PathNormalizer must not be null.");

        if (Files.exists(this.rootPath, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(this.rootPath)) {
            throw new InvalidPathException("Configured root path must not be a symbolic link: " + this.rootPath);
        }
    }

    @Override
    public Path resolveUnderRoot(String relativePath) {
        String normalizedRelativePath = pathNormalizer.normalizeRelativePath(relativePath);
        Path resolvedPath = resolveAgainstRoot(normalizedRelativePath).normalize();

        if (!resolvedPath.startsWith(rootPath)) {
            throw new InvalidPathException("Resolved path escapes the configured root: " + relativePath);
        }

        validateNoSymbolicLinks(resolvedPath);
        return resolvedPath;
    }

    @Override
    public boolean exists(String relativePath) {
        return Files.exists(resolveUnderRoot(relativePath), LinkOption.NOFOLLOW_LINKS);
    }

    @Override
    public boolean isDirectory(String relativePath) {
        return Files.isDirectory(resolveUnderRoot(relativePath), LinkOption.NOFOLLOW_LINKS);
    }

    @Override
    public boolean isRegularFile(String relativePath) {
        return Files.isRegularFile(resolveUnderRoot(relativePath), LinkOption.NOFOLLOW_LINKS);
    }

    private Path resolveAgainstRoot(String normalizedRelativePath) {
        if ("/".equals(normalizedRelativePath)) {
            return rootPath;
        }

        return rootPath.resolve(normalizedRelativePath.substring(1));
    }

    private void validateNoSymbolicLinks(Path resolvedPath) {
        Path current = rootPath;
        Path relativePart = rootPath.relativize(resolvedPath);

        for (Path segment : relativePart) {
            current = current.resolve(segment);

            // Initial version keeps the policy simple and safe:
            // reject any symlink encountered instead of trying to allow limited cases.
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(current)) {
                throw new FileOperationException("Symbolic links are not allowed: " + current);
            }
        }
    }

    private static String requireRootPath(String rootPath) {
        if (rootPath == null || rootPath.isBlank()) {
            throw new InvalidPathException("Root path must not be null or blank.");
        }

        return rootPath.trim();
    }

    private static Path toRootPath(String rootPath) {
        try {
            return Paths.get(requireRootPath(rootPath));
        } catch (java.nio.file.InvalidPathException exception) {
            throw new InvalidPathException("Configured root path is invalid: " + rootPath, exception);
        }
    }
}
