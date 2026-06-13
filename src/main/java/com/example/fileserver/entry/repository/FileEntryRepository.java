package com.example.fileserver.entry.repository;

import com.example.fileserver.entry.entity.FileEntryEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FileEntryRepository extends JpaRepository<FileEntryEntity, Long> {

    // 경로와 일치하는 파일 메타데이터를 활성 여부와 무관하게 조회한다.
    Optional<FileEntryEntity> findByFilePath(String filePath);

    // 경로 목록에 해당하는 활성 파일 메타데이터와 태그를 함께 조회한다.
    @EntityGraph(attributePaths = "tags")
    List<FileEntryEntity> findByFilePathInAndActiveTrue(Collection<String> filePaths);

    // 지정 경로와 하위 경로의 활성 파일 메타데이터를 비활성화한다.
    @Modifying
    @Query("""
            update FileEntryEntity e
               set e.active = false
             where e.active = true
               and (e.filePath = :targetPath or e.filePath like concat(:targetPath, '/%'))
            """)
    int deactivateByFilePathOrDescendant(@Param("targetPath") String targetPath);
}
