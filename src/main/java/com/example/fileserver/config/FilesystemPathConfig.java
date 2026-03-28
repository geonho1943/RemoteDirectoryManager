package com.example.fileserver.config;

import com.example.fileserver.filesystem.path.PathNormalizer;
import com.example.fileserver.filesystem.path.PathNormalizerImpl;
import com.example.fileserver.filesystem.path.PathResolver;
import com.example.fileserver.filesystem.path.PathResolverImpl;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class FilesystemPathConfig {

    @Bean
    public PathNormalizer pathNormalizer() {
        return new PathNormalizerImpl();
    }

    @Bean
    public PathResolver pathResolver(StorageProperties storageProperties, PathNormalizer pathNormalizer) {
        return new PathResolverImpl(storageProperties.rootPath(), pathNormalizer);
    }
}
