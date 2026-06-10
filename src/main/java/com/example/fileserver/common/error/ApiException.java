package com.example.fileserver.common.error;

public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;

    // 원인 예외가 없는 API 예외를 생성한다.
    public ApiException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    // 원인 예외를 보존하는 API 예외를 생성한다.
    public ApiException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    // 예외를 HTTP 응답 코드와 기본 메시지로 매핑할 에러 코드를 반환한다.
    public ErrorCode errorCode() {
        return errorCode;
    }
}
