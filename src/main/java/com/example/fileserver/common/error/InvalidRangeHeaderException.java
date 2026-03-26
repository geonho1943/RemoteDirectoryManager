package com.example.fileserver.common.error;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
public class InvalidRangeHeaderException extends RuntimeException {

    public InvalidRangeHeaderException(String message) {
        super(message);
    }

    public InvalidRangeHeaderException(String message, Throwable cause) {
        super(message, cause);
    }
}
