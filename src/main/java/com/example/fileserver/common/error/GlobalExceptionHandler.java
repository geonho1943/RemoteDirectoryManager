package com.example.fileserver.common.error;

import com.example.fileserver.common.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // Spring MVC 요청 처리 예외를 원인에 맞는 공통 오류 응답으로 변환한다.
    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class,
            NoResourceFoundException.class,
            HttpRequestMethodNotSupportedException.class,
            MaxUploadSizeExceededException.class
    })
    public ResponseEntity<ErrorResponse> handleFrameworkException(Exception exception, HttpServletRequest request) {
        ErrorCode errorCode = switch (exception) {
            case NoResourceFoundException ignored -> ErrorCode.RESOURCE_NOT_FOUND;
            case HttpRequestMethodNotSupportedException ignored -> ErrorCode.METHOD_NOT_ALLOWED;
            case MaxUploadSizeExceededException ignored -> ErrorCode.PAYLOAD_TOO_LARGE;
            default -> ErrorCode.INVALID_REQUEST;
        };
        return buildErrorResponse(errorCode, errorCode.defaultMessage(), request);
    }

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
        log.error("Unexpected error while handling {} {}", request.getMethod(), request.getRequestURI(), exception);
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
