CREATE TABLE IF NOT EXISTS files (
    file_id BIGINT NOT NULL AUTO_INCREMENT,
    file_path VARCHAR(4096) NOT NULL,
    file_name VARCHAR(512) NOT NULL,
    file_extension VARCHAR(64) NULL,
    created_at DATETIME(6) NOT NULL,
    modified_at DATETIME(6) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_files PRIMARY KEY (file_id),
    CONSTRAINT uk_files_file_path UNIQUE (file_path),
    INDEX idx_files_file_name (file_name),
    INDEX idx_files_file_extension (file_extension),
    INDEX idx_files_modified_at (modified_at),
    INDEX idx_files_is_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS thumbnails (
    thumbnail_id BIGINT NOT NULL AUTO_INCREMENT,
    file_id BIGINT NOT NULL,
    thumbnail_path VARCHAR(4096) NOT NULL,
    thumbnail_name VARCHAR(512) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_thumbnails PRIMARY KEY (thumbnail_id),
    CONSTRAINT fk_thumbnails_file FOREIGN KEY (file_id) REFERENCES files (file_id) ON DELETE CASCADE,
    INDEX idx_thumbnails_file_id (file_id),
    INDEX idx_thumbnails_is_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS tags (
    tag_id BIGINT NOT NULL AUTO_INCREMENT,
    tag_name VARCHAR(120) NOT NULL,
    CONSTRAINT pk_tags PRIMARY KEY (tag_id),
    CONSTRAINT uk_tags_tag_name UNIQUE (tag_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS file_tags (
    file_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    CONSTRAINT pk_file_tags PRIMARY KEY (file_id, tag_id),
    CONSTRAINT fk_file_tags_file FOREIGN KEY (file_id) REFERENCES files (file_id) ON DELETE CASCADE,
    CONSTRAINT fk_file_tags_tag FOREIGN KEY (tag_id) REFERENCES tags (tag_id) ON DELETE CASCADE,
    INDEX idx_file_tags_tag_id (tag_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
