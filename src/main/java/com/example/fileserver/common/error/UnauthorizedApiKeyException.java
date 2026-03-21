package com.example.fileserver.common.error;

public class UnauthorizedApiKeyException extends RuntimeException {

    public UnauthorizedApiKeyException(String message) {
        super(message);
    }

    public UnauthorizedApiKeyException(String message, Throwable cause) {
        super(message, cause);
    }
}
