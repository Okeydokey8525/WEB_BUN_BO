package com.example.demo.exception;

public class ShiftNotOpenException extends BusinessValidationException {
    public ShiftNotOpenException() { super("Không có ca làm việc đang mở."); }
}
