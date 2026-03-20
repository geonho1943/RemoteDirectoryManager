package com.example.fileserver.filesystem.path;

import com.example.fileserver.common.error.InvalidEntryNameException;
import com.example.fileserver.common.error.InvalidPathException;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class PathNormalizerImpl implements PathNormalizer {

    private static final Pattern WINDOWS_ABSOLUTE_PATH = Pattern.compile("^[A-Za-z]:(?:[\\\\/].*|$)");

    @Override
    public String normalizeRelativePath(String inputPath) {
        if (inputPath == null) {
            throw new InvalidPathException("Path must not be null.");
        }

        String trimmed = inputPath.trim();
        if (trimmed.isEmpty()) {
            throw new InvalidPathException("Path must not be blank.");
        }

        if (WINDOWS_ABSOLUTE_PATH.matcher(trimmed).matches() || trimmed.startsWith("\\\\")) {
            throw new InvalidPathException("Absolute filesystem paths are not allowed: " + inputPath);
        }

        String normalizedSeparators = trimmed.replace('\\', '/');
        String[] rawSegments = normalizedSeparators.split("/+");
        List<String> segments = new ArrayList<>();

        for (String rawSegment : rawSegments) {
            if (rawSegment.isEmpty() || ".".equals(rawSegment)) {
                continue;
            }

            if ("..".equals(rawSegment)) {
                throw new InvalidPathException("Parent path traversal is not allowed: " + inputPath);
            }

            segments.add(rawSegment);
        }

        if (segments.isEmpty()) {
            return "/";
        }

        return "/" + String.join("/", segments);
    }

    @Override
    public String normalizeChildName(String name) {
        if (name == null) {
            throw new InvalidEntryNameException("Entry name must not be null.");
        }

        String trimmed = name.trim();
        if (trimmed.isEmpty()) {
            throw new InvalidEntryNameException("Entry name must not be blank.");
        }

        if (".".equals(trimmed) || "..".equals(trimmed)) {
            throw new InvalidEntryNameException("'.' and '..' are not valid entry names.");
        }

        if (trimmed.contains("/") || trimmed.contains("\\")) {
            throw new InvalidEntryNameException("Entry name must not contain path separators.");
        }

        return trimmed;
    }

    @Override
    public String join(String parentPath, String name) {
        String normalizedParentPath = normalizeRelativePath(parentPath);
        String normalizedChildName = normalizeChildName(name);

        if ("/".equals(normalizedParentPath)) {
            return "/" + normalizedChildName;
        }

        return normalizedParentPath + "/" + normalizedChildName;
    }

    @Override
    public String extractParentPath(String relativePath) {
        String normalizedPath = normalizeRelativePath(relativePath);
        if ("/".equals(normalizedPath)) {
            return "/";
        }

        int lastSlashIndex = normalizedPath.lastIndexOf('/');
        if (lastSlashIndex <= 0) {
            return "/";
        }

        return normalizedPath.substring(0, lastSlashIndex);
    }

    @Override
    public String extractFileName(String relativePath) {
        String normalizedPath = normalizeRelativePath(relativePath);
        if ("/".equals(normalizedPath)) {
            return "/";
        }

        int lastSlashIndex = normalizedPath.lastIndexOf('/');
        return normalizedPath.substring(lastSlashIndex + 1);
    }

    @Override
    public String extractExtension(String name) {
        String normalizedName = normalizeChildName(name);
        int lastDotIndex = normalizedName.lastIndexOf('.');

        if (lastDotIndex <= 0 || lastDotIndex == normalizedName.length() - 1) {
            return null;
        }

        return normalizedName.substring(lastDotIndex + 1);
    }
}
