package com.example.fileserver.filesystem.path;

import com.example.fileserver.common.error.ApiException;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static com.example.fileserver.common.error.ErrorCode.INVALID_ENTRY_NAME;
import static com.example.fileserver.common.error.ErrorCode.INVALID_PATH;

public class PathNormalizer {

    private static final Pattern WINDOWS_ABSOLUTE_PATH = Pattern.compile("^[A-Za-z]:(?:[\\\\/].*|$)");

    // 외부 입력 경로를 루트 기준 상대 경로 형식으로 정규화한다.
    public String normalizeRelativePath(String inputPath) {
        if (inputPath == null) {
            throw new ApiException(INVALID_PATH, "Path must not be null.");
        }

        String trimmed = inputPath.trim();
        if (trimmed.isEmpty()) {
            throw new ApiException(INVALID_PATH, "Path must not be blank.");
        }

        if (WINDOWS_ABSOLUTE_PATH.matcher(trimmed).matches() || trimmed.startsWith("\\\\")) {
            throw new ApiException(INVALID_PATH, "Absolute filesystem paths are not allowed: " + inputPath);
        }

        String normalizedSeparators = trimmed.replace('\\', '/');
        String[] rawSegments = normalizedSeparators.split("/+");
        List<String> segments = new ArrayList<>();

        for (String rawSegment : rawSegments) {
            if (rawSegment.isEmpty() || ".".equals(rawSegment)) {
                continue;
            }

            if ("..".equals(rawSegment)) {
                throw new ApiException(INVALID_PATH, "Parent path traversal is not allowed: " + inputPath);
            }

            segments.add(rawSegment);
        }

        if (segments.isEmpty()) {
            return "/";
        }

        return "/" + String.join("/", segments);
    }

    // 단일 파일명 또는 디렉터리명을 검증하고 정규화한다.
    public String normalizeChildName(String name) {
        if (name == null) {
            throw new ApiException(INVALID_ENTRY_NAME, "Entry name must not be null.");
        }

        String trimmed = name.trim();
        if (trimmed.isEmpty()) {
            throw new ApiException(INVALID_ENTRY_NAME, "Entry name must not be blank.");
        }

        if (".".equals(trimmed) || "..".equals(trimmed)) {
            throw new ApiException(INVALID_ENTRY_NAME, "'.' and '..' are not valid entry names.");
        }

        if (trimmed.contains("/") || trimmed.contains("\\")) {
            throw new ApiException(INVALID_ENTRY_NAME, "Entry name must not contain path separators.");
        }

        return trimmed;
    }

    // 부모 경로와 자식 이름을 하나의 정규화된 상대 경로로 결합한다.
    public String join(String parentPath, String name) {
        String normalizedParentPath = normalizeRelativePath(parentPath);
        String normalizedChildName = normalizeChildName(name);

        if ("/".equals(normalizedParentPath)) {
            return "/" + normalizedChildName;
        }

        return normalizedParentPath + "/" + normalizedChildName;
    }

    // 정규화된 경로에서 부모 경로를 추출한다.
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

    // 정규화된 경로에서 마지막 파일명 세그먼트를 추출한다.
    public String extractFileName(String relativePath) {
        String normalizedPath = normalizeRelativePath(relativePath);
        if ("/".equals(normalizedPath)) {
            return "/";
        }

        int lastSlashIndex = normalizedPath.lastIndexOf('/');
        return normalizedPath.substring(lastSlashIndex + 1);
    }

    // 파일명에서 유효한 확장자를 추출하고 없으면 null을 반환한다.
    public String extractExtension(String name) {
        String normalizedName = normalizeChildName(name);
        int lastDotIndex = normalizedName.lastIndexOf('.');

        if (lastDotIndex <= 0 || lastDotIndex == normalizedName.length() - 1) {
            return null;
        }

        return normalizedName.substring(lastDotIndex + 1);
    }
}
