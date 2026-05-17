package com.bankaudi.baw.document.api;

public class DocumentGenerationException extends Exception {
    private final String errorCode;

    public DocumentGenerationException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public DocumentGenerationException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
