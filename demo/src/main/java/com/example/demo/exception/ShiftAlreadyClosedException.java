package com.example.demo.exception;

public class ShiftAlreadyClosedException extends BusinessValidationException {
    public ShiftAlreadyClosedException() { super("Ca làm việc đã được đóng."); }
}
