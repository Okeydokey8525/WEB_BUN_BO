package com.example.demo.exception;

public class ShiftAccessDeniedException extends BranchAccessDeniedException {
    public ShiftAccessDeniedException() { super("Không có quyền truy cập ca làm việc này."); }
}
