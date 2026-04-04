package com.example.fileserver.entry.repository;

import com.example.fileserver.entry.entity.TagEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TagRepository extends JpaRepository<TagEntity, Long> {

    Optional<TagEntity> findByTagName(String tagName);

    Optional<TagEntity> findByTagNameIgnoreCase(String tagName);

    List<TagEntity> findAllByOrderByTagNameAsc();
}
