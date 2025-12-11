package com.factcheck.collector.exception;

public class WeaviateException extends RuntimeException {
    public WeaviateException(String message, Throwable cause) {
        super(message, cause);
    }
}
