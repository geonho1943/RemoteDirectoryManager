package com.example.fileserver.filesystem.path;

import java.nio.file.Path;

public interface PathResolver {

    Path resolveUnderRoot(String relativePath);

    boolean exists(String relativePath);

    boolean isDirectory(String relativePath);

    boolean isRegularFile(String relativePath);
}
