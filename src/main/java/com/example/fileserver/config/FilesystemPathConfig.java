package com.example.fileserver.config;

import com.example.fileserver.filesystem.path.PathNormalizer;
import com.example.fileserver.filesystem.path.PathResolver;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class FilesystemPathConfig {

    // 경로 문자열 정규화 도구를 빈으로 등록한다.
    @Bean
    public PathNormalizer pathNormalizer() {
        return new PathNormalizer();
    }

    // 설정된 저장소 루트를 기준으로 경로 해석기를 생성한다.
    @Bean
    public PathResolver pathResolver(StorageProperties storageProperties, PathNormalizer pathNormalizer) {
        return new PathResolver(storageProperties.rootPath(), pathNormalizer);
    }
}
