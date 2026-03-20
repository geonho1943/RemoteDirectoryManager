package com.example.fileserver.filesystem.path;

public interface PathNormalizer {

    String normalizeRelativePath(String inputPath);

    String normalizeChildName(String name);

    String join(String parentPath, String name);

    String extractParentPath(String relativePath);

    String extractFileName(String relativePath);

    String extractExtension(String name);
}
