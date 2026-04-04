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

    Optional<FileEntryEntity> findByFilePath(String filePath);

    @EntityGraph(attributePaths = "tags")
    Optional<FileEntryEntity> findByFilePathAndActiveTrue(String filePath);

    @EntityGraph(attributePaths = "tags")
    List<FileEntryEntity> findByFilePathInAndActiveTrue(Collection<String> filePaths);

    @Modifying
    @Query("""
            update FileEntryEntity e
               set e.active = false
             where e.active = true
               and (e.filePath = :targetPath or e.filePath like concat(:targetPath, '/%'))
            """)
    int deactivateByFilePathOrDescendant(@Param("targetPath") String targetPath);
}
