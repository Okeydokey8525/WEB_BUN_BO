package com.example.demo;

import com.example.demo.model.Branch;
import com.example.demo.model.Dish;
import com.example.demo.model.Order;
import com.example.demo.model.OrderItem;
import com.example.demo.model.PaymentTransaction;
import com.example.demo.model.Role;
import com.example.demo.model.User;
import com.example.demo.model.enums.OrderItemStatus;
import com.example.demo.model.enums.OrderStatus;
import com.example.demo.model.enums.OrderType;
import com.example.demo.model.enums.PaymentMethod;
import com.example.demo.model.enums.PaymentStatus;
import com.example.demo.model.enums.PaymentTransactionStatus;
import com.example.demo.model.enums.PaymentTransactionType;
import com.example.demo.repository.BranchRepository;
import com.example.demo.repository.DishRepository;
import com.example.demo.repository.OrderItemRepository;
import com.example.demo.repository.OrderRepository;
import com.example.demo.repository.PaymentTransactionRepository;
import com.example.demo.repository.RoleRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.projection.TopDishProjection;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TopDishReportingRepositoryIntegrationTests {
    @Autowired private BranchRepository branchRepository;
    @Autowired private DishRepository dishRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderItemRepository orderItemRepository;
    @Autowired private PaymentTransactionRepository paymentTransactionRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;

    @Test
    void aggregatesSnapshotItemsOncePerEligibleOrderAndAppliesLimit() {
        Branch branchA = branchRepository.save(branch("Top A"));
        Branch branchB = branchRepository.save(branch("Top B"));
        User actorA = user(branchA);
        User actorB = user(branchB);
        Dish bunBo = dish(branchA, "Bun bo", "50000");
        Dish traDa = dish(branchA, "Tra da", "5000");
        Dish pho = dish(branchA, "Pho", "45000");
        LocalDateTime from = LocalDateTime.of(2026, 7, 1, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 7, 2, 0, 0);

        Order firstOrder = order(branchA);
        item(firstOrder, bunBo, "Bun bo", 2, "50000", OrderItemStatus.SERVED);
        item(firstOrder, traDa, "Tra da", 1, "5000", OrderItemStatus.SERVED);
        payment(firstOrder, branchA, actorA, PaymentTransactionType.PAYMENT, PaymentTransactionStatus.COMPLETED, from);
        payment(firstOrder, branchA, actorA, PaymentTransactionType.PAYMENT, PaymentTransactionStatus.COMPLETED, from.plusMinutes(5));
        payment(firstOrder, branchA, actorA, PaymentTransactionType.REFUND, PaymentTransactionStatus.COMPLETED, from.plusMinutes(10));

        Order secondOrder = order(branchA);
        item(secondOrder, bunBo, "Bun bo", 3, "50000", OrderItemStatus.SERVED);
        payment(secondOrder, branchA, actorA, PaymentTransactionType.PAYMENT, PaymentTransactionStatus.COMPLETED, from.plusHours(1));

        Order thirdOrder = order(branchA);
        item(thirdOrder, pho, "Pho", 4, "45000", OrderItemStatus.SERVED);
        item(thirdOrder, traDa, "Tra da", 8, "5000", OrderItemStatus.CANCELLED);
        payment(thirdOrder, branchA, actorA, PaymentTransactionType.PAYMENT, PaymentTransactionStatus.COMPLETED, from.plusHours(2));

        Order pendingOrder = order(branchA);
        item(pendingOrder, traDa, "Pending drink", 10, "5000", OrderItemStatus.SERVED);
        payment(pendingOrder, branchA, actorA, PaymentTransactionType.PAYMENT, PaymentTransactionStatus.PENDING, from.plusHours(3));

        Order failedOrder = order(branchA);
        item(failedOrder, traDa, "Failed drink", 10, "5000", OrderItemStatus.SERVED);
        payment(failedOrder, branchA, actorA, PaymentTransactionType.PAYMENT, PaymentTransactionStatus.FAILED, from.plusHours(4));

        Order refundOnlyOrder = order(branchA);
        item(refundOnlyOrder, traDa, "Refund only", 10, "5000", OrderItemStatus.SERVED);
        payment(refundOnlyOrder, branchA, actorA, PaymentTransactionType.REFUND, PaymentTransactionStatus.COMPLETED, from.plusHours(5));

        Order boundaryOrder = order(branchA);
        item(boundaryOrder, traDa, "Boundary drink", 10, "5000", OrderItemStatus.SERVED);
        payment(boundaryOrder, branchA, actorA, PaymentTransactionType.PAYMENT, PaymentTransactionStatus.COMPLETED, to);

        Order otherBranchOrder = order(branchB);
        Dish otherBranchDish = dish(branchB, "Other branch", "50000");
        item(otherBranchOrder, otherBranchDish, "Other branch", 10, "50000", OrderItemStatus.SERVED);
        payment(otherBranchOrder, branchB, actorB, PaymentTransactionType.PAYMENT, PaymentTransactionStatus.COMPLETED, from.plusHours(6));

        List<TopDishProjection> results = orderItemRepository.aggregateTopDishesByBranchAndPaidAt(
                branchA.getId(), from, to, PageRequest.of(0, 10));

        assertEquals(3, results.size());
        assertDish(results.get(0), bunBo.getId(), "Bun bo", 5L, "250000", 2L);
        assertDish(results.get(1), pho.getId(), "Pho", 4L, "180000", 1L);
        assertDish(results.get(2), traDa.getId(), "Tra da", 1L, "5000", 1L);

        List<TopDishProjection> limited = orderItemRepository.aggregateTopDishesByBranchAndPaidAt(
                branchA.getId(), from, to, PageRequest.of(0, 2));
        assertEquals(2, limited.size());
        assertEquals("Bun bo", limited.get(0).getDishName());
        assertEquals("Pho", limited.get(1).getDishName());
    }

    private void assertDish(TopDishProjection result, Long dishId, String name, Long quantity, String revenue, Long orders) {
        assertEquals(dishId, result.getDishId());
        assertEquals(name, result.getDishName());
        assertEquals(quantity, result.getQuantitySold());
        assertEquals(new BigDecimal(revenue), result.getRevenue());
        assertEquals(orders, result.getOrderCount());
    }

    private Branch branch(String name) {
        Branch branch = new Branch();
        branch.setName(name);
        branch.setAddress(name);
        branch.setPhone("0");
        branch.setStatus("ACTIVE");
        return branch;
    }

    private Dish dish(Branch branch, String name, String price) {
        Dish dish = new Dish();
        dish.setName(name);
        dish.setPrice(new BigDecimal(price));
        dish.setCategory("Test");
        dish.setAvailable(true);
        dish.setBranch(branch);
        return dishRepository.save(dish);
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

    private void item(Order order, Dish dish, String snapshotName, int quantity, String price, OrderItemStatus status) {
        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setDish(dish);
        item.setDishNameSnapshot(snapshotName);
        item.setQuantity(quantity);
        item.setPrice(new BigDecimal(price));
        item.setLineTotal(new BigDecimal(price).multiply(BigDecimal.valueOf(quantity)));
        item.setStatus(status);
        orderItemRepository.save(item);
    }

    private User user(Branch branch) {
        Role role = roleRepository.findByName("ROLE_ADMIN")
                .orElseGet(() -> roleRepository.save(new Role(null, "ROLE_ADMIN")));
        User user = new User();
        user.setUsername("top-" + branch.getName());
        user.setPassword("password");
        user.setFullName("Top");
        user.setRole(role);
        user.setBranch(branch);
        user.setEnabled(true);
        return userRepository.save(user);
    }

    private void payment(Order order, Branch branch, User actor, PaymentTransactionType type,
                         PaymentTransactionStatus status, LocalDateTime completedAt) {
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setOrder(order);
        transaction.setBranch(branch);
        transaction.setTransactionType(type);
        transaction.setPaymentMethod(PaymentMethod.CASH);
        transaction.setAmount(BigDecimal.ONE);
        transaction.setStatus(status);
        transaction.setCreatedAt(completedAt);
        transaction.setCompletedAt(completedAt);
        transaction.setCreatedBy(actor);
        paymentTransactionRepository.save(transaction);
    }
}
