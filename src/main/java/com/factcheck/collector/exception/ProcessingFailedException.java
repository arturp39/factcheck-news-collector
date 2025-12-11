package com.factcheck.collector.exception;

public class ProcessingFailedException extends RuntimeException {
    public ProcessingFailedException(Throwable cause) {
        super(cause);
    }
}
