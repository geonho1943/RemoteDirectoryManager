package com.example.fileserver.common.error;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class NotAFileException extends RuntimeException {

    public NotAFileException(String message) {
        super(message);
    }

    public NotAFileException(String message, Throwable cause) {
        super(message, cause);
    }
}
