package com.example.fileserver.filesystem.path;

import com.example.fileserver.common.error.ApiException;
import org.junit.jupiter.api.Test;

import static com.example.fileserver.common.error.ErrorCode.INVALID_ENTRY_NAME;
import static com.example.fileserver.common.error.ErrorCode.INVALID_PATH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PathNormalizerTest {

    private final PathNormalizer pathNormalizer = new PathNormalizer();

    @Test
    void normalizesApiPaths() {
        assertThat(pathNormalizer.normalizeRelativePath("//docs/./images//")).isEqualTo("/docs/images");
        assertThat(pathNormalizer.normalizeRelativePath("/")).isEqualTo("/");
    }

    @Test
    void rejectsTraversalAndAbsoluteWindowsPaths() {
        assertApiError(() -> pathNormalizer.normalizeRelativePath("/docs/../secret"), INVALID_PATH);
        assertApiError(() -> pathNormalizer.normalizeRelativePath("C:\\secret"), INVALID_PATH);
        assertApiError(() -> pathNormalizer.normalizeRelativePath("\\\\server\\share"), INVALID_PATH);
    }

    @Test
    void rejectsNamesContainingPathSeparators() {
        assertApiError(() -> pathNormalizer.normalizeChildName("docs/file.txt"), INVALID_ENTRY_NAME);
        assertApiError(() -> pathNormalizer.normalizeChildName(".."), INVALID_ENTRY_NAME);
    }

    private void assertApiError(Runnable action, com.example.fileserver.common.error.ErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(errorCode));
    }
}
