package com.example.fileserver.common.error;

import com.example.fileserver.common.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // 도메인 API 예외를 에러 코드에 맞는 HTTP 응답으로 변환한다.
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException exception, HttpServletRequest request) {
        ErrorCode errorCode = exception.errorCode();
        return buildErrorResponse(
                errorCode,
                resolveMessage(exception, errorCode.defaultMessage()),
                request
        );
    }

    // 예상하지 못한 예외를 공통 500 에러 응답으로 변환한다.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(
            Exception exception,
            HttpServletRequest request
    ) {
        return buildErrorResponse(
                ErrorCode.INTERNAL_SERVER_ERROR,
                "Internal server error.",
                request
        );
    }

    // 공통 에러 응답 본문과 HTTP 상태를 조립한다.
    private ResponseEntity<ErrorResponse> buildErrorResponse(
            ErrorCode errorCode,
            String message,
            HttpServletRequest request
    ) {
        ErrorResponse errorResponse = new ErrorResponse(
                errorCode.name(),
                message,
                request.getRequestURI(),
                LocalDateTime.now()
        );

        return ResponseEntity.status(errorCode.status()).body(errorResponse);
    }

    // 예외 메시지가 비어 있으면 에러 코드의 기본 메시지를 사용한다.
    private String resolveMessage(RuntimeException exception, String defaultMessage) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return defaultMessage;
        }

        return message;
    }
}
