package com.example.demo;

import com.example.demo.model.Branch;
import com.example.demo.model.Order;
import com.example.demo.model.PaymentTransaction;
import com.example.demo.model.Role;
import com.example.demo.model.User;
import com.example.demo.model.enums.OrderStatus;
import com.example.demo.model.enums.OrderType;
import com.example.demo.model.enums.PaymentMethod;
import com.example.demo.model.enums.PaymentStatus;
import com.example.demo.model.enums.PaymentTransactionStatus;
import com.example.demo.model.enums.PaymentTransactionType;
import com.example.demo.repository.BranchRepository;
import com.example.demo.repository.OrderRepository;
import com.example.demo.repository.PaymentTransactionRepository;
import com.example.demo.repository.RoleRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.projection.PaymentMethodAggregateProjection;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PaymentMethodReportingRepositoryIntegrationTests {
    @Autowired
    private BranchRepository branchRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private PaymentTransactionRepository paymentTransactionRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;

    @Test
    void aggregatesCompletedTransactionsByMethodWithinBranchAndExclusiveTimeRange() {
        Branch branchA = branchRepository.save(branch("Method A"));
        Branch branchB = branchRepository.save(branch("Method B"));
        User actorA = user(branchA);
        User actorB = user(branchB);
        Order cashOrder = order(branchA);
        Order qrOrder = order(branchA);
        Order otherBranchOrder = order(branchB);
        LocalDateTime from = LocalDateTime.of(2026, 7, 1, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 7, 2, 0, 0);

        transaction(cashOrder, branchA, actorA, PaymentMethod.CASH, PaymentTransactionType.PAYMENT,
                PaymentTransactionStatus.COMPLETED, "120000", from);
        transaction(cashOrder, branchA, actorA, PaymentMethod.CASH, PaymentTransactionType.PAYMENT,
                PaymentTransactionStatus.COMPLETED, "80000", from.plusHours(1));
        transaction(cashOrder, branchA, actorA, PaymentMethod.CASH, PaymentTransactionType.REFUND,
                PaymentTransactionStatus.COMPLETED, "50000", from.plusHours(2));
        transaction(qrOrder, branchA, actorA, PaymentMethod.VIETQR, PaymentTransactionType.PAYMENT,
                PaymentTransactionStatus.COMPLETED, "150000", from.plusHours(3));
        transaction(qrOrder, branchA, actorA, PaymentMethod.VIETQR, PaymentTransactionType.REFUND,
                PaymentTransactionStatus.COMPLETED, "20000", from.plusHours(4));
        transaction(cashOrder, branchA, actorA, PaymentMethod.CASH, PaymentTransactionType.PAYMENT,
                PaymentTransactionStatus.PENDING, "90000", from.plusHours(5));
        transaction(qrOrder, branchA, actorA, PaymentMethod.VIETQR, PaymentTransactionType.PAYMENT,
                PaymentTransactionStatus.FAILED, "70000", from.plusHours(6));
        transaction(otherBranchOrder, branchB, actorB, PaymentMethod.CASH, PaymentTransactionType.PAYMENT,
                PaymentTransactionStatus.COMPLETED, "60000", from.plusHours(7));
        transaction(cashOrder, branchA, actorA, PaymentMethod.CASH, PaymentTransactionType.PAYMENT,
                PaymentTransactionStatus.COMPLETED, "60000", to);

        Map<PaymentMethod, PaymentMethodAggregateProjection> byMethod = paymentTransactionRepository
                .aggregateByPaymentMethodAndBranchAndCompletedAt(branchA.getId(), from, to)
                .stream()
                .collect(Collectors.toMap(PaymentMethodAggregateProjection::getPaymentMethod, aggregate -> aggregate));

        assertEquals(2, byMethod.size());
        PaymentMethodAggregateProjection cash = byMethod.get(PaymentMethod.CASH);
        assertEquals(new BigDecimal("200000"), cash.getGrossAmount());
        assertEquals(new BigDecimal("50000"), cash.getRefundAmount());
        assertEquals(2L, cash.getPaymentTransactionCount());
        assertEquals(1L, cash.getRefundTransactionCount());
        assertEquals(1L, cash.getPaidOrderCount());

        PaymentMethodAggregateProjection vietQr = byMethod.get(PaymentMethod.VIETQR);
        assertEquals(new BigDecimal("150000"), vietQr.getGrossAmount());
        assertEquals(new BigDecimal("20000"), vietQr.getRefundAmount());
        assertEquals(1L, vietQr.getPaymentTransactionCount());
        assertEquals(1L, vietQr.getRefundTransactionCount());
        assertEquals(1L, vietQr.getPaidOrderCount());
        assertFalse(byMethod.containsKey(null));
    }

    private Branch branch(String name) {
        Branch branch = new Branch();
        branch.setName(name);
        branch.setAddress(name);
        branch.setPhone("0");
        branch.setStatus("ACTIVE");
        return branch;
    }

    private Order order(Branch branch) {
        Order order = new Order();
        order.setBranch(branch);
        order.setCustomerName("Customer");
        order.setSubtotal(BigDecimal.ONE);
        order.setTotalAmount(BigDecimal.ONE);
        order.setStatus(OrderStatus.PENDING);
        order.setPaymentStatus(PaymentStatus.UNPAID);
        order.setPaymentMethod(PaymentMethod.CASH);
        order.setOrderType(OrderType.DINE_IN);
        return orderRepository.save(order);
    }

    private User user(Branch branch) {
        Role role = roleRepository.findByName("ROLE_ADMIN")
                .orElseGet(() -> roleRepository.save(new Role(null, "ROLE_ADMIN")));
        User user = new User();
        user.setUsername("method-" + branch.getName());
        user.setPassword("password");
        user.setFullName("Method");
        user.setRole(role);
        user.setBranch(branch);
        user.setEnabled(true);
        return userRepository.save(user);
    }

    private void transaction(Order order, Branch branch, User actor, PaymentMethod method,
                             PaymentTransactionType type, PaymentTransactionStatus status,
                             String amount, LocalDateTime completedAt) {
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setOrder(order);
        transaction.setBranch(branch);
        transaction.setTransactionType(type);
        transaction.setPaymentMethod(method);
        transaction.setAmount(new BigDecimal(amount));
        transaction.setStatus(status);
        transaction.setCreatedAt(completedAt);
        transaction.setCompletedAt(completedAt);
        transaction.setCreatedBy(actor);
        paymentTransactionRepository.save(transaction);
    }
}
