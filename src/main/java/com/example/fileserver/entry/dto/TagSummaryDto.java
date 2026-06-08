package com.example.fileserver.entry.dto;

import com.example.fileserver.entry.entity.TagEntity;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;

public record TagSummaryDto(
        Long tagId,
        String tagName
) {

    private static final Comparator<TagSummaryDto> TAG_NAME_ORDER =
            Comparator.comparing(TagSummaryDto::tagName, String.CASE_INSENSITIVE_ORDER);

    // 태그 엔티티 하나를 요약 응답 DTO로 변환한다.
    public static TagSummaryDto from(TagEntity tag) {
        return new TagSummaryDto(tag.getTagId(), tag.getTagName());
    }

    // 태그 엔티티 컬렉션을 이름순 요약 응답 목록으로 변환한다.
    public static List<TagSummaryDto> sortedFrom(Collection<TagEntity> tags) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }

        return tags.stream()
                .map(TagSummaryDto::from)
                .sorted(TAG_NAME_ORDER)
                .toList();
    }
}
