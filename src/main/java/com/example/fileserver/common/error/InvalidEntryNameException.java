package com.example.fileserver.common.error;

public class InvalidEntryNameException extends RuntimeException {

    public InvalidEntryNameException(String message) {
        super(message);
    }

    public InvalidEntryNameException(String message, Throwable cause) {
        super(message, cause);
    }
}
