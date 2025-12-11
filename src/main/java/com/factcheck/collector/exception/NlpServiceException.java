package com.factcheck.collector.exception;

public class NlpServiceException extends RuntimeException {

    public NlpServiceException(String message) {
        super(message);
    }

    public NlpServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}