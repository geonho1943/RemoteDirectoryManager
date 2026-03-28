package com.example.fileserver.common.error;

import com.example.fileserver.common.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidPathException.class)
    public ResponseEntity<ErrorResponse> handleInvalidPath(
            InvalidPathException exception,
            HttpServletRequest request
    ) {
        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                ErrorCode.INVALID_PATH,
                resolveMessage(exception, "Invalid path."),
                request
        );
    }

    @ExceptionHandler({InvalidEntryNameException.class, InvalidFileUploadException.class})
    public ResponseEntity<ErrorResponse> handleInvalidEntryName(
            RuntimeException exception,
            HttpServletRequest request
    ) {
        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                ErrorCode.INVALID_ENTRY_NAME,
                resolveMessage(exception, "Invalid entry name."),
                request
        );
    }

    @ExceptionHandler(EntryNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleEntryNotFound(
            EntryNotFoundException exception,
            HttpServletRequest request
    ) {
        return buildErrorResponse(
                HttpStatus.NOT_FOUND,
                ErrorCode.ENTRY_NOT_FOUND,
                resolveMessage(exception, "Entry not found."),
                request
        );
    }

    @ExceptionHandler(EntryAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleEntryAlreadyExists(
            EntryAlreadyExistsException exception,
            HttpServletRequest request
    ) {
        return buildErrorResponse(
                HttpStatus.CONFLICT,
                ErrorCode.ENTRY_ALREADY_EXISTS,
                resolveMessage(exception, "Entry already exists."),
                request
        );
    }

    @ExceptionHandler(NotADirectoryException.class)
    public ResponseEntity<ErrorResponse> handleNotADirectory(
            NotADirectoryException exception,
            HttpServletRequest request
    ) {
        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                ErrorCode.NOT_A_DIRECTORY,
                resolveMessage(exception, "Not a directory."),
                request
        );
    }

    @ExceptionHandler(NotAFileException.class)
    public ResponseEntity<ErrorResponse> handleNotAFile(
            NotAFileException exception,
            HttpServletRequest request
    ) {
        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                ErrorCode.NOT_A_FILE,
                resolveMessage(exception, "Not a file."),
                request
        );
    }

    @ExceptionHandler(InvalidMoveTargetException.class)
    public ResponseEntity<ErrorResponse> handleInvalidMoveTarget(
            InvalidMoveTargetException exception,
            HttpServletRequest request
    ) {
        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                ErrorCode.INVALID_MOVE_TARGET,
                resolveMessage(exception, "Invalid move target."),
                request
        );
    }

    @ExceptionHandler(InvalidRangeHeaderException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRangeHeader(
            InvalidRangeHeaderException exception,
            HttpServletRequest request
    ) {
        return buildErrorResponse(
                HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE,
                ErrorCode.INVALID_RANGE_HEADER,
                resolveMessage(exception, "Invalid Range header."),
                request
        );
    }

    @ExceptionHandler(UnauthorizedApiKeyException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorizedApiKey(
            UnauthorizedApiKeyException exception,
            HttpServletRequest request
    ) {
        return buildErrorResponse(
                HttpStatus.UNAUTHORIZED,
                ErrorCode.UNAUTHORIZED_API_KEY,
                resolveMessage(exception, "Invalid API key."),
                request
        );
    }

    @ExceptionHandler(FileOperationException.class)
    public ResponseEntity<ErrorResponse> handleFileOperationFailed(
            FileOperationException exception,
            HttpServletRequest request
    ) {
        return buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ErrorCode.FILE_OPERATION_FAILED,
                resolveMessage(exception, "File operation failed."),
                request
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(
            Exception exception,
            HttpServletRequest request
    ) {
        return buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ErrorCode.INTERNAL_SERVER_ERROR,
                "Internal server error.",
                request
        );
    }

    private ResponseEntity<ErrorResponse> buildErrorResponse(
            HttpStatus status,
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

        return ResponseEntity.status(status).body(errorResponse);
    }

    private String resolveMessage(RuntimeException exception, String defaultMessage) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return defaultMessage;
        }

        return message;
    }
}
