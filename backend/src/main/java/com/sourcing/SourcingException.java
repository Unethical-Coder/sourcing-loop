package com.sourcing;

public class SourcingException extends RuntimeException {

    private final int status;

    public SourcingException(String message, int status) {
        super(message);
        this.status = status;
    }

    public int status() {
        return status;
    }
}