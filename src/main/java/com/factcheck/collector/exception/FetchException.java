package com.factcheck.collector.exception;

public class FetchException extends RuntimeException {
    public FetchException(String message, Throwable cause) {
        super(message, cause);
    }
    public FetchException(String message) {
        super(message);
    }
}
