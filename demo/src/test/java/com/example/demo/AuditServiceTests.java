package com.example.demo;

import com.example.demo.dto.request.AuditLogFilter;
import com.example.demo.dto.response.AuditLogSummary;
import com.example.demo.exception.BranchAccessDeniedException;
import com.example.demo.exception.BusinessValidationException;
import com.example.demo.model.ActivityLog;
import com.example.demo.model.Branch;
import com.example.demo.model.Role;
import com.example.demo.model.User;
import com.example.demo.model.enums.AuditAction;
import com.example.demo.model.enums.AuditEntityType;
import com.example.demo.repository.ActivityLogRepository;
import com.example.demo.security.BranchAccessService;
import com.example.demo.security.CurrentUserService;
import com.example.demo.service.AuditService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditServiceTests {
    @Mock private ActivityLogRepository activityLogRepository;
    @Mock private CurrentUserService currentUserService;
    @Mock private BranchAccessService branchAccessService;
    @Mock private ObjectMapper objectMapper;
    @InjectMocks private AuditService auditService;

    @AfterEach void clearRequest() { RequestContextHolder.resetRequestAttributes(); }

    @Test
    void recordsActorBranchActionMetadataAndForwardedIp() {
        Branch branch = branch(); User actor = user(branch, "ROLE_CASHIER");
        when(currentUserService.getCurrentUser()).thenReturn(actor);
        when(currentUserService.requireCurrentBranch()).thenReturn(branch);
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"orderId\":123}");
        MockHttpServletRequest request = new MockHttpServletRequest(); request.addHeader("X-Forwarded-For", "203.0.113.7, proxy");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        auditService.record(AuditAction.PAY_ORDER, AuditEntityType.PAYMENT_TRANSACTION, 99L,
                "Payment completed", Map.of("orderId", 123, "token", "never-save"));

        ArgumentCaptor<ActivityLog> captor = ArgumentCaptor.forClass(ActivityLog.class);
        verify(activityLogRepository).save(captor.capture());
        ActivityLog saved = captor.getValue();
        assertEquals("PAY_ORDER", saved.getAction()); assertEquals("PAYMENT_TRANSACTION", saved.getEntityType());
        assertEquals(99L, saved.getEntityId()); assertEquals(branch, saved.getBranch());
        assertEquals("cashier", saved.getUsername()); assertEquals("203.0.113.7", saved.getIpAddress());
        assertEquals("{\"orderId\":123}", saved.getMetadata()); assertNotNull(saved.getTimestamp());
        verify(objectMapper).writeValueAsString(argThat(value -> value instanceof Map<?, ?> map && !map.containsKey("token")));
    }

    @Test
    void fallsBackToRemoteAddressAndSystemWithoutRequest() {
        Branch branch = branch(); User actor = user(branch, "ROLE_ADMIN");
        when(currentUserService.getCurrentUser()).thenReturn(actor); when(currentUserService.requireCurrentBranch()).thenReturn(branch);
        MockHttpServletRequest request = new MockHttpServletRequest(); request.setRemoteAddr("127.0.0.1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        auditService.record(AuditAction.SHIFT_OPEN, AuditEntityType.WORK_SHIFT, 1L, "open", null);
        RequestContextHolder.resetRequestAttributes();
        auditService.record(AuditAction.SHIFT_CLOSE, AuditEntityType.WORK_SHIFT, 1L, "close", null);
        ArgumentCaptor<ActivityLog> captor = ArgumentCaptor.forClass(ActivityLog.class);
        verify(activityLogRepository, times(2)).save(captor.capture());
        assertEquals("127.0.0.1", captor.getAllValues().get(0).getIpAddress());
        assertEquals("SYSTEM", captor.getAllValues().get(1).getIpAddress());
        assertNull(captor.getAllValues().get(0).getMetadata());
    }

    @Test
    void rejectsInvalidBranchConsistencyWithoutSaving() {
        Branch a = branch(); Branch b = new Branch(); b.setId(20L); User actor = user(a, "ROLE_ADMIN");
        assertThrows(BranchAccessDeniedException.class, () -> auditService.record(AuditAction.SHIFT_OPEN,
                AuditEntityType.WORK_SHIFT, 1L, b, actor, "open", Map.of()));
        assertThrows(BusinessValidationException.class, () -> auditService.record(null,
                AuditEntityType.WORK_SHIFT, 1L, b, actor, "open", Map.of()));
        verify(activityLogRepository, never()).save(any());
    }

    @Test
    void readsOnlyScopedAdminBranchWithFilters() {
        Branch branch = branch(); User admin = user(branch, "ROLE_ADMIN");
        when(currentUserService.getCurrentUser()).thenReturn(admin); when(branchAccessService.requireScopedBranchId()).thenReturn(10L);
        ActivityLog log = new ActivityLog(); log.setId(8L); log.setBranch(branch); log.setUsername("cashier");
        log.setAction("PAY_ORDER"); log.setEntityType("PAYMENT_TRANSACTION"); log.setTimestamp(LocalDateTime.now());
        when(activityLogRepository.findBranchAuditLogs(eq(10L), eq("PAY_ORDER"), eq("cashier"), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(log)));

        List<AuditLogSummary> results = auditService.getBranchAuditLogs(
                new AuditLogFilter(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 2), AuditAction.PAY_ORDER, "cashier"),
                PageRequest.of(0, 10)).getContent();

        assertEquals(1, results.size()); assertEquals(10L, results.get(0).branchId());
        verify(activityLogRepository).findBranchAuditLogs(eq(10L), eq("PAY_ORDER"), eq("cashier"), any(), any(), any());
    }

    @Test
    void deniesAuditReadsToNonAdminBeforeRepositoryAccess() {
        User cashier = user(branch(), "ROLE_CASHIER"); when(currentUserService.getCurrentUser()).thenReturn(cashier);
        assertThrows(BranchAccessDeniedException.class, () -> auditService.getBranchAuditLogs(null, PageRequest.of(0, 10)));
        verifyNoInteractions(activityLogRepository);
    }

    private Branch branch() { Branch branch = new Branch(); branch.setId(10L); branch.setName("A"); return branch; }
    private User user(Branch branch, String roleName) { Role role = new Role(); role.setName(roleName); User user = new User(); user.setUsername("cashier"); user.setBranch(branch); user.setRole(role); return user; }
}
