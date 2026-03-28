package com.example.fileserver.config;

import com.example.fileserver.common.error.InvalidPathException;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(String rootPath) {

    public StorageProperties {
        if (rootPath == null || rootPath.isBlank()) {
            throw new InvalidPathException("app.storage.root-path must not be blank.");
        }

        rootPath = rootPath.trim();
    }
}
