package com.example.demo.dto.request;

import com.example.demo.model.enums.AuditAction;

import java.time.LocalDate;

public record AuditLogFilter(
        LocalDate fromDate,
        LocalDate toDate,
        AuditAction action,
        String username
) {
}
