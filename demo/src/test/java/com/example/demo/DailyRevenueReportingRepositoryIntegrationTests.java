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
import com.example.demo.repository.projection.DailyRevenueProjection;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DailyRevenueReportingRepositoryIntegrationTests {
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
    void aggregatesCompletedPaymentsAndRefundsPerDayInAscendingOrder() {
        Branch branchA = branchRepository.save(branch("Daily A"));
        Branch branchB = branchRepository.save(branch("Daily B"));
        User actorA = user(branchA);
        User actorB = user(branchB);
        Order firstOrder = order(branchA);
        Order secondOrder = order(branchA);
        Order otherBranchOrder = order(branchB);
        LocalDateTime dayOne = LocalDateTime.of(2026, 7, 1, 0, 0);
        LocalDateTime dayTwo = dayOne.plusDays(1);
        LocalDateTime dayThree = dayOne.plusDays(2);
        LocalDateTime toExclusive = dayOne.plusDays(3);

        transaction(firstOrder, branchA, actorA, PaymentTransactionType.PAYMENT,
                PaymentTransactionStatus.COMPLETED, "120000", dayOne);
        transaction(firstOrder, branchA, actorA, PaymentTransactionType.PAYMENT,
                PaymentTransactionStatus.COMPLETED, "80000", dayOne.plusHours(1));
        transaction(firstOrder, branchA, actorA, PaymentTransactionType.REFUND,
                PaymentTransactionStatus.COMPLETED, "20000", dayOne.plusHours(2));
        transaction(secondOrder, branchA, actorA, PaymentTransactionType.PAYMENT,
                PaymentTransactionStatus.COMPLETED, "150000", dayTwo.plusHours(3));
        transaction(secondOrder, branchA, actorA, PaymentTransactionType.REFUND,
                PaymentTransactionStatus.COMPLETED, "50000", dayThree.plusHours(4));
        transaction(firstOrder, branchA, actorA, PaymentTransactionType.PAYMENT,
                PaymentTransactionStatus.PENDING, "90000", dayOne.plusHours(5));
        transaction(secondOrder, branchA, actorA, PaymentTransactionType.PAYMENT,
                PaymentTransactionStatus.FAILED, "70000", dayTwo.plusHours(5));
        transaction(otherBranchOrder, branchB, actorB, PaymentTransactionType.PAYMENT,
                PaymentTransactionStatus.COMPLETED, "60000", dayOne.plusHours(6));
        transaction(firstOrder, branchA, actorA, PaymentTransactionType.PAYMENT,
                PaymentTransactionStatus.COMPLETED, "60000", toExclusive);

        List<DailyRevenueProjection> results = paymentTransactionRepository
                .aggregateDailyRevenueByBranchAndCompletedAt(branchA.getId(), dayOne, toExclusive);

        assertEquals(3, results.size());
        assertDay(results.get(0), LocalDate.of(2026, 7, 1), "200000", "20000", 1L);
        assertDay(results.get(1), LocalDate.of(2026, 7, 2), "150000", "0", 1L);
        assertDay(results.get(2), LocalDate.of(2026, 7, 3), "0", "50000", 0L);
    }

    private void assertDay(DailyRevenueProjection result, LocalDate date, String gross, String refund, Long orders) {
        assertEquals(date, result.getRevenueDate());
        assertEquals(new BigDecimal(gross), result.getGrossSales());
        assertEquals(new BigDecimal(refund), result.getRefundTotal());
        assertEquals(orders, result.getPaidOrderCount());
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
        user.setUsername("daily-" + branch.getName());
        user.setPassword("password");
        user.setFullName("Daily");
        user.setRole(role);
        user.setBranch(branch);
        user.setEnabled(true);
        return userRepository.save(user);
    }

    private void transaction(Order order, Branch branch, User actor, PaymentTransactionType type,
                             PaymentTransactionStatus status, String amount, LocalDateTime completedAt) {
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setOrder(order);
        transaction.setBranch(branch);
        transaction.setTransactionType(type);
        transaction.setPaymentMethod(PaymentMethod.CASH);
        transaction.setAmount(new BigDecimal(amount));
        transaction.setStatus(status);
        transaction.setCreatedAt(completedAt);
        transaction.setCompletedAt(completedAt);
        transaction.setCreatedBy(actor);
        paymentTransactionRepository.save(transaction);
    }
}
