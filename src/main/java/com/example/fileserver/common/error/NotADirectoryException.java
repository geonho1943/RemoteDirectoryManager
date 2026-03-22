package com.example.fileserver.common.error;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class NotADirectoryException extends RuntimeException {

    public NotADirectoryException(String message) {
        super(message);
    }

    public NotADirectoryException(String message, Throwable cause) {
        super(message, cause);
    }
}
