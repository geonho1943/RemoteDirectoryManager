package com.example.fileserver.common.error;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidEntryNameException extends RuntimeException {

    public InvalidEntryNameException(String message) {
        super(message);
    }

    public InvalidEntryNameException(String message, Throwable cause) {
        super(message, cause);
    }
}
