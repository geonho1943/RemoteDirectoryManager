package com.example.fileserver.entry.repository;

import com.example.fileserver.entry.entity.FileEntryEntity;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FileEntryRepository extends JpaRepository<FileEntryEntity, Long> {

    Optional<FileEntryEntity> findByRelativePath(String relativePath);

    boolean existsByRelativePath(String relativePath);

    List<FileEntryEntity> findByParentPath(String parentPath);

    List<FileEntryEntity> findByRelativePathStartingWith(String prefix);

    @Modifying
    @Transactional
    @Query("""
            delete from FileEntryEntity e
            where e.relativePath = :targetPath
               or (:targetPath = '/' and e.relativePath like '/%')
               or (:targetPath <> '/' and e.relativePath like concat(:targetPath, '/%'))
            """)
    int deleteSubtreeByRelativePath(@Param("targetPath") String targetPath);
}
