package com.example.fileserver.entry.service;

import com.example.fileserver.common.error.InvalidTagException;
import com.example.fileserver.entry.dto.AssignTagsRequest;
import com.example.fileserver.entry.dto.FileTagsResponse;
import com.example.fileserver.entry.dto.RemoveTagsRequest;
import com.example.fileserver.entry.dto.TagListResponse;
import com.example.fileserver.entry.dto.TagSummaryMapper;
import com.example.fileserver.entry.entity.FileEntryEntity;
import com.example.fileserver.entry.entity.TagEntity;
import com.example.fileserver.entry.repository.FileEntryRepository;
import com.example.fileserver.entry.repository.TagRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class FileTagServiceImpl implements FileTagService {

    private static final int MAX_TAG_NAME_LENGTH = 120;

    private final FileMetadataService fileMetadataService;
    private final FileEntryRepository fileEntryRepository;
    private final TagRepository tagRepository;

    public FileTagServiceImpl(
            FileMetadataService fileMetadataService,
            FileEntryRepository fileEntryRepository,
            TagRepository tagRepository
    ) {
        this.fileMetadataService = fileMetadataService;
        this.fileEntryRepository = fileEntryRepository;
        this.tagRepository = tagRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public TagListResponse listTags() {
        return new TagListResponse(
                TagSummaryMapper.toSortedTagSummaries(tagRepository.findAllByOrderByTagNameAsc())
        );
    }

    @Override
    @Transactional
    public FileTagsResponse assignTags(AssignTagsRequest request) {
        if (request == null) {
            throw new InvalidTagException("Tag assignment request is required.");
        }

        FileEntryEntity file = fileMetadataService.syncFileRecord(request.path());
        Map<Long, TagEntity> tagsToAssign = new LinkedHashMap<>();

        addExistingTags(request.tagIds(), tagsToAssign);
        addNewTags(request.tagNames(), tagsToAssign);

        if (tagsToAssign.isEmpty()) {
            throw new InvalidTagException("At least one tag must be selected or created.");
        }

        file.getTags().addAll(tagsToAssign.values());
        return toFileTagsResponse(fileEntryRepository.save(file));
    }

    @Override
    @Transactional
    public FileTagsResponse removeTags(RemoveTagsRequest request) {
        if (request == null) {
            throw new InvalidTagException("Tag removal request is required.");
        }

        FileEntryEntity file = fileMetadataService.syncFileRecord(request.path());
        List<Long> requestedIds = normalizeTagIds(request.tagIds());
        if (requestedIds.isEmpty()) {
            throw new InvalidTagException("At least one attached tag must be selected.");
        }

        file.getTags().removeIf(tag -> requestedIds.contains(tag.getTagId()));
        return toFileTagsResponse(fileEntryRepository.save(file));
    }

    private void addExistingTags(Collection<Long> tagIds, Map<Long, TagEntity> tagsToAssign) {
        List<Long> requestedIds = normalizeTagIds(tagIds);
        if (requestedIds.isEmpty()) {
            return;
        }

        List<TagEntity> existingTags = tagRepository.findAllById(requestedIds);
        if (existingTags.size() != requestedIds.size()) {
            throw new InvalidTagException("One or more selected tags do not exist.");
        }

        for (TagEntity tag : existingTags) {
            tagsToAssign.put(tag.getTagId(), tag);
        }
    }

    private List<Long> normalizeTagIds(Collection<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return List.of();
        }

        return tagIds.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
    }

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

    private TagEntity getOrCreateTag(String tagName) {
        return tagRepository.findByTagNameIgnoreCase(tagName)
                .orElseGet(() -> createTag(tagName));
    }

    private TagEntity createTag(String tagName) {
        try {
            return tagRepository.save(new TagEntity(tagName));
        } catch (DataIntegrityViolationException exception) {
            return tagRepository.findByTagNameIgnoreCase(tagName)
                    .orElseThrow(() -> new InvalidTagException("Failed to create tag: " + tagName, exception));
        }
    }

    private String normalizeTagName(String rawTagName) {
        if (rawTagName == null) {
            throw new InvalidTagException("Tag name must not be null.");
        }

        String trimmed = rawTagName.trim();
        if (trimmed.isEmpty()) {
            throw new InvalidTagException("Tag name must not be blank.");
        }

        if (trimmed.length() > MAX_TAG_NAME_LENGTH) {
            throw new InvalidTagException("Tag name must be at most " + MAX_TAG_NAME_LENGTH + " characters.");
        }

        return trimmed;
    }

    private FileTagsResponse toFileTagsResponse(FileEntryEntity file) {
        return new FileTagsResponse(
                file.getFileId(),
                file.getFilePath(),
                TagSummaryMapper.toSortedTagSummaries(file.getTags())
        );
    }
}
