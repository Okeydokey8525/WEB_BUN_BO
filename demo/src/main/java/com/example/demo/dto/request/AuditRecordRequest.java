package com.example.demo.dto.request;

import com.example.demo.model.Branch;
import com.example.demo.model.User;
import com.example.demo.model.enums.AuditAction;
import com.example.demo.model.enums.AuditEntityType;

import java.util.Map;

public record AuditRecordRequest(
        AuditAction action,
        AuditEntityType entityType,
        Long entityId,
        Branch branch,
        User actor,
        String description,
        Map<String, Object> metadata
) {
}
