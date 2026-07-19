package com.example.demo;

import com.example.demo.dto.request.PayOrderRequest;
import com.example.demo.dto.response.PaymentResult;
import com.example.demo.exception.OrderAlreadyPaidException;
import com.example.demo.model.Branch;
import com.example.demo.model.Order;
import com.example.demo.model.PaymentTransaction;
import com.example.demo.model.User;
import com.example.demo.model.enums.PaymentMethod;
import com.example.demo.model.enums.PaymentStatus;
import com.example.demo.repository.OrderRepository;
import com.example.demo.repository.PaymentTransactionRepository;
import com.example.demo.security.BranchAccessService;
import com.example.demo.security.CurrentUserService;
import com.example.demo.service.PaymentService;
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
    @Mock CurrentUserService currentUserService;
    @Mock BranchAccessService branchAccessService;
    @InjectMocks PaymentService paymentService;

    @Test
    void cashPaymentUsesOrderTotalAndCalculatesChange() {
        Order order = order(100_000);
        User cashier = new User(); cashier.setId(9L);
        when(branchAccessService.requireScopedBranchId()).thenReturn(1L);
        when(orderRepository.findByIdAndBranchId(7L, 1L)).thenReturn(Optional.of(order));
        when(paymentTransactionRepository.existsByOrderIdAndBranchIdAndTransactionTypeAndStatus(anyLong(), anyLong(), any(), any())).thenReturn(false);
        when(currentUserService.getCurrentUser()).thenReturn(cashier);
        when(paymentTransactionRepository.save(any(PaymentTransaction.class))).thenAnswer(i -> { PaymentTransaction tx = i.getArgument(0); tx.setId(11L); return tx; });

        PaymentResult result = paymentService.payOrder(new PayOrderRequest(7L, PaymentMethod.CASH, new BigDecimal("120000"), null, null));

        assertEquals(new BigDecimal("100000"), result.amount());
        assertEquals(new BigDecimal("20000"), result.changeAmount());
        assertEquals(PaymentStatus.PAID, order.getPaymentStatus());
        ArgumentCaptor<PaymentTransaction> captor = ArgumentCaptor.forClass(PaymentTransaction.class);
        verify(paymentTransactionRepository).save(captor.capture());
        assertEquals(new BigDecimal("100000"), captor.getValue().getAmount());
    }

    @Test
    void alreadyPaidOrderIsRejectedBeforeCreatingTransaction() {
        Order order = order(100_000); order.setPaymentStatus(PaymentStatus.PAID);
        when(branchAccessService.requireScopedBranchId()).thenReturn(1L);
        when(orderRepository.findByIdAndBranchId(7L, 1L)).thenReturn(Optional.of(order));

        assertThrows(OrderAlreadyPaidException.class, () -> paymentService.payOrder(new PayOrderRequest(7L, PaymentMethod.CASH, new BigDecimal("100000"), null, null)));
        verifyNoInteractions(paymentTransactionRepository);
    }

    private Order order(long total) {
        Branch branch = new Branch(); branch.setId(1L);
        Order order = new Order(); order.setId(7L); order.setBranch(branch); order.setTotalAmount(BigDecimal.valueOf(total));
        order.setPaymentStatus(PaymentStatus.UNPAID);
        return order;
    }
}
