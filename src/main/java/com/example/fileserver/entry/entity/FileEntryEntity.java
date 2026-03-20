package com.example.fileserver.entry.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "file_entries",
        indexes = {
                @Index(name = "idx_file_entries_parent_path", columnList = "parent_path"),
                @Index(name = "idx_file_entries_name", columnList = "name"),
                @Index(name = "idx_file_entries_entry_type", columnList = "entry_type"),
                @Index(name = "idx_file_entries_modified_at", columnList = "modified_at"),
                @Index(name = "idx_file_entries_filesystem_id", columnList = "filesystem_id")
        }
)
public class FileEntryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "filesystem_id", nullable = false, length = 64)
    private String filesystemId = "default";

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 16)
    private FileEntryType entryType;

    @Column(name = "relative_path", nullable = false, unique = true, length = 4096)
    private String relativePath;

    @Column(name = "parent_path", nullable = false, length = 4096)
    private String parentPath;

    @Column(name = "name", nullable = false, length = 512)
    private String name;

    @Column(name = "extension", length = 64)
    private String extension;

    @Column(name = "mime_type", length = 255)
    private String mimeType;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "modified_at", nullable = false)
    private LocalDateTime modifiedAt;

    @Column(name = "created_at_fs")
    private LocalDateTime createdAtFs;

    @Column(name = "is_hidden", nullable = false)
    private boolean hidden;

    @Column(name = "checksum_sha256", length = 64)
    private String checksumSha256;

    @Column(name = "last_scanned_at", nullable = false)
    private LocalDateTime lastScannedAt;

    @Column(name = "db_created_at", nullable = false, updatable = false)
    private LocalDateTime dbCreatedAt;

    @Column(name = "db_updated_at", nullable = false)
    private LocalDateTime dbUpdatedAt;

    protected FileEntryEntity() {
    }

    public Long getId() {
        return id;
    }

    public String getFilesystemId() {
        return filesystemId;
    }

    public void setFilesystemId(String filesystemId) {
        this.filesystemId = filesystemId;
    }

    public FileEntryType getEntryType() {
        return entryType;
    }

    public void setEntryType(FileEntryType entryType) {
        this.entryType = entryType;
    }

    public String getRelativePath() {
        return relativePath;
    }

    public void setRelativePath(String relativePath) {
        this.relativePath = relativePath;
    }

    public String getParentPath() {
        return parentPath;
    }

    public void setParentPath(String parentPath) {
        this.parentPath = parentPath;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getExtension() {
        return extension;
    }

    public void setExtension(String extension) {
        this.extension = extension;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(Long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public LocalDateTime getModifiedAt() {
        return modifiedAt;
    }

    public void setModifiedAt(LocalDateTime modifiedAt) {
        this.modifiedAt = modifiedAt;
    }

    public LocalDateTime getCreatedAtFs() {
        return createdAtFs;
    }

    public void setCreatedAtFs(LocalDateTime createdAtFs) {
        this.createdAtFs = createdAtFs;
    }

    public boolean isHidden() {
        return hidden;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }

    public String getChecksumSha256() {
        return checksumSha256;
    }

    public void setChecksumSha256(String checksumSha256) {
        this.checksumSha256 = checksumSha256;
    }

    public LocalDateTime getLastScannedAt() {
        return lastScannedAt;
    }

    public void setLastScannedAt(LocalDateTime lastScannedAt) {
        this.lastScannedAt = lastScannedAt;
    }

    public LocalDateTime getDbCreatedAt() {
        return dbCreatedAt;
    }

    public LocalDateTime getDbUpdatedAt() {
        return dbUpdatedAt;
    }

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.dbCreatedAt = now;
        this.dbUpdatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.dbUpdatedAt = LocalDateTime.now();
    }
}
