package com.example.fileserver.transfer.service;

import com.example.fileserver.common.error.ApiException;
import com.example.fileserver.filesystem.path.PathNormalizer;
import com.example.fileserver.filesystem.path.PathResolver;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpRange;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static com.example.fileserver.common.error.ErrorCode.ENTRY_NOT_FOUND;
import static com.example.fileserver.common.error.ErrorCode.FILE_OPERATION_FAILED;
import static com.example.fileserver.common.error.ErrorCode.INVALID_RANGE_HEADER;
import static com.example.fileserver.common.error.ErrorCode.NOT_A_FILE;

@Service
public class FileTransferService {

    private static final String ACCEPT_RANGES_VALUE = "bytes";

    private final PathNormalizer pathNormalizer;
    private final PathResolver pathResolver;

    // 파일 전송에 필요한 경로 정규화와 루트 경로 해석 도구를 주입한다.
    public FileTransferService(PathNormalizer pathNormalizer, PathResolver pathResolver) {
        this.pathNormalizer = pathNormalizer;
        this.pathResolver = pathResolver;
    }

    // 파일을 첨부 다운로드 응답으로 반환한다.
    public ResponseEntity<Resource> downloadFile(String path) {
        String normalizedPath = pathNormalizer.normalizeRelativePath(path);
        Path filePath = resolveRegularFile(normalizedPath);

        try {
            return buildFullContentResponse(filePath, normalizedPath, ContentDisposition.attachment())
                    .body(new UrlResource(filePath.toUri()));
        } catch (IOException exception) {
            throw new ApiException(FILE_OPERATION_FAILED, "Failed to prepare file download: " + normalizedPath, exception);
        }
    }

    // Range 헤더가 있으면 부분 응답을, 없으면 전체 inline 스트림 응답을 반환한다.
    public ResponseEntity<?> streamFile(String path, String rangeHeader) {
        String normalizedPath = pathNormalizer.normalizeRelativePath(path);
        Path filePath = resolveRegularFile(normalizedPath);
        MediaType mediaType = resolveMediaType(filePath);

        try {
            Resource resource = new UrlResource(filePath.toUri());
            if (rangeHeader == null || rangeHeader.isBlank()) {
                return buildFullContentResponse(filePath, normalizedPath, ContentDisposition.inline())
                        .header(HttpHeaders.ACCEPT_RANGES, ACCEPT_RANGES_VALUE)
                        .body(resource);
            }

            ResourceRegion region = toSingleResourceRegion(rangeHeader, resource);
            return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                    .contentType(mediaType)
                    .header(
                            HttpHeaders.CONTENT_DISPOSITION,
                            ContentDisposition.inline()
                                    .filename(pathNormalizer.extractFileName(normalizedPath), StandardCharsets.UTF_8)
                                    .build()
                                    .toString()
                    )
                    .header(HttpHeaders.ACCEPT_RANGES, ACCEPT_RANGES_VALUE)
                    .body(region);
        } catch (IOException exception) {
            throw new ApiException(FILE_OPERATION_FAILED, "Failed to prepare file stream: " + normalizedPath, exception);
        }
    }

    // 요청 경로가 실제 일반 파일인지 확인하고 절대 경로로 변환한다.
    private Path resolveRegularFile(String relativePath) {
        Path filePath = pathResolver.resolveUnderRoot(relativePath);
        if (!Files.exists(filePath, LinkOption.NOFOLLOW_LINKS)) {
            throw new ApiException(ENTRY_NOT_FOUND, "Entry not found: " + relativePath);
        }

        if (!Files.isRegularFile(filePath, LinkOption.NOFOLLOW_LINKS)) {
            throw new ApiException(NOT_A_FILE, "Path is not a file: " + relativePath);
        }

        return filePath;
    }

    // 파일 확장자와 시스템 탐지를 기반으로 응답 Content-Type을 결정한다.
    private MediaType resolveMediaType(Path filePath) {
        try {
            String contentType = Files.probeContentType(filePath);
            if (contentType == null || contentType.isBlank()) {
                return MediaType.APPLICATION_OCTET_STREAM;
            }

            return MediaType.parseMediaType(contentType);
        } catch (IOException | IllegalArgumentException exception) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    // 단일 Range 헤더를 Spring ResourceRegion으로 변환한다.
    private ResourceRegion toSingleResourceRegion(String rangeHeader, Resource resource) {
        try {
            List<HttpRange> ranges = HttpRange.parseRanges(rangeHeader);
            long contentLength = resource.contentLength();
            if (ranges.size() != 1 || contentLength == 0) {
                throw invalidRangeHeader();
            }

            HttpRange range = ranges.get(0);
            long start = range.getRangeStart(contentLength);
            long end = range.getRangeEnd(contentLength);
            if (start < 0 || start >= contentLength || end < start) {
                throw invalidRangeHeader();
            }

            return range.toResourceRegion(resource);
        } catch (IllegalArgumentException exception) {
            throw invalidRangeHeader(exception);
        } catch (IOException exception) {
            throw new ApiException(FILE_OPERATION_FAILED, "Failed to read stream resource length.", exception);
        }
    }

    // 전체 파일 응답에 공통으로 필요한 헤더를 구성한다.
    private ResponseEntity.BodyBuilder buildFullContentResponse(
            Path filePath,
            String normalizedPath,
            ContentDisposition.Builder dispositionBuilder
    ) throws IOException {
        return baseResponse(
                HttpStatus.OK,
                resolveMediaType(filePath),
                Files.size(filePath),
                pathNormalizer.extractFileName(normalizedPath),
                dispositionBuilder
        );
    }

    // 상태, 타입, 길이, 파일명을 담은 기본 응답 빌더를 만든다.
    private ResponseEntity.BodyBuilder baseResponse(
            HttpStatus status,
            MediaType mediaType,
            long contentLength,
            String fileName,
            ContentDisposition.Builder dispositionBuilder
    ) {
        return ResponseEntity.status(status)
                .contentType(mediaType)
                .contentLength(contentLength)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        dispositionBuilder.filename(fileName, StandardCharsets.UTF_8).build().toString()
                );
    }

    // 원인 예외가 없는 Range 헤더 오류를 만든다.
    private ApiException invalidRangeHeader() {
        return new ApiException(INVALID_RANGE_HEADER, "Invalid Range header.");
    }

    // 원인 예외를 포함한 Range 헤더 오류를 만든다.
    private ApiException invalidRangeHeader(Throwable cause) {
        return new ApiException(INVALID_RANGE_HEADER, "Invalid Range header.", cause);
    }
}
