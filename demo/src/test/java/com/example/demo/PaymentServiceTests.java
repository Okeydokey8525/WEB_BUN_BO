package com.example.demo;

import com.example.demo.dto.request.PayOrderRequest;
import com.example.demo.dto.request.RefundOrderRequest;
import com.example.demo.dto.response.PaymentResult;
import com.example.demo.exception.OrderAlreadyPaidException;
import com.example.demo.exception.InvalidPaymentAmountException;
import com.example.demo.model.Branch;
import com.example.demo.model.Order;
import com.example.demo.model.PaymentTransaction;
import com.example.demo.model.Role;
import com.example.demo.model.User;
import com.example.demo.model.WorkShift;
import com.example.demo.model.enums.PaymentMethod;
import com.example.demo.model.enums.PaymentStatus;
import com.example.demo.model.enums.ShiftStatus;
import com.example.demo.repository.OrderRepository;
import com.example.demo.repository.PaymentTransactionRepository;
import com.example.demo.repository.WorkShiftRepository;
import com.example.demo.security.BranchAccessService;
import com.example.demo.security.CurrentUserService;
import com.example.demo.service.PaymentService;
import com.example.demo.service.AuditService;
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
class PaymentServiceTests {
    @Mock OrderRepository orderRepository;
    @Mock PaymentTransactionRepository paymentTransactionRepository;
    @Mock WorkShiftRepository workShiftRepository;
    @Mock CurrentUserService currentUserService;
    @Mock BranchAccessService branchAccessService;
    @Mock AuditService auditService;
    @InjectMocks PaymentService paymentService;

    @Test
    void cashPaymentUsesOrderTotalAndCalculatesChange() {
        Order order = order(100_000);
        User cashier = cashier();
        when(branchAccessService.requireScopedBranchId()).thenReturn(1L);
        when(orderRepository.findByIdAndBranchId(7L, 1L)).thenReturn(Optional.of(order));
        when(paymentTransactionRepository.existsByOrderIdAndBranchIdAndTransactionTypeAndStatus(anyLong(), anyLong(), any(), any())).thenReturn(false);
        WorkShift openShift = configureOpenShift(cashier, order.getBranch());
        when(paymentTransactionRepository.save(any(PaymentTransaction.class))).thenAnswer(i -> { PaymentTransaction tx = i.getArgument(0); tx.setId(11L); return tx; });

        PaymentResult result = paymentService.payOrder(new PayOrderRequest(7L, PaymentMethod.CASH, new BigDecimal("120000"), null, null));

        assertEquals(new BigDecimal("100000"), result.amount());
        assertEquals(new BigDecimal("20000"), result.changeAmount());
        assertEquals(PaymentStatus.PAID, order.getPaymentStatus());
        ArgumentCaptor<PaymentTransaction> captor = ArgumentCaptor.forClass(PaymentTransaction.class);
        verify(paymentTransactionRepository).save(captor.capture());
        assertEquals(new BigDecimal("100000"), captor.getValue().getAmount());
        assertSame(openShift, captor.getValue().getWorkShift());
        verify(auditService).record(eq(com.example.demo.model.enums.AuditAction.PAY_ORDER), eq(com.example.demo.model.enums.AuditEntityType.PAYMENT_TRANSACTION), eq(11L), eq(order.getBranch()), eq(cashier), anyString(), argThat(metadata -> metadata.get("orderId").equals(7L) && metadata.get("shiftId").equals(openShift.getId()) && metadata.get("amount").equals(new BigDecimal("100000"))));
    }

    @Test
    void alreadyPaidOrderIsRejectedBeforeCreatingTransaction() {
        Order order = order(100_000); order.setPaymentStatus(PaymentStatus.PAID);
        when(branchAccessService.requireScopedBranchId()).thenReturn(1L);
        when(orderRepository.findByIdAndBranchId(7L, 1L)).thenReturn(Optional.of(order));
        configureOpenShift(cashier(), order.getBranch());

        assertThrows(OrderAlreadyPaidException.class, () -> paymentService.payOrder(new PayOrderRequest(7L, PaymentMethod.CASH, new BigDecimal("100000"), null, null)));
        verifyNoInteractions(paymentTransactionRepository);
    }

    @Test
    void cashTenderedBelowTotalIsRejected() {
        Order order = order(100_000);
        when(branchAccessService.requireScopedBranchId()).thenReturn(1L);
        when(orderRepository.findByIdAndBranchId(7L, 1L)).thenReturn(Optional.of(order));
        when(paymentTransactionRepository.existsByOrderIdAndBranchIdAndTransactionTypeAndStatus(anyLong(), anyLong(), any(), any())).thenReturn(false);
        configureOpenShift(cashier(), order.getBranch());

        assertThrows(InvalidPaymentAmountException.class,
                () -> paymentService.payOrder(new PayOrderRequest(7L, PaymentMethod.CASH, new BigDecimal("99999"), null, null)));
        assertEquals(PaymentStatus.UNPAID, order.getPaymentStatus());
        verify(paymentTransactionRepository, never()).save(any());
    }

