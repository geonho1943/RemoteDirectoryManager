package com.example.fileserver.entry.service;

import com.example.fileserver.common.error.ApiException;
import com.example.fileserver.entry.dto.FileTagsResponse;
import com.example.fileserver.entry.dto.TagListResponse;
import com.example.fileserver.entry.dto.TagSummaryDto;
import com.example.fileserver.entry.entity.FileEntryEntity;
import com.example.fileserver.entry.entity.TagEntity;
import com.example.fileserver.entry.repository.TagRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.example.fileserver.common.error.ErrorCode.INVALID_TAG;

@Service
public class FileTagService {

    private static final int MAX_TAG_NAME_LENGTH = 120;

    private final FileMetadataService fileMetadataService;
    private final TagRepository tagRepository;

    // 태그 작업에 필요한 파일 메타데이터 서비스와 태그 저장소를 주입한다.
    public FileTagService(
            FileMetadataService fileMetadataService,
            TagRepository tagRepository
    ) {
        this.fileMetadataService = fileMetadataService;
        this.tagRepository = tagRepository;
    }

    // 저장된 태그 전체를 이름순 응답으로 반환한다.
    @Transactional(readOnly = true)
    public TagListResponse listTags() {
        return new TagListResponse(
                TagSummaryDto.sortedFrom(tagRepository.findAllByOrderByTagNameAsc())
        );
    }

    // 기존 태그와 새 태그명을 파일에 추가한다.
    @Transactional
    public FileTagsResponse assignTags(String path, Collection<Long> tagIds, Collection<String> tagNames) {
        FileEntryEntity file = fileMetadataService.syncFileRecord(path);
        Map<Long, TagEntity> tagsToAssign = new LinkedHashMap<>();

        addExistingTags(tagIds, tagsToAssign);
        addNewTags(tagNames, tagsToAssign);

        if (tagsToAssign.isEmpty()) {
            throw new ApiException(INVALID_TAG, "At least one tag must be selected or created.");
        }

        file.getTags().addAll(tagsToAssign.values());
        return toFileTagsResponse(file);
    }

    // 파일에서 요청한 태그 ID들을 제거한다.
    @Transactional
    public FileTagsResponse removeTags(String path, Collection<Long> tagIds) {
        FileEntryEntity file = fileMetadataService.syncFileRecord(path);
        List<Long> requestedIds = normalizeTagIds(tagIds);
        if (requestedIds.isEmpty()) {
            throw new ApiException(INVALID_TAG, "At least one attached tag must be selected.");
        }

        file.getTags().removeIf(tag -> requestedIds.contains(tag.getTagId()));
        return toFileTagsResponse(file);
    }

    // 요청된 기존 태그 ID들을 조회해 할당 대상에 추가한다.
    private void addExistingTags(Collection<Long> tagIds, Map<Long, TagEntity> tagsToAssign) {
        List<Long> requestedIds = normalizeTagIds(tagIds);
        if (requestedIds.isEmpty()) {
            return;
        }

        List<TagEntity> existingTags = tagRepository.findAllById(requestedIds);
        if (existingTags.size() != requestedIds.size()) {
            throw new ApiException(INVALID_TAG, "One or more selected tags do not exist.");
        }

        for (TagEntity tag : existingTags) {
            tagsToAssign.put(tag.getTagId(), tag);
        }
    }

    // 태그 ID 목록에서 null, 음수, 중복 값을 제거한다.
    private List<Long> normalizeTagIds(Collection<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return List.of();
        }

        return tagIds.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
    }

    // 요청된 새 태그명들을 정규화하고 없으면 생성해 할당 대상에 추가한다.
    private void addNewTags(Collection<String> tagNames, Map<Long, TagEntity> tagsToAssign) {
        if (tagNames == null || tagNames.isEmpty()) {
            return;
        }

        Map<String, String> normalizedNames = new LinkedHashMap<>();
        for (String rawTagName : tagNames) {
            String tagName = normalizeTagName(rawTagName);
            normalizedNames.putIfAbsent(tagName.toLowerCase(Locale.ROOT), tagName);
        }

        for (String tagName : normalizedNames.values()) {
            TagEntity tag = getOrCreateTag(tagName);
            tagsToAssign.put(tag.getTagId(), tag);
        }
    }

    // 태그명을 대소문자 무시 기준으로 조회하고 없으면 생성한다.
    private TagEntity getOrCreateTag(String tagName) {
        return tagRepository.findByTagNameIgnoreCase(tagName)
                .orElseGet(() -> createTag(tagName));
    }

    // 새 태그를 저장하고 동시 생성 충돌 시 기존 태그를 다시 조회한다.
    private TagEntity createTag(String tagName) {
        try {
            return tagRepository.save(new TagEntity(tagName));
        } catch (DataIntegrityViolationException exception) {
            return tagRepository.findByTagNameIgnoreCase(tagName)
                    .orElseThrow(() -> new ApiException(INVALID_TAG, "Failed to create tag: " + tagName, exception));
        }
    }

    // 태그명을 trim하고 빈 값과 길이 제한을 검증한다.
    private String normalizeTagName(String rawTagName) {
        if (rawTagName == null) {
            throw new ApiException(INVALID_TAG, "Tag name must not be null.");
        }

        String trimmed = rawTagName.trim();
        if (trimmed.isEmpty()) {
            throw new ApiException(INVALID_TAG, "Tag name must not be blank.");
        }

        if (trimmed.length() > MAX_TAG_NAME_LENGTH) {
            throw new ApiException(INVALID_TAG, "Tag name must be at most " + MAX_TAG_NAME_LENGTH + " characters.");
        }

        return trimmed;
    }

    // 파일 엔티티의 현재 태그 상태를 응답 DTO로 변환한다.
    private FileTagsResponse toFileTagsResponse(FileEntryEntity file) {
        return new FileTagsResponse(
                file.getFileId(),
                file.getFilePath(),
                TagSummaryDto.sortedFrom(file.getTags())
        );
    }
}
