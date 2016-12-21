package com.github.mike10004.gaetesting;

public class GCloudExecutionException extends RuntimeException {
    public GCloudExecutionException() {
    }

    public GCloudExecutionException(String message) {
        super(message);
    }

    public GCloudExecutionException(String message, Throwable cause) {
        super(message, cause);
    }

    public GCloudExecutionException(Throwable cause) {
        super(cause);
    }
}
