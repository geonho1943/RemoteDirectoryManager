package com.example.fileserver.entry.controller;

import com.example.fileserver.common.error.ApiException;
import com.example.fileserver.entry.ConflictPolicy;
import com.example.fileserver.entry.dto.DirectoryListResponse;
import com.example.fileserver.entry.dto.FileEntryResponse;
import com.example.fileserver.entry.dto.FileTagsResponse;
import com.example.fileserver.entry.dto.PathResponse;
import com.example.fileserver.entry.dto.TagListResponse;
import com.example.fileserver.entry.service.FileCommandService;
import com.example.fileserver.entry.service.FileQueryService;
import com.example.fileserver.entry.service.FileTagService;
import com.example.fileserver.transfer.service.FileTransferService;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static com.example.fileserver.common.error.ErrorCode.INVALID_REQUEST;
import static com.example.fileserver.common.error.ErrorCode.INVALID_TAG;

@RestController
@RequestMapping("/api/v1")
public class EntryController {

    private final FileQueryService fileQueryService;
    private final FileCommandService fileCommandService;
    private final FileTagService fileTagService;
    private final FileTransferService fileTransferService;

    // 엔트리 조회, 명령, 태그, 전송 서비스를 하나의 API 컨트롤러에 주입한다.
    public EntryController(
            FileQueryService fileQueryService,
            FileCommandService fileCommandService,
            FileTagService fileTagService,
            FileTransferService fileTransferService
    ) {
        this.fileQueryService = fileQueryService;
        this.fileCommandService = fileCommandService;
        this.fileTagService = fileTagService;
        this.fileTransferService = fileTransferService;
    }

    // 지정 경로의 하위 파일과 디렉터리 목록을 반환한다.
    @GetMapping("/entries")
    public DirectoryListResponse listEntries(
            @RequestParam("path") String path,
            @RequestParam(name = "includeHidden", defaultValue = "true") boolean includeHidden
    ) {
        return fileQueryService.listEntries(path, includeHidden);
    }

    // 지정 경로의 파일 또는 디렉터리 상세 정보를 반환한다.
    @GetMapping("/entries/detail")
    public FileEntryResponse getEntryDetail(@RequestParam("path") String path) {
        return fileQueryService.getEntryDetail(path);
    }

    // 지정 경로의 파일 또는 디렉터리를 삭제한다.
    @DeleteMapping("/entries")
    public ResponseEntity<Void> deleteEntry(@RequestBody DeleteEntryRequest request) {
        if (request == null) {
            throw new ApiException(INVALID_REQUEST, "Delete request is required.");
        }

        fileCommandService.deleteEntry(request.path());
        return ResponseEntity.noContent().build();
    }

    // 지정 부모 경로 아래에 새 디렉터리를 생성한다.
    @PostMapping("/directories")
    public ResponseEntity<PathResponse> createDirectory(@RequestBody CreateDirectoryRequest request) {
        if (request == null) {
            throw new ApiException(INVALID_REQUEST, "Directory creation request is required.");
        }

        PathResponse response = fileCommandService.createDirectory(request.parentPath(), request.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 파일 업로드 요청을 받아 충돌 정책에 따라 저장한다.
    @PostMapping("/files/upload")
    public ResponseEntity<PathResponse> uploadFile(
            @RequestParam("parentPath") String parentPath,
            @RequestParam("conflictPolicy") ConflictPolicy conflictPolicy,
            @RequestPart("file") MultipartFile file
    ) {
        PathResponse response = fileCommandService.uploadFile(parentPath, conflictPolicy, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 지정 파일을 다운로드 응답으로 반환한다.
    @GetMapping("/files/download")
    public ResponseEntity<Resource> downloadFile(@RequestParam("path") String path) {
        return fileTransferService.downloadFile(path);
    }

    // Range 헤더가 없는 요청은 파일 전체를 inline 응답으로 반환한다.
    @GetMapping(value = "/files/stream", headers = "!Range")
    public ResponseEntity<Resource> streamFile(@RequestParam("path") String path) {
        return fileTransferService.streamFile(path);
    }

    // Range 헤더가 있는 요청은 단일 부분 영역을 206 응답으로 반환한다.
    @GetMapping(value = "/files/stream", headers = HttpHeaders.RANGE)
    public ResponseEntity<ResourceRegion> streamFileRegion(
            @RequestParam("path") String path,
            @RequestHeader(HttpHeaders.RANGE) String rangeHeader
    ) {
        return fileTransferService.streamFileRegion(path, rangeHeader);
    }

    // 전체 태그 목록을 반환한다.
    @GetMapping("/tags")
    public TagListResponse listTags() {
        return fileTagService.listTags();
    }

    // 기존 태그 또는 새 태그명을 파일에 할당한다.
    @PostMapping("/files/tags")
    public ResponseEntity<FileTagsResponse> assignTags(@RequestBody AssignTagsRequest request) {
        if (request == null) {
            throw new ApiException(INVALID_TAG, "Tag assignment request is required.");
        }

        return ResponseEntity.ok(fileTagService.assignTags(request.path(), request.tagIds(), request.tagNames()));
    }

    // 파일에서 지정 태그들을 제거한다.
    @DeleteMapping("/files/tags")
    public ResponseEntity<FileTagsResponse> removeTags(@RequestBody RemoveTagsRequest request) {
        if (request == null) {
            throw new ApiException(INVALID_TAG, "Tag removal request is required.");
        }

        return ResponseEntity.ok(fileTagService.removeTags(request.path(), request.tagIds()));
    }

    public record CreateDirectoryRequest(String parentPath, String name) {
    }

    public record DeleteEntryRequest(String path) {
    }

    public record AssignTagsRequest(String path, List<Long> tagIds, List<String> tagNames) {
    }

    public record RemoveTagsRequest(String path, List<Long> tagIds) {
    }
}
