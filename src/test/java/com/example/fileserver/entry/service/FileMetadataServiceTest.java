package com.example.fileserver.entry.service;

import com.example.fileserver.entry.repository.FileEntryRepository;
import com.example.fileserver.filesystem.path.PathNormalizer;
import com.example.fileserver.filesystem.path.PathResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FileMetadataServiceTest {

    @TempDir
    Path storageRoot;

    @Mock
    FileEntryRepository fileEntryRepository;

    @Test
    void passesNormalizedPathWhenDeactivatingDescendants() {
        PathNormalizer pathNormalizer = new PathNormalizer();
        FileMetadataService service = new FileMetadataService(
                pathNormalizer,
                new PathResolver(storageRoot, pathNormalizer),
                fileEntryRepository
        );

        service.deactivateByPathOrDescendant("/report_100%!done");

        verify(fileEntryRepository).deactivateByFilePathOrDescendant("/report_100%!done");
    }
}
