package com.example.demo;

import com.example.demo.dto.request.OpenShiftRequest;
import com.example.demo.dto.response.OpenShiftResult;
import com.example.demo.exception.BranchAccessDeniedException;
import com.example.demo.exception.BusinessValidationException;
import com.example.demo.exception.ShiftAlreadyOpenException;
import com.example.demo.model.*;
import com.example.demo.model.enums.ShiftStatus;
import com.example.demo.repository.PaymentTransactionRepository;
import com.example.demo.repository.WorkShiftRepository;
import com.example.demo.security.BranchAccessService;
import com.example.demo.security.CurrentUserService;
import com.example.demo.service.ShiftService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShiftServiceTests {
    @Mock WorkShiftRepository workShiftRepository;
    @Mock PaymentTransactionRepository paymentTransactionRepository;
    @Mock CurrentUserService currentUserService;
    @Mock BranchAccessService branchAccessService;
    @InjectMocks ShiftService shiftService;

    private Branch branch;
    private User cashier;

    @BeforeEach
    void setUp() {
        branch = new Branch(); branch.setId(1L); branch.setName("Branch A");
        cashier = user("ROLE_CASHIER");
        lenient().when(currentUserService.getCurrentUser()).thenReturn(cashier);
        lenient().when(currentUserService.requireCurrentBranch()).thenReturn(branch);
        lenient().when(workShiftRepository.findOpenShiftForCashierForUpdate(cashier.getId(), ShiftStatus.OPEN)).thenReturn(Optional.empty());
        lenient().when(workShiftRepository.save(any(WorkShift.class))).thenAnswer(invocation -> {
            WorkShift shift = invocation.getArgument(0); shift.setId(100L); return shift;
        });
    }

    @Test void opensShiftWithCurrentUserBranchAndSnapshots() {
        OpenShiftResult result = shiftService.openShift(new OpenShiftRequest(new BigDecimal("500000"), "Morning"));
        ArgumentCaptor<WorkShift> captor = ArgumentCaptor.forClass(WorkShift.class);
        verify(workShiftRepository).save(captor.capture());
        WorkShift saved = captor.getValue();
        assertEquals(ShiftStatus.OPEN, saved.getStatus()); assertSame(branch, saved.getBranch());
        assertSame(cashier, saved.getCashier()); assertSame(cashier, saved.getOpenedBy());
        assertNotNull(saved.getOpenedAt()); assertEquals(new BigDecimal("500000"), saved.getOpeningCash()); assertEquals("Morning", saved.getNote());
        assertEquals(100L, result.shift().shiftId()); assertEquals(1L, result.shift().branchId()); assertEquals(cashier.getUsername(), result.shift().cashierUsername());
        assertSnapshotsZero(saved);
    }

    @Test void zeroOpeningCashIsAllowed() { shiftService.openShift(new OpenShiftRequest(BigDecimal.ZERO, null)); verify(workShiftRepository).save(any()); }
    @Test void negativeOpeningCashIsRejected() { assertThrows(BusinessValidationException.class, () -> shiftService.openShift(new OpenShiftRequest(new BigDecimal("-1000"), null))); verify(workShiftRepository, never()).save(any()); }
    @Test void nullRequestIsRejected() { assertThrows(BusinessValidationException.class, () -> shiftService.openShift(null)); verify(workShiftRepository, never()).save(any()); }
    @Test void nullOpeningCashIsRejected() { assertThrows(BusinessValidationException.class, () -> shiftService.openShift(new OpenShiftRequest(null, null))); verify(workShiftRepository, never()).save(any()); }

    @Test void existingOpenShiftIsRejected() {
        when(workShiftRepository.findOpenShiftForCashierForUpdate(cashier.getId(), ShiftStatus.OPEN)).thenReturn(Optional.of(new WorkShift()));
        assertThrows(ShiftAlreadyOpenException.class, () -> shiftService.openShift(new OpenShiftRequest(BigDecimal.ZERO, null)));
        verify(workShiftRepository, never()).save(any());
    }

    @Test void userWithoutBranchIsRejected() {
        when(currentUserService.requireCurrentBranch()).thenThrow(new BranchAccessDeniedException("No branch"));
        assertThrows(BranchAccessDeniedException.class, () -> shiftService.openShift(new OpenShiftRequest(BigDecimal.ZERO, null)));
        verify(workShiftRepository, never()).save(any());
    }

    @Test void userRoleIsRejected() { assertRoleRejected("ROLE_USER"); }
    @Test void kitchenRoleIsRejected() { assertRoleRejected("ROLE_KITCHEN"); }
    @Test void adminCanOpenShift() { cashier = user("ROLE_ADMIN"); when(currentUserService.getCurrentUser()).thenReturn(cashier); when(workShiftRepository.findOpenShiftForCashierForUpdate(2L, ShiftStatus.OPEN)).thenReturn(Optional.empty()); shiftService.openShift(new OpenShiftRequest(BigDecimal.ZERO, null)); verify(workShiftRepository).save(any()); }

    private void assertRoleRejected(String roleName) { cashier = user(roleName); when(currentUserService.getCurrentUser()).thenReturn(cashier); assertThrows(BusinessValidationException.class, () -> shiftService.openShift(new OpenShiftRequest(BigDecimal.ZERO, null))); verify(workShiftRepository, never()).save(any()); }
    private User user(String roleName) { Role role = new Role(); role.setName(roleName); User user = new User(); user.setId(2L); user.setUsername("cashier-a"); user.setRole(role); user.setBranch(branch); return user; }
    private void assertSnapshotsZero(WorkShift shift) { assertEquals(BigDecimal.ZERO, shift.getExpectedCash()); assertEquals(BigDecimal.ZERO, shift.getActualCash()); assertEquals(BigDecimal.ZERO, shift.getCashDifference()); assertEquals(BigDecimal.ZERO, shift.getTotalSales()); assertEquals(BigDecimal.ZERO, shift.getCashSales()); assertEquals(BigDecimal.ZERO, shift.getTransferSales()); assertEquals(BigDecimal.ZERO, shift.getCardSales()); assertEquals(BigDecimal.ZERO, shift.getRefundTotal()); assertEquals(0, shift.getOrderCount()); }
}
