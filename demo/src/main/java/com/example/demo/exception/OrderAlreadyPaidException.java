package com.example.demo.exception;

public class OrderAlreadyPaidException extends BusinessValidationException {
    public OrderAlreadyPaidException() {
        super("Đơn hàng đã được thanh toán.");
    }
}
