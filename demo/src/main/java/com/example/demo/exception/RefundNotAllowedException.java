package com.example.demo.exception;

public class RefundNotAllowedException extends BusinessValidationException {
    public RefundNotAllowedException(String message) {
        super(message);
    }
}
