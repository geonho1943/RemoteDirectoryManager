package com.example.fileserver.entry.repository;

import com.example.fileserver.entry.entity.TagEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TagRepository extends JpaRepository<TagEntity, Long> {

    // 태그명을 대소문자 구분 없이 조회한다.
    Optional<TagEntity> findByTagNameIgnoreCase(String tagName);

    // 전체 태그를 이름순으로 조회한다.
    List<TagEntity> findAllByOrderByTagNameAsc();
}