    @Test
    void paidOrderCanBeRefundedOnce() {
        Order order = order(100_000); order.setPaymentStatus(PaymentStatus.PAID);
        User cashier = cashier();
        when(branchAccessService.requireScopedBranchId()).thenReturn(1L);
        when(orderRepository.findByIdAndBranchId(7L, 1L)).thenReturn(Optional.of(order));
        when(paymentTransactionRepository.sumAmountByOrderIdAndBranchIdAndTypeAndStatus(anyLong(), anyLong(), any(), any()))
                .thenReturn(new BigDecimal("100000"), BigDecimal.ZERO);
        WorkShift openShift = configureOpenShift(cashier, order.getBranch());
        when(paymentTransactionRepository.save(any(PaymentTransaction.class))).thenAnswer(i -> { PaymentTransaction tx = i.getArgument(0); tx.setId(12L); return tx; });

        PaymentResult result = paymentService.refundOrder(new RefundOrderRequest(7L, "Customer request"));

        assertEquals(new BigDecimal("100000"), result.amount());
        assertEquals(PaymentStatus.REFUNDED, order.getPaymentStatus());
        ArgumentCaptor<PaymentTransaction> captor = ArgumentCaptor.forClass(PaymentTransaction.class);
        verify(paymentTransactionRepository).save(captor.capture());
        assertSame(openShift, captor.getValue().getWorkShift());
    }

    @Test
    void unpaidOrderCannotBeRefunded() {
        Order order = order(100_000);
        when(branchAccessService.requireScopedBranchId()).thenReturn(1L);
        when(orderRepository.findByIdAndBranchId(7L, 1L)).thenReturn(Optional.of(order));

        assertThrows(com.example.demo.exception.RefundNotAllowedException.class,
                () -> paymentService.refundOrder(new RefundOrderRequest(7L, "Customer request")));
    }

    @Test
    void cashierWithoutOpenShiftCannotPayAndOrderStaysUnpaid() {
        Order order = order(100_000);
        when(branchAccessService.requireScopedBranchId()).thenReturn(1L);
        when(orderRepository.findByIdAndBranchId(7L, 1L)).thenReturn(Optional.of(order));
        User cashier = cashier();
        when(currentUserService.getCurrentUser()).thenReturn(cashier);
        when(currentUserService.requireCurrentBranch()).thenReturn(order.getBranch());
        when(workShiftRepository.findOpenShiftForCashierForUpdate(9L, ShiftStatus.OPEN)).thenReturn(Optional.empty());

        assertThrows(com.example.demo.exception.ShiftNotOpenException.class,
                () -> paymentService.payOrder(new PayOrderRequest(7L, PaymentMethod.CASH, new BigDecimal("100000"), null, null)));
        assertEquals(PaymentStatus.UNPAID, order.getPaymentStatus());
        verify(paymentTransactionRepository, never()).save(any());
        verify(orderRepository, never()).saveAndFlush(any());
    }

    @Test
    void adminWithoutOpenShiftCannotPay() {
        Order order = order(100_000);
        when(branchAccessService.requireScopedBranchId()).thenReturn(1L);
        when(orderRepository.findByIdAndBranchId(7L, 1L)).thenReturn(Optional.of(order));
        User admin = cashier(); admin.getRole().setName("ROLE_ADMIN");
        when(currentUserService.getCurrentUser()).thenReturn(admin);
        when(currentUserService.requireCurrentBranch()).thenReturn(order.getBranch());
        when(workShiftRepository.findOpenShiftForCashierForUpdate(9L, ShiftStatus.OPEN)).thenReturn(Optional.empty());

        assertThrows(com.example.demo.exception.ShiftNotOpenException.class,
                () -> paymentService.payOrder(new PayOrderRequest(7L, PaymentMethod.CASH, new BigDecimal("100000"), null, null)));
        verify(paymentTransactionRepository, never()).save(any());
    }

    @Test
    void adminWithOwnOpenShiftCanPay() {
        Order order = order(100_000);
        when(branchAccessService.requireScopedBranchId()).thenReturn(1L);
        when(orderRepository.findByIdAndBranchId(7L, 1L)).thenReturn(Optional.of(order));
        when(paymentTransactionRepository.existsByOrderIdAndBranchIdAndTransactionTypeAndStatus(anyLong(), anyLong(), any(), any())).thenReturn(false);
        User admin = cashier(); admin.getRole().setName("ROLE_ADMIN");
        configureOpenShift(admin, order.getBranch());
        when(paymentTransactionRepository.save(any(PaymentTransaction.class))).thenAnswer(i -> {
            PaymentTransaction transaction = i.getArgument(0); transaction.setId(19L); return transaction;
        });

        paymentService.payOrder(new PayOrderRequest(7L, PaymentMethod.CASH, new BigDecimal("100000"), null, null));
        assertEquals(PaymentStatus.PAID, order.getPaymentStatus());
    }

