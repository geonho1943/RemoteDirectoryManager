package com.example.fileserver.config;

import com.example.fileserver.common.error.ApiException;
import com.example.fileserver.filesystem.path.PathNormalizer;
import com.example.fileserver.filesystem.path.PathResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.example.fileserver.common.error.ErrorCode.INVALID_PATH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FilesystemPathConfigTest {

    @TempDir
    Path tempDirectory;

    private final FilesystemPathConfig config = new FilesystemPathConfig();
    private final PathNormalizer pathNormalizer = new PathNormalizer();

    @Test
    void createsMissingStorageRoot() {
        Path storageRoot = tempDirectory.resolve("nested/storage");

        PathResolver resolver = config.pathResolver(
                new StorageProperties(storageRoot.toString()),
                pathNormalizer
        );

        assertThat(storageRoot).isDirectory();
        assertThat(resolver.resolveUnderRoot("/")).isEqualTo(storageRoot.toAbsolutePath().normalize());
    }

    @Test
    void rejectsStorageRootThatIsARegularFile() throws IOException {
        Path regularFile = Files.writeString(tempDirectory.resolve("not-a-directory"), "data");

        assertThatThrownBy(() -> config.pathResolver(
                new StorageProperties(regularFile.toString()),
                pathNormalizer
        )).isInstanceOfSatisfying(ApiException.class,
                exception -> assertThat(exception.errorCode()).isEqualTo(INVALID_PATH));
    }
}
