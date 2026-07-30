package com.example.demo.controller;

import com.example.demo.dto.request.AuditLogFilter;
import com.example.demo.dto.response.AuditLogStatsResponse;
import com.example.demo.dto.response.AuditLogSummary;
import com.example.demo.model.enums.AuditAction;
import com.example.demo.security.BranchAccessService;
import com.example.demo.service.AuditService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/admin/audit-logs")
public class AuditLogController {
    private static final int MAX_PAGE_SIZE = 100;
    private final AuditService auditService;
    private final BranchAccessService branchAccessService;

    public AuditLogController(AuditService auditService, BranchAccessService branchAccessService) {
        this.auditService = auditService;
        this.branchAccessService = branchAccessService;
    }

    @GetMapping
    public Page<AuditLogSummary> list(@RequestParam(required = false) Long branchId,
                                      @RequestParam(required = false) AuditAction action,
                                      @RequestParam(required = false) String username,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "20") int size) {
        Long scopedBranch = branchAccessService.requireScopedBranchId();
        if (branchId != null && !branchId.equals(scopedBranch)) {
            throw new com.example.demo.exception.BranchAccessDeniedException("Khong duoc xem audit cua chi nhanh khac.");
        }
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new com.example.demo.exception.BusinessValidationException("Phan trang audit khong hop le.");
        }
        return auditService.getBranchAuditLogs(new AuditLogFilter(from, to, action, username), PageRequest.of(page, size));
    }

    @GetMapping("/summary")
    public AuditLogStatsResponse summary(@RequestParam(required = false) Long branchId,
                                         @RequestParam(required = false) AuditAction action,
                                         @RequestParam(required = false) String username,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        Long scopedBranch = branchAccessService.requireScopedBranchId();
        if (branchId != null && !branchId.equals(scopedBranch)) throw new com.example.demo.exception.BranchAccessDeniedException("Khong duoc xem audit cua chi nhanh khac.");
        Pageable page = PageRequest.of(0, 1);
        long total = auditService.getBranchAuditLogs(new AuditLogFilter(from, to, action, username), page).getTotalElements();
        return new AuditLogStatsResponse(total, scopedBranch, from, to);
    }
}
