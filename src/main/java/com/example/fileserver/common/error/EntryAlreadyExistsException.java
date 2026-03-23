package com.example.fileserver.common.error;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class EntryAlreadyExistsException extends RuntimeException {

    public EntryAlreadyExistsException(String message) {
        super(message);
    }

    public EntryAlreadyExistsException(String message, Throwable cause) {
        super(message, cause);
    }
}
