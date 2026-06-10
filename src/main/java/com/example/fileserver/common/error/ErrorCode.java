package com.example.fileserver.common.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    INVALID_PATH(HttpStatus.BAD_REQUEST, "Invalid path."),
    INVALID_ENTRY_NAME(HttpStatus.BAD_REQUEST, "Invalid entry name."),
    ENTRY_NOT_FOUND(HttpStatus.NOT_FOUND, "Entry not found."),
    ENTRY_ALREADY_EXISTS(HttpStatus.CONFLICT, "Entry already exists."),
    NOT_A_DIRECTORY(HttpStatus.BAD_REQUEST, "Not a directory."),
    NOT_A_FILE(HttpStatus.BAD_REQUEST, "Not a file."),
    INVALID_RANGE_HEADER(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE, "Invalid Range header."),
    INVALID_TAG(HttpStatus.BAD_REQUEST, "Invalid tag."),
    METADATA_SYNC_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to synchronize file metadata."),
    TRANSACTION_SYNCHRONIZATION_UNAVAILABLE(HttpStatus.INTERNAL_SERVER_ERROR, "Transaction synchronization is not available."),
    FILE_OPERATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "File operation failed."),
    UNAUTHORIZED_API_KEY(HttpStatus.UNAUTHORIZED, "Invalid API key."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error.");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    // 이 에러 코드가 응답해야 할 HTTP 상태를 반환한다.
    public HttpStatus status() {
        return status;
    }

    // 예외 메시지가 없을 때 사용할 기본 메시지를 반환한다.
    public String defaultMessage() {
        return defaultMessage;
    }
}
