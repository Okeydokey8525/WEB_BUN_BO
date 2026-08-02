package com.example.demo.dto.response;

import java.time.LocalDateTime;

public record AuditLogSummary(
        Long id,
        LocalDateTime timestamp,
        String username,
        String action,
        String entityType,
        Long entityId,
        Long branchId,
        String branchName,
        String description,
        String ipAddress,
        String metadata
) {
}