    @Test
    void crossBranchOrClosedShiftCannotBeUsedForPayment() {
        Order order = order(100_000);
        when(branchAccessService.requireScopedBranchId()).thenReturn(1L);
        when(orderRepository.findByIdAndBranchId(7L, 1L)).thenReturn(Optional.of(order));
        User cashier = cashier();
        Branch otherBranch = new Branch(); otherBranch.setId(2L);
        WorkShift wrongBranch = openShift(cashier, otherBranch, ShiftStatus.OPEN);
        when(currentUserService.getCurrentUser()).thenReturn(cashier);
        when(currentUserService.requireCurrentBranch()).thenReturn(order.getBranch());
        when(workShiftRepository.findOpenShiftForCashierForUpdate(9L, ShiftStatus.OPEN)).thenReturn(Optional.of(wrongBranch));
        assertThrows(com.example.demo.exception.ShiftAccessDeniedException.class,
                () -> paymentService.payOrder(new PayOrderRequest(7L, PaymentMethod.CASH, new BigDecimal("100000"), null, null)));
        WorkShift closed = openShift(cashier, order.getBranch(), ShiftStatus.CLOSED);
        when(workShiftRepository.findOpenShiftForCashierForUpdate(9L, ShiftStatus.OPEN)).thenReturn(Optional.of(closed));
        assertThrows(com.example.demo.exception.ShiftNotOpenException.class,
                () -> paymentService.payOrder(new PayOrderRequest(7L, PaymentMethod.CASH, new BigDecimal("100000"), null, null)));
        verify(paymentTransactionRepository, never()).save(any());
    }

    @Test
    void refundWithoutOpenShiftCannotChangeOrder() {
        Order order = order(100_000); order.setPaymentStatus(PaymentStatus.PAID);
        when(branchAccessService.requireScopedBranchId()).thenReturn(1L);
        when(orderRepository.findByIdAndBranchId(7L, 1L)).thenReturn(Optional.of(order));
        User cashier = cashier();
        when(currentUserService.getCurrentUser()).thenReturn(cashier);
        when(currentUserService.requireCurrentBranch()).thenReturn(order.getBranch());
        when(workShiftRepository.findOpenShiftForCashierForUpdate(9L, ShiftStatus.OPEN)).thenReturn(Optional.empty());

        assertThrows(com.example.demo.exception.ShiftNotOpenException.class,
                () -> paymentService.refundOrder(new RefundOrderRequest(7L, "Customer request")));
        assertEquals(PaymentStatus.PAID, order.getPaymentStatus());
        verify(paymentTransactionRepository, never()).save(any());
    }

    @Test
    void refundCannotUseOpenShiftFromAnotherBranch() {
        Order order = order(100_000); order.setPaymentStatus(PaymentStatus.PAID);
        when(branchAccessService.requireScopedBranchId()).thenReturn(1L);
        when(orderRepository.findByIdAndBranchId(7L, 1L)).thenReturn(Optional.of(order));
        User cashier = cashier();
        Branch otherBranch = new Branch(); otherBranch.setId(2L);
        when(currentUserService.getCurrentUser()).thenReturn(cashier);
        when(currentUserService.requireCurrentBranch()).thenReturn(order.getBranch());
        when(workShiftRepository.findOpenShiftForCashierForUpdate(9L, ShiftStatus.OPEN))
                .thenReturn(Optional.of(openShift(cashier, otherBranch, ShiftStatus.OPEN)));

        assertThrows(com.example.demo.exception.ShiftAccessDeniedException.class,
                () -> paymentService.refundOrder(new RefundOrderRequest(7L, "Customer request")));
        assertEquals(PaymentStatus.PAID, order.getPaymentStatus());
        verify(paymentTransactionRepository, never()).save(any());
    }

    private Order order(long total) {
        Branch branch = new Branch(); branch.setId(1L);
        Order order = new Order(); order.setId(7L); order.setBranch(branch); order.setTotalAmount(BigDecimal.valueOf(total));
        order.setPaymentStatus(PaymentStatus.UNPAID);
        return order;
    }

    private User cashier() {
        Role role = new Role(); role.setName("ROLE_CASHIER");
        User cashier = new User(); cashier.setId(9L); cashier.setRole(role);
        return cashier;
    }

    private WorkShift configureOpenShift(User cashier, Branch branch) {
        cashier.setBranch(branch);
        WorkShift shift = openShift(cashier, branch, ShiftStatus.OPEN);
        when(currentUserService.getCurrentUser()).thenReturn(cashier);
        when(currentUserService.requireCurrentBranch()).thenReturn(branch);
        when(workShiftRepository.findOpenShiftForCashierForUpdate(cashier.getId(), ShiftStatus.OPEN)).thenReturn(Optional.of(shift));
        return shift;
    }

    private WorkShift openShift(User cashier, Branch branch, ShiftStatus status) {
        WorkShift shift = new WorkShift(); shift.setId(21L); shift.setCashier(cashier); shift.setBranch(branch); shift.setStatus(status);
        return shift;
    }
}
