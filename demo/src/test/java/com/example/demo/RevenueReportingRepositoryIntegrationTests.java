package com.example.demo;

import com.example.demo.model.*;
import com.example.demo.model.enums.*;
import com.example.demo.repository.*;
import com.example.demo.repository.projection.RevenueAggregateProjection;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RevenueReportingRepositoryIntegrationTests {
    @Autowired BranchRepository branchRepository;
    @Autowired OrderRepository orderRepository;
    @Autowired PaymentTransactionRepository paymentTransactionRepository;
    @Autowired UserRepository userRepository;
    @Autowired RoleRepository roleRepository;

    @Test
    void aggregateUsesCompletedTransactionsCurrentBranchAndExclusiveEndBoundary() {
        Branch branchA = branchRepository.save(branch("Revenue A"));
        Branch branchB = branchRepository.save(branch("Revenue B"));
        Order orderA = order(branchA);
        Order orderB = order(branchB);
        User actor = user(branchA);
        LocalDateTime start = LocalDateTime.of(2026, 7, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 7, 2, 0, 0);
        transaction(orderA, branchA, actor, PaymentTransactionType.PAYMENT, PaymentTransactionStatus.COMPLETED, "120000", start);
        transaction(orderA, branchA, actor, PaymentTransactionType.PAYMENT, PaymentTransactionStatus.COMPLETED, "80000", start.plusHours(2));
        transaction(orderA, branchA, actor, PaymentTransactionType.REFUND, PaymentTransactionStatus.COMPLETED, "50000", start.plusHours(3));
        transaction(orderA, branchA, actor, PaymentTransactionType.PAYMENT, PaymentTransactionStatus.PENDING, "90000", start.plusHours(4));
        transaction(orderA, branchA, actor, PaymentTransactionType.PAYMENT, PaymentTransactionStatus.FAILED, "70000", start.plusHours(5));
        transaction(orderA, branchA, actor, PaymentTransactionType.PAYMENT, PaymentTransactionStatus.COMPLETED, "60000", end);
        transaction(orderB, branchB, user(branchB), PaymentTransactionType.PAYMENT, PaymentTransactionStatus.COMPLETED, "90000", start);

        RevenueAggregateProjection aggregate = paymentTransactionRepository.aggregateRevenueByBranchAndCompletedAt(branchA.getId(), start, end, PaymentTransactionStatus.COMPLETED);
        assertEquals(new BigDecimal("200000"), aggregate.getGrossSales());
        assertEquals(new BigDecimal("50000"), aggregate.getRefundTotal());
        assertEquals(1L, aggregate.getPaidOrderCount());
    }

    private Branch branch(String name) { Branch branch = new Branch(); branch.setName(name); branch.setAddress(name); branch.setPhone("0"); branch.setStatus("ACTIVE"); return branch; }
    private Order order(Branch branch) { Order order = new Order(); order.setBranch(branch); order.setCustomerName("Customer"); order.setSubtotal(BigDecimal.ONE); order.setTotalAmount(BigDecimal.ONE); order.setStatus(OrderStatus.PENDING); order.setPaymentStatus(PaymentStatus.UNPAID); order.setPaymentMethod(PaymentMethod.CASH); order.setOrderType(OrderType.DINE_IN); return orderRepository.save(order); }
    private User user(Branch branch) { Role role = roleRepository.findByName("ROLE_ADMIN").orElseGet(() -> roleRepository.save(new Role(null, "ROLE_ADMIN"))); User user = new User(); user.setUsername("revenue-" + branch.getName()); user.setPassword("password"); user.setFullName("Revenue"); user.setRole(role); user.setBranch(branch); user.setEnabled(true); return userRepository.save(user); }
    private void transaction(Order order, Branch branch, User actor, PaymentTransactionType type, PaymentTransactionStatus status, String amount, LocalDateTime completedAt) { PaymentTransaction transaction = new PaymentTransaction(); transaction.setOrder(order); transaction.setBranch(branch); transaction.setTransactionType(type); transaction.setPaymentMethod(PaymentMethod.CASH); transaction.setAmount(new BigDecimal(amount)); transaction.setStatus(status); transaction.setCreatedAt(completedAt); transaction.setCompletedAt(completedAt); transaction.setCreatedBy(actor); paymentTransactionRepository.save(transaction); }
}
