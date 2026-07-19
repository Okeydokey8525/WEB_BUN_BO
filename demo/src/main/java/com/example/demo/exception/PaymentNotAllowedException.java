package com.example.demo.exception;

public class PaymentNotAllowedException extends BusinessValidationException {
    public PaymentNotAllowedException(String message) {
        super(message);
    }
}
