package com.example.fileserver.entry.service;

import com.example.fileserver.entry.dto.AssignTagsRequest;
import com.example.fileserver.entry.dto.FileTagsResponse;
import com.example.fileserver.entry.dto.RemoveTagsRequest;
import com.example.fileserver.entry.dto.TagListResponse;

public interface FileTagService {

    TagListResponse listTags();

    FileTagsResponse assignTags(AssignTagsRequest request);

    FileTagsResponse removeTags(RemoveTagsRequest request);
}
