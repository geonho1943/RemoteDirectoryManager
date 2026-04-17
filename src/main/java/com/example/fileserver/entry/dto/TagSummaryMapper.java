package com.example.fileserver.entry.dto;

import com.example.fileserver.entry.entity.TagEntity;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;

public final class TagSummaryMapper {

    private static final Comparator<TagSummaryDto> TAG_NAME_ORDER =
            Comparator.comparing(TagSummaryDto::tagName, String.CASE_INSENSITIVE_ORDER);

    private TagSummaryMapper() {
    }

    public static TagSummaryDto toTagSummary(TagEntity tag) {
        return new TagSummaryDto(tag.getTagId(), tag.getTagName());
    }

    public static List<TagSummaryDto> toSortedTagSummaries(Collection<TagEntity> tags) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }

        return tags.stream()
                .map(TagSummaryMapper::toTagSummary)
                .sorted(TAG_NAME_ORDER)
                .toList();
    }
}
