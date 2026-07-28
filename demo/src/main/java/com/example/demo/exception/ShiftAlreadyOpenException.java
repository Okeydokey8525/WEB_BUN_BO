package com.example.demo.exception;

public class ShiftAlreadyOpenException extends BusinessValidationException {
    public ShiftAlreadyOpenException() {
        super("Nhân viên đã có ca làm việc đang mở.");
    }
}
