package com.example.fileserver.common.error;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidMoveTargetException extends RuntimeException {

    public InvalidMoveTargetException(String message) {
        super(message);
    }

    public InvalidMoveTargetException(String message, Throwable cause) {
        super(message, cause);
    }
}
