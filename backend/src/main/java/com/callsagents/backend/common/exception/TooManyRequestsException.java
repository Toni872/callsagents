package com.callsagents.backend.common.exception;

/**
 * Thrown when a client exceeds a rate limit (HTTP 429).
 */
public class TooManyRequestsException extends RuntimeException {

    public TooManyRequestsException(String message) {
        super(message);
    }
}