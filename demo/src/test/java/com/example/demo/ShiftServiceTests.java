package com.example.demo;

import com.example.demo.dto.request.OpenShiftRequest;
import com.example.demo.dto.request.CloseShiftRequest;
import com.example.demo.dto.response.CloseShiftResult;
import com.example.demo.dto.response.OpenShiftResult;
import com.example.demo.dto.response.ShiftSummary;
import com.example.demo.exception.BranchAccessDeniedException;
import com.example.demo.exception.BusinessValidationException;
import com.example.demo.exception.ShiftAlreadyOpenException;
import com.example.demo.exception.ShiftAccessDeniedException;
import com.example.demo.exception.ShiftNotOpenException;
import com.example.demo.exception.ShiftAlreadyClosedException;
import com.example.demo.model.*;
import com.example.demo.model.enums.ShiftStatus;
import com.example.demo.repository.PaymentTransactionRepository;
import com.example.demo.repository.WorkShiftRepository;
import com.example.demo.repository.projection.ShiftPaymentAggregate;
import com.example.demo.security.BranchAccessService;
import com.example.demo.security.CurrentUserService;
import com.example.demo.service.ShiftService;
import com.example.demo.service.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.List;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShiftServiceTests {
    @Mock WorkShiftRepository workShiftRepository;
    @Mock PaymentTransactionRepository paymentTransactionRepository;
    @Mock CurrentUserService currentUserService;
    @Mock BranchAccessService branchAccessService;
    @Mock AuditService auditService;
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
    @Test void successfulOpenWritesStructuredAudit() { shiftService.openShift(new OpenShiftRequest(new BigDecimal("500000"), null)); verify(auditService).record(eq(com.example.demo.model.enums.AuditAction.SHIFT_OPEN), eq(com.example.demo.model.enums.AuditEntityType.WORK_SHIFT), eq(100L), eq(branch), eq(cashier), anyString(), argThat(metadata -> metadata.get("openingCash").equals(new BigDecimal("500000")) && metadata.get("branchId").equals(1L))); }
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

    @Test void currentShiftReturnsOnlyCurrentCashierOpenShiftAndMapsAllFields() {
        WorkShift shift = shift(10L, cashier, branch, ShiftStatus.OPEN); shift.setClosedAt(LocalDateTime.now()); shift.setNote("note");
        when(workShiftRepository.findByCashierIdAndStatus(2L, ShiftStatus.OPEN)).thenReturn(Optional.of(shift));
        ShiftSummary summary = shiftService.getCurrentShift();
        assertEquals(10L, summary.shiftId()); assertEquals(ShiftStatus.OPEN, summary.status()); assertEquals(1L, summary.branchId()); assertEquals("Branch A", summary.branchName()); assertEquals(2L, summary.cashierId()); assertEquals("cashier-a", summary.cashierUsername()); assertEquals(new BigDecimal("500000"), summary.openingCash()); assertEquals(BigDecimal.ZERO, summary.expectedCash()); assertEquals(BigDecimal.ZERO, summary.refundTotal()); assertEquals(0, summary.orderCount()); assertNotNull(summary.openedAt()); assertNotNull(summary.closedAt());
        verify(workShiftRepository).findByCashierIdAndStatus(2L, ShiftStatus.OPEN);
    }
    @Test void currentShiftAbsentOrClosedIsRejected() { when(workShiftRepository.findByCashierIdAndStatus(2L, ShiftStatus.OPEN)).thenReturn(Optional.empty()); assertThrows(ShiftNotOpenException.class, () -> shiftService.getCurrentShift()); }
    @Test void currentShiftWithoutBranchIsRejected() { when(currentUserService.requireCurrentBranch()).thenThrow(new BranchAccessDeniedException("No branch")); assertThrows(BranchAccessDeniedException.class, () -> shiftService.getCurrentShift()); }
    @Test void cashierCanViewOwnShiftAndUsesBranchAwareQuery() { WorkShift shift = shift(11L, cashier, branch, ShiftStatus.OPEN); when(workShiftRepository.findByIdAndBranchId(11L, 1L)).thenReturn(Optional.of(shift)); assertEquals(11L, shiftService.getShift(11L).shiftId()); verify(workShiftRepository).findByIdAndBranchId(11L, 1L); }
    @Test void cashierCannotViewAnotherCashierShift() { User other = user("ROLE_CASHIER"); other.setId(3L); WorkShift shift = shift(12L, other, branch, ShiftStatus.OPEN); when(workShiftRepository.findByIdAndBranchId(12L, 1L)).thenReturn(Optional.of(shift)); assertThrows(ShiftAccessDeniedException.class, () -> shiftService.getShift(12L)); }
    @Test void adminCanViewSameBranchCashierShift() { cashier = user("ROLE_ADMIN"); when(currentUserService.getCurrentUser()).thenReturn(cashier); User other = user("ROLE_CASHIER"); other.setId(3L); WorkShift shift = shift(13L, other, branch, ShiftStatus.CLOSED); when(workShiftRepository.findByIdAndBranchId(13L, 1L)).thenReturn(Optional.of(shift)); assertEquals(13L, shiftService.getShift(13L).shiftId()); }
    @Test void crossBranchAndMissingShiftAreNotFoundThroughScopedQuery() { when(workShiftRepository.findByIdAndBranchId(14L, 1L)).thenReturn(Optional.empty()); assertThrows(com.example.demo.exception.ResourceNotFoundException.class, () -> shiftService.getShift(14L)); verify(workShiftRepository).findByIdAndBranchId(14L, 1L); }
    @Test void nullShiftIdIsRejectedWithoutRepositoryCall() { assertThrows(BusinessValidationException.class, () -> shiftService.getShift(null)); verify(workShiftRepository, never()).findByIdAndBranchId(any(), any()); }
    @Test void getShiftWithoutBranchIsRejected() { when(currentUserService.requireCurrentBranch()).thenThrow(new BranchAccessDeniedException("No branch")); assertThrows(BranchAccessDeniedException.class, () -> shiftService.getShift(16L)); verify(workShiftRepository, never()).findByIdAndBranchId(any(), any()); }
    @Test void userAndKitchenRolesCannotViewShift() { assertLookupRoleRejected("ROLE_USER"); assertLookupRoleRejected("ROLE_KITCHEN"); }

    @Test void closeValidationRejectsInvalidRequestsWithoutSave() { assertThrows(BusinessValidationException.class, () -> shiftService.closeShift(null)); assertThrows(BusinessValidationException.class, () -> shiftService.closeShift(new CloseShiftRequest(null, BigDecimal.ZERO, null))); assertThrows(BusinessValidationException.class, () -> shiftService.closeShift(new CloseShiftRequest(20L, null, null))); assertThrows(BusinessValidationException.class, () -> shiftService.closeShift(new CloseShiftRequest(20L, new BigDecimal("-1"), null))); verify(workShiftRepository, never()).save(any()); }
    @Test void cashierClosesOwnShiftAndReconciles() { WorkShift shift = shift(20L, cashier, branch, ShiftStatus.OPEN); ShiftPaymentAggregate a = aggregate("150000", "50000", "100000", "20000", "50000", "30000", "0", "0", 2L); when(workShiftRepository.findForUpdateByIdAndBranchId(20L, 1L)).thenReturn(Optional.of(shift)); when(paymentTransactionRepository.aggregateCompletedTransactionsByShiftId(20L)).thenReturn(a); when(workShiftRepository.save(any())).thenAnswer(i -> i.getArgument(0)); CloseShiftResult r = shiftService.closeShift(new CloseShiftRequest(20L, new BigDecimal("590000"), "close")); assertEquals(ShiftStatus.CLOSED, r.status()); assertEquals(new BigDecimal("580000"), r.expectedCash()); assertEquals(new BigDecimal("100000"), r.totalSales()); assertEquals(new BigDecimal("80000"), r.cashSales()); assertEquals(new BigDecimal("20000"), r.transferSales()); assertEquals(new BigDecimal("10000"), r.cashDifference()); assertEquals(2, r.orderCount()); assertSame(cashier, shift.getClosedBy()); assertNotNull(shift.getClosedAt()); verify(workShiftRepository).save(shift); }
    @Test void adminCanCloseSameBranchOtherCashierShift() { cashier = user("ROLE_ADMIN"); when(currentUserService.getCurrentUser()).thenReturn(cashier); User other = user("ROLE_CASHIER"); other.setId(3L); WorkShift shift = shift(21L, other, branch, ShiftStatus.OPEN); ShiftPaymentAggregate aggregate = zeroAggregate(); when(workShiftRepository.findForUpdateByIdAndBranchId(21L, 1L)).thenReturn(Optional.of(shift)); when(paymentTransactionRepository.aggregateCompletedTransactionsByShiftId(21L)).thenReturn(aggregate); when(workShiftRepository.save(any())).thenAnswer(i -> i.getArgument(0)); assertEquals(ShiftStatus.CLOSED, shiftService.closeShift(new CloseShiftRequest(21L, new BigDecimal("500000"), null)).status()); }
    @Test void cashierCannotCloseAnotherShiftAndCrossBranchDoesNotFallback() { User other = user("ROLE_CASHIER"); other.setId(3L); WorkShift shift = shift(22L, other, branch, ShiftStatus.OPEN); when(workShiftRepository.findForUpdateByIdAndBranchId(22L, 1L)).thenReturn(Optional.of(shift)); assertThrows(ShiftAccessDeniedException.class, () -> shiftService.closeShift(new CloseShiftRequest(22L, BigDecimal.ZERO, null))); when(workShiftRepository.findForUpdateByIdAndBranchId(23L, 1L)).thenReturn(Optional.empty()); assertThrows(com.example.demo.exception.ResourceNotFoundException.class, () -> shiftService.closeShift(new CloseShiftRequest(23L, BigDecimal.ZERO, null))); }
    @Test void closedOrCancelledShiftCannotCloseTwice() { WorkShift closed = shift(24L, cashier, branch, ShiftStatus.CLOSED); when(workShiftRepository.findForUpdateByIdAndBranchId(24L, 1L)).thenReturn(Optional.of(closed)); assertThrows(ShiftAlreadyClosedException.class, () -> shiftService.closeShift(new CloseShiftRequest(24L, BigDecimal.ZERO, null))); closed.setStatus(ShiftStatus.CANCELLED); assertThrows(ShiftNotOpenException.class, () -> shiftService.closeShift(new CloseShiftRequest(24L, BigDecimal.ZERO, null))); verifyNoInteractions(paymentTransactionRepository); }
    @Test void closeNormalizesNullAggregateValues() { WorkShift shift = shift(25L, cashier, branch, ShiftStatus.OPEN); ShiftPaymentAggregate a = mock(ShiftPaymentAggregate.class); when(workShiftRepository.findForUpdateByIdAndBranchId(25L, 1L)).thenReturn(Optional.of(shift)); when(paymentTransactionRepository.aggregateCompletedTransactionsByShiftId(25L)).thenReturn(a); when(workShiftRepository.save(any())).thenAnswer(i -> i.getArgument(0)); CloseShiftResult r = shiftService.closeShift(new CloseShiftRequest(25L, new BigDecimal("490000"), null)); assertEquals(new BigDecimal("500000"), r.expectedCash()); assertEquals(BigDecimal.ZERO, r.totalSales()); assertEquals(new BigDecimal("-10000"), r.cashDifference()); assertEquals(0, r.orderCount()); }
    @Test void adminSeesAllCurrentBranchShiftsInRepositoryOrder() { cashier = user("ROLE_ADMIN"); when(currentUserService.getCurrentUser()).thenReturn(cashier); WorkShift first = shift(30L, cashier, branch, ShiftStatus.OPEN); WorkShift second = shift(29L, user("ROLE_CASHIER"), branch, ShiftStatus.CLOSED); when(workShiftRepository.findByBranchIdOrderByOpenedAtDesc(1L)).thenReturn(List.of(first, second)); assertEquals(List.of(30L, 29L), shiftService.getBranchShifts().stream().map(ShiftSummary::shiftId).toList()); verify(workShiftRepository).findByBranchIdOrderByOpenedAtDesc(1L); verify(workShiftRepository, never()).findAll(); }
    @Test void cashierSeesOnlyOwnBranchHistory() { WorkShift own = shift(31L, cashier, branch, ShiftStatus.CLOSED); when(workShiftRepository.findByBranchIdAndCashierIdOrderByOpenedAtDesc(1L, 2L)).thenReturn(List.of(own)); assertEquals(List.of(31L), shiftService.getBranchShifts().stream().map(ShiftSummary::shiftId).toList()); verify(workShiftRepository).findByBranchIdAndCashierIdOrderByOpenedAtDesc(1L, 2L); }
    @Test void emptyHistoryReturnsEmptyListAndUnauthorizedRoleIsBlocked() { when(workShiftRepository.findByBranchIdAndCashierIdOrderByOpenedAtDesc(1L, 2L)).thenReturn(List.of()); assertTrue(shiftService.getBranchShifts().isEmpty()); cashier = user("ROLE_KITCHEN"); when(currentUserService.getCurrentUser()).thenReturn(cashier); assertThrows(ShiftAccessDeniedException.class, () -> shiftService.getBranchShifts()); }
    @Test void branchHistoryWithoutBranchIsRejectedBeforeRepositoryAccess() { when(currentUserService.requireCurrentBranch()).thenThrow(new BranchAccessDeniedException("No branch")); assertThrows(BranchAccessDeniedException.class, () -> shiftService.getBranchShifts()); verifyNoInteractions(workShiftRepository); }
    @Test void userRoleCannotViewBranchHistory() { cashier = user("ROLE_USER"); when(currentUserService.getCurrentUser()).thenReturn(cashier); assertThrows(ShiftAccessDeniedException.class, () -> shiftService.getBranchShifts()); verifyNoInteractions(workShiftRepository); }
    @Test void branchHistoryUsesSummaryMapperForOpenAndClosedShifts() { cashier = user("ROLE_ADMIN"); when(currentUserService.getCurrentUser()).thenReturn(cashier); WorkShift open = shift(32L, cashier, branch, ShiftStatus.OPEN); WorkShift closed = shift(33L, user("ROLE_CASHIER"), branch, ShiftStatus.CLOSED); closed.setClosedAt(LocalDateTime.now()); closed.setNote("Closed shift"); when(workShiftRepository.findByBranchIdOrderByOpenedAtDesc(1L)).thenReturn(List.of(open, closed)); List<ShiftSummary> summaries = shiftService.getBranchShifts(); assertEquals(ShiftStatus.OPEN, summaries.get(0).status()); assertEquals(ShiftStatus.CLOSED, summaries.get(1).status()); assertEquals("Branch A", summaries.get(1).branchName()); assertNotNull(summaries.get(1).closedAt()); assertEquals("Closed shift", summaries.get(1).note()); }

    private void assertRoleRejected(String roleName) { cashier = user(roleName); when(currentUserService.getCurrentUser()).thenReturn(cashier); assertThrows(BusinessValidationException.class, () -> shiftService.openShift(new OpenShiftRequest(BigDecimal.ZERO, null))); verify(workShiftRepository, never()).save(any()); }
    private User user(String roleName) { Role role = new Role(); role.setName(roleName); User user = new User(); user.setId(2L); user.setUsername("cashier-a"); user.setRole(role); user.setBranch(branch); return user; }
    private WorkShift shift(Long id, User owner, Branch branch, ShiftStatus status) { WorkShift shift = new WorkShift(); shift.setId(id); shift.setCashier(owner); shift.setOpenedBy(owner); shift.setBranch(branch); shift.setStatus(status); shift.setOpenedAt(LocalDateTime.now()); shift.setOpeningCash(new BigDecimal("500000")); shift.setExpectedCash(BigDecimal.ZERO); shift.setActualCash(BigDecimal.ZERO); shift.setCashDifference(BigDecimal.ZERO); shift.setTotalSales(BigDecimal.ZERO); shift.setCashSales(BigDecimal.ZERO); shift.setTransferSales(BigDecimal.ZERO); shift.setCardSales(BigDecimal.ZERO); shift.setRefundTotal(BigDecimal.ZERO); return shift; }
    private void assertLookupRoleRejected(String roleName) { cashier = user(roleName); when(currentUserService.getCurrentUser()).thenReturn(cashier); assertThrows(ShiftAccessDeniedException.class, () -> shiftService.getShift(15L)); }
    private void assertSnapshotsZero(WorkShift shift) { assertEquals(BigDecimal.ZERO, shift.getExpectedCash()); assertEquals(BigDecimal.ZERO, shift.getActualCash()); assertEquals(BigDecimal.ZERO, shift.getCashDifference()); assertEquals(BigDecimal.ZERO, shift.getTotalSales()); assertEquals(BigDecimal.ZERO, shift.getCashSales()); assertEquals(BigDecimal.ZERO, shift.getTransferSales()); assertEquals(BigDecimal.ZERO, shift.getCardSales()); assertEquals(BigDecimal.ZERO, shift.getRefundTotal()); assertEquals(0, shift.getOrderCount()); }
    private ShiftPaymentAggregate zeroAggregate() { return aggregate("0", "0", "0", "0", "0", "0", "0", "0", 0L); }
    private ShiftPaymentAggregate aggregate(String gross, String refund, String cashPay, String cashRefund, String transferPay, String transferRefund, String cardPay, String cardRefund, Long orders) { ShiftPaymentAggregate a = mock(ShiftPaymentAggregate.class); when(a.getGrossPaymentTotal()).thenReturn(new BigDecimal(gross)); when(a.getRefundTotal()).thenReturn(new BigDecimal(refund)); when(a.getCashPaymentTotal()).thenReturn(new BigDecimal(cashPay)); when(a.getCashRefundTotal()).thenReturn(new BigDecimal(cashRefund)); when(a.getTransferPaymentTotal()).thenReturn(new BigDecimal(transferPay)); when(a.getTransferRefundTotal()).thenReturn(new BigDecimal(transferRefund)); when(a.getCardPaymentTotal()).thenReturn(new BigDecimal(cardPay)); when(a.getCardRefundTotal()).thenReturn(new BigDecimal(cardRefund)); when(a.getDistinctOrderCount()).thenReturn(orders); return a; }
}
