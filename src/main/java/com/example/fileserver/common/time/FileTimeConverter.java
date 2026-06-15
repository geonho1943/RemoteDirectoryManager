package com.example.fileserver.common.time;

import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.time.ZoneId;

public final class FileTimeConverter {

    // 인스턴스 생성을 막는 유틸리티 클래스 생성자다.
    private FileTimeConverter() {
    }

    // 파일시스템 시간을 시스템 기본 시간대의 LocalDateTime으로 변환한다.
    public static LocalDateTime toLocalDateTime(FileTime fileTime) {
        if (fileTime == null) {
            return null;
        }

        return LocalDateTime.ofInstant(fileTime.toInstant(), ZoneId.systemDefault());
    }
}
