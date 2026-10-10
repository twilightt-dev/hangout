package com.dianping.exception;

import org.springframework.http.HttpStatus;

public class VisitStatsException extends RuntimeException {
    private final HttpStatus status;

    public VisitStatsException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() { return status; }
}
