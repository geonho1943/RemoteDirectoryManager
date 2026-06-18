package com.example.fileserver.filesystem.path;

import com.example.fileserver.common.error.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static com.example.fileserver.common.error.ErrorCode.INVALID_PATH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PathResolverTest {

    @TempDir
    Path rootPath;

    @Test
    void resolvesOnlyUnderConfiguredRoot() {
        PathResolver resolver = new PathResolver(rootPath, new PathNormalizer());

        assertThat(resolver.resolveUnderRoot("/docs/file.txt"))
                .isEqualTo(rootPath.resolve("docs/file.txt").toAbsolutePath().normalize());
    }

    @Test
    void convertsInvalidFilesystemCharactersToApiError() {
        PathResolver resolver = new PathResolver(rootPath, new PathNormalizer());

        assertThatThrownBy(() -> resolver.resolveUnderRoot("/bad\0name"))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(INVALID_PATH));
    }
}
