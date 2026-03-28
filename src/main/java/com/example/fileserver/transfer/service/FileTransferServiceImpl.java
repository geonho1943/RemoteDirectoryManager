package com.example.fileserver.transfer.service;

import com.example.fileserver.common.error.EntryNotFoundException;
import com.example.fileserver.common.error.FileOperationException;
import com.example.fileserver.common.error.InvalidRangeHeaderException;
import com.example.fileserver.common.error.NotAFileException;
import com.example.fileserver.filesystem.path.PathNormalizer;
import com.example.fileserver.filesystem.path.PathResolver;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.charset.StandardCharsets;

@Service
public class FileTransferServiceImpl implements FileTransferService {

    private static final String ACCEPT_RANGES_VALUE = "bytes";

    private final PathNormalizer pathNormalizer;
    private final PathResolver pathResolver;

    public FileTransferServiceImpl(PathNormalizer pathNormalizer, PathResolver pathResolver) {
        this.pathNormalizer = pathNormalizer;
        this.pathResolver = pathResolver;
    }

    @Override
    public ResponseEntity<Resource> downloadFile(String path) {
        String normalizedPath = pathNormalizer.normalizeRelativePath(path);
        Path filePath = resolveRegularFile(normalizedPath);

        try {
            Resource resource = new UrlResource(filePath.toUri());
            String fileName = pathNormalizer.extractFileName(normalizedPath);

            return ResponseEntity.ok()
                    .contentType(resolveMediaType(filePath))
                    .contentLength(Files.size(filePath))
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                            .filename(fileName, StandardCharsets.UTF_8)
                            .build()
                            .toString())
                    .body(resource);
        } catch (IOException exception) {
            throw new FileOperationException("Failed to prepare file download: " + normalizedPath, exception);
        }
    }

    @Override
    public ResponseEntity<Resource> streamFile(String path, String rangeHeader) {
        String normalizedPath = pathNormalizer.normalizeRelativePath(path);
        Path filePath = resolveRegularFile(normalizedPath);
        MediaType mediaType = resolveMediaType(filePath);

        try {
            long fileSize = Files.size(filePath);
            if (rangeHeader == null || rangeHeader.isBlank()) {
                Resource resource = new UrlResource(filePath.toUri());
                return ResponseEntity.ok()
                        .contentType(mediaType)
                        .contentLength(fileSize)
                        .header(HttpHeaders.ACCEPT_RANGES, ACCEPT_RANGES_VALUE)
                        .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                                .filename(pathNormalizer.extractFileName(normalizedPath), StandardCharsets.UTF_8)
                                .build()
                                .toString())
                        .body(resource);
            }

            RangeSpec rangeSpec = parseRangeHeader(rangeHeader, fileSize);
            Resource resource = new InputStreamResource(openRangedStream(filePath, rangeSpec.start(), rangeSpec.contentLength()));

            return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                    .contentType(mediaType)
                    .contentLength(rangeSpec.contentLength())
                    .header(HttpHeaders.ACCEPT_RANGES, ACCEPT_RANGES_VALUE)
                    .header(HttpHeaders.CONTENT_RANGE, "bytes " + rangeSpec.start() + "-" + rangeSpec.end() + "/" + fileSize)
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                            .filename(pathNormalizer.extractFileName(normalizedPath), StandardCharsets.UTF_8)
                            .build()
                            .toString())
                    .body(resource);
        } catch (IOException exception) {
            throw new FileOperationException("Failed to prepare file stream: " + normalizedPath, exception);
        }
    }

    private Path resolveRegularFile(String relativePath) {
        Path filePath = pathResolver.resolveUnderRoot(relativePath);
        if (!Files.exists(filePath, LinkOption.NOFOLLOW_LINKS)) {
            throw new EntryNotFoundException("Entry not found: " + relativePath);
        }

        if (!Files.isRegularFile(filePath, LinkOption.NOFOLLOW_LINKS)) {
            throw new NotAFileException("Path is not a file: " + relativePath);
        }

        return filePath;
    }

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

    private RangeSpec parseRangeHeader(String rangeHeader, long fileSize) {
        String trimmedHeader = rangeHeader.trim();
        if (!trimmedHeader.startsWith("bytes=")) {
            throw new InvalidRangeHeaderException("Invalid Range header.");
        }

        String rangeValue = trimmedHeader.substring("bytes=".length()).trim();
        if (rangeValue.isEmpty() || rangeValue.contains(",")) {
            throw new InvalidRangeHeaderException("Invalid Range header.");
        }

        int dashIndex = rangeValue.indexOf('-');
        if (dashIndex < 0) {
            throw new InvalidRangeHeaderException("Invalid Range header.");
        }

        String startPart = rangeValue.substring(0, dashIndex).trim();
        String endPart = rangeValue.substring(dashIndex + 1).trim();

        if (startPart.isEmpty() && endPart.isEmpty()) {
            throw new InvalidRangeHeaderException("Invalid Range header.");
        }

        if (fileSize == 0) {
            throw new InvalidRangeHeaderException("Invalid Range header.");
        }

        try {
            long start;
            long end;

            if (startPart.isEmpty()) {
                long suffixLength = Long.parseLong(endPart);
                if (suffixLength <= 0) {
                    throw new InvalidRangeHeaderException("Invalid Range header.");
                }

                start = Math.max(fileSize - suffixLength, 0);
                end = fileSize - 1;
            } else {
                start = Long.parseLong(startPart);
                if (start < 0 || start >= fileSize) {
                    throw new InvalidRangeHeaderException("Invalid Range header.");
                }

                if (endPart.isEmpty()) {
                    end = fileSize - 1;
                } else {
                    end = Long.parseLong(endPart);
                    if (end < start || end >= fileSize) {
                        throw new InvalidRangeHeaderException("Invalid Range header.");
                    }
                }
            }

            return new RangeSpec(start, end, end - start + 1);
        } catch (NumberFormatException exception) {
            throw new InvalidRangeHeaderException("Invalid Range header.", exception);
        }
    }

    private InputStream openRangedStream(Path filePath, long start, long contentLength) throws IOException {
        SeekableByteChannel channel = Files.newByteChannel(filePath, StandardOpenOption.READ);
        channel.position(start);
        return new BoundedInputStream(Channels.newInputStream(channel), contentLength);
    }

    private record RangeSpec(long start, long end, long contentLength) {
    }

    private static final class BoundedInputStream extends InputStream {

        private final InputStream delegate;
        private long remaining;

        private BoundedInputStream(InputStream delegate, long remaining) {
            this.delegate = delegate;
            this.remaining = remaining;
        }

        @Override
        public int read() throws IOException {
            if (remaining <= 0) {
                return -1;
            }

            int value = delegate.read();
            if (value != -1) {
                remaining--;
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int off, int len) throws IOException {
            if (remaining <= 0) {
                return -1;
            }

            int bytesToRead = (int) Math.min(len, remaining);
            int read = delegate.read(buffer, off, bytesToRead);
            if (read > 0) {
                remaining -= read;
            }
            return read;
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }
}
