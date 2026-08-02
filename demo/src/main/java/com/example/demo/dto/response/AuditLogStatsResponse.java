package com.example.demo.dto.response;

import java.time.LocalDate;

public record AuditLogStatsResponse(long totalRecords, Long branchId, LocalDate fromDate, LocalDate toDate) {
}
