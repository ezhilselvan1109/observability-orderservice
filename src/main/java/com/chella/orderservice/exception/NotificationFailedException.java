package com.chella.orderservice.exception;

public class NotificationFailedException extends RuntimeException {
    public NotificationFailedException(String message) {
        super(message);
    }
}
