package com.example.stockbrokerage.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a service call is rejected by the rate limiter or circuit breaker.
 * Maps to HTTP 429 Too Many Requests.
 */
@ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
public class RequestThrottledException extends RuntimeException {
    public RequestThrottledException(String message) {
        super(message);
    }
}
