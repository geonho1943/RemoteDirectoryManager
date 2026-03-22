package com.example.fileserver.entry.service;

import com.example.fileserver.entry.dto.DirectoryListResponse;
import com.example.fileserver.entry.dto.FileEntryDetailResponse;

public interface FileQueryService {

    DirectoryListResponse listEntries(String path, boolean includeHidden);

    FileEntryDetailResponse getEntryDetail(String path);
}
