package com.example.fileserver.config;

import com.example.fileserver.common.error.ApiException;
import org.springframework.boot.context.properties.ConfigurationProperties;

import static com.example.fileserver.common.error.ErrorCode.INVALID_PATH;

@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(String rootPath) {

    // 저장소 루트 경로 설정값이 비어 있지 않은지 검증한다.
    public StorageProperties {
        if (rootPath == null || rootPath.isBlank()) {
            throw new ApiException(INVALID_PATH, "app.storage.root-path must not be blank.");
        }

        rootPath = rootPath.trim();
    }
}
