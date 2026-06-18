package com.example.fileserver.entry.controller;

import com.example.fileserver.entry.service.FileCommandService;
import com.example.fileserver.entry.service.FileQueryService;
import com.example.fileserver.entry.service.FileTagService;
import com.example.fileserver.filesystem.path.PathNormalizer;
import com.example.fileserver.filesystem.path.PathResolver;
import com.example.fileserver.transfer.service.FileTransferService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FileStreamingControllerTest {

    @TempDir
    Path storageRoot;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        PathNormalizer pathNormalizer = new PathNormalizer();
        FileTransferService transferService = new FileTransferService(
                pathNormalizer,
                new PathResolver(storageRoot, pathNormalizer)
        );
        EntryController controller = new EntryController(
                mock(FileQueryService.class),
                mock(FileCommandService.class),
                mock(FileTagService.class),
                transferService
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void servesSingleByteRangeAsPartialContent() throws Exception {
        Files.writeString(storageRoot.resolve("sample.txt"), "stream-content");

        mockMvc.perform(get("/api/v1/files/stream")
                        .param("path", "/sample.txt")
                        .header("Range", "bytes=0-3"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string("Content-Range", "bytes 0-3/14"))
                .andExpect(content().bytes("stre".getBytes()));
    }

    @Test
    void servesWholeFileWithoutRangeHeader() throws Exception {
        Files.writeString(storageRoot.resolve("sample.txt"), "stream-content");

        mockMvc.perform(get("/api/v1/files/stream")
                        .param("path", "/sample.txt"))
                .andExpect(status().isOk())
                .andExpect(content().bytes("stream-content".getBytes()));
    }
}
