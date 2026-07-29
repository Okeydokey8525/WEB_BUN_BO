package com.example.demo.service;

import com.example.demo.dto.request.AuditLogFilter;
import com.example.demo.dto.request.AuditRecordRequest;
import com.example.demo.dto.response.AuditLogSummary;
import com.example.demo.exception.BranchAccessDeniedException;
import com.example.demo.exception.BusinessValidationException;
import com.example.demo.model.ActivityLog;
import com.example.demo.model.Branch;
import com.example.demo.model.User;
import com.example.demo.model.enums.AuditAction;
import com.example.demo.model.enums.AuditEntityType;
import com.example.demo.repository.ActivityLogRepository;
import com.example.demo.security.BranchAccessService;
import com.example.demo.security.CurrentUserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@Transactional
public class AuditService {
    private static final int MAX_IP_LENGTH = 255;

    private final ActivityLogRepository activityLogRepository;
    private final CurrentUserService currentUserService;
    private final BranchAccessService branchAccessService;
    private final ObjectMapper objectMapper;

    public AuditService(ActivityLogRepository activityLogRepository,
                        CurrentUserService currentUserService,
                        BranchAccessService branchAccessService,
                        ObjectMapper objectMapper) {
        this.activityLogRepository = activityLogRepository;
        this.currentUserService = currentUserService;
        this.branchAccessService = branchAccessService;
        this.objectMapper = objectMapper;
    }

    public void record(AuditAction action, AuditEntityType entityType, Long entityId,
                       String description, Map<String, Object> metadata) {
        User actor = currentUserService.getCurrentUser();
        Branch branch = currentUserService.requireCurrentBranch();
        record(new AuditRecordRequest(action, entityType, entityId, branch, actor, description, metadata));
    }

    public void record(AuditAction action, AuditEntityType entityType, Long entityId,
                       Branch branch, User actor, String description, Map<String, Object> metadata) {
        record(new AuditRecordRequest(action, entityType, entityId, branch, actor, description, metadata));
    }

    public void record(AuditRecordRequest request) {
        validateRecord(request);
        ActivityLog log = new ActivityLog();
        log.setAction(request.action().name());
        log.setEntityType(request.entityType().name());
        log.setEntityId(request.entityId());
        log.setBranch(request.branch());
        log.setUsername(request.actor().getUsername());
        log.setDescription(request.description());
        log.setTimestamp(LocalDateTime.now());
        log.setIpAddress(resolveClientIp());
        log.setMetadata(serializeMetadata(request.metadata()));
        activityLogRepository.save(log);
    }

    @Transactional(readOnly = true)
    public Page<AuditLogSummary> getBranchAuditLogs(AuditLogFilter filter, Pageable pageable) {
        if (pageable == null) {
            throw new BusinessValidationException("Phan trang audit la bat buoc.");
        }
        validateFilter(filter);
        User actor = currentUserService.getCurrentUser();
        if (actor.getRole() == null || !"ROLE_ADMIN".equals(actor.getRole().getName())) {
            throw new BranchAccessDeniedException("Chi quan tri vien duoc xem lich su audit.");
        }
        Long branchId = branchAccessService.requireScopedBranchId();
        return activityLogRepository.findBranchAuditLogs(branchId,
                        filter == null || filter.action() == null ? null : filter.action().name(),
                        filter == null || blankToNull(filter.username()) == null ? null : filter.username().trim(),
                        filter == null || filter.fromDate() == null ? null : filter.fromDate().atStartOfDay(),
                        filter == null || filter.toDate() == null ? null : filter.toDate().plusDays(1).atStartOfDay(),
                        pageable)
                .map(this::toSummary);
    }

    private void validateRecord(AuditRecordRequest request) {
        if (request == null || request.action() == null || request.entityType() == null
                || request.branch() == null || request.branch().getId() == null
                || request.actor() == null || request.actor().getUsername() == null || request.actor().getUsername().isBlank()) {
            throw new BusinessValidationException("Thong tin audit khong hop le.");
        }
        if (request.actor().getBranch() == null || request.actor().getBranch().getId() == null
                || !request.branch().getId().equals(request.actor().getBranch().getId())) {
            throw new BranchAccessDeniedException("Nguoi dung audit phai thuoc cung chi nhanh.");
        }
    }

    private void validateFilter(AuditLogFilter filter) {
        if (filter != null && filter.fromDate() != null && filter.toDate() != null
                && filter.fromDate().isAfter(filter.toDate())) {
            throw new BusinessValidationException("Ngay bat dau khong duoc sau ngay ket thuc.");
        }
    }

    private String serializeMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        Map<String, Object> safe = new LinkedHashMap<>();
        metadata.forEach((key, value) -> {
            if (key != null && !isSensitiveKey(key)) {
                safe.put(key, value);
            }
        });
        try {
            return objectMapper.writeValueAsString(safe);
        } catch (RuntimeException exception) {
            return "{}";
        }
    }

    private boolean isSensitiveKey(String key) {
        String normalized = key.toLowerCase();
        return normalized.contains("password") || normalized.contains("token") || normalized.contains("csrf")
                || normalized.contains("cardnumber") || normalized.contains("bankqr") || normalized.contains("qrcode");
    }

    private String resolveClientIp() {
        ServletRequestAttributes attributes = RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes servlet
                ? servlet : null;
        if (attributes == null) {
            return "SYSTEM";
        }
        HttpServletRequest request = attributes.getRequest();
        String forwarded = blankToNull(request.getHeader("X-Forwarded-For"));
        String candidate = forwarded == null ? request.getRemoteAddr() : forwarded.split(",", 2)[0].trim();
        return candidate == null ? "SYSTEM" : candidate.substring(0, Math.min(candidate.length(), MAX_IP_LENGTH));
    }

    private AuditLogSummary toSummary(ActivityLog log) {
        Branch branch = log.getBranch();
        return new AuditLogSummary(log.getId(), log.getTimestamp(), log.getUsername(), log.getAction(),
                log.getEntityType(), log.getEntityId(), branch == null ? null : branch.getId(),
                branch == null ? null : branch.getName(), log.getDescription(), log.getIpAddress(), log.getMetadata());
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
