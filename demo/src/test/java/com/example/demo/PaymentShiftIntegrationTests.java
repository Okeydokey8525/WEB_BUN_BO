package com.example.demo;

import com.example.demo.dto.request.CloseShiftRequest;
import com.example.demo.dto.request.OpenShiftRequest;
import com.example.demo.dto.request.PayOrderRequest;
import com.example.demo.dto.request.RefundOrderRequest;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.exception.ShiftNotOpenException;
import com.example.demo.model.Branch;
import com.example.demo.model.Order;
import com.example.demo.model.PaymentTransaction;
import com.example.demo.model.RestaurantTable;
import com.example.demo.model.Role;
import com.example.demo.model.User;
import com.example.demo.model.enums.OrderStatus;
import com.example.demo.model.enums.OrderType;
import com.example.demo.model.enums.PaymentMethod;
import com.example.demo.model.enums.PaymentStatus;
import com.example.demo.model.enums.TableStatus;
import com.example.demo.repository.BranchRepository;
import com.example.demo.repository.OrderRepository;
import com.example.demo.repository.PaymentTransactionRepository;
import com.example.demo.repository.RestaurantTableRepository;
import com.example.demo.repository.RoleRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.PaymentService;
import com.example.demo.service.ShiftService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PaymentShiftIntegrationTests {

    @Autowired private BranchRepository branchRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RestaurantTableRepository tableRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private PaymentTransactionRepository paymentTransactionRepository;
    @Autowired private ShiftService shiftService;
    @Autowired private PaymentService paymentService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void paymentAndRefundAreLinkedToTheirRespectiveOpenShifts() {
        Branch branch = branchRepository.save(branch("Payment branch"));
        User cashier = userRepository.save(user("shift-cashier", branch));
        Order paidOrder = order("A1", branch, "100000");
        authenticate(cashier);

        Long paymentShiftId = shiftService.openShift(new OpenShiftRequest(BigDecimal.ZERO, "Opening shift")).shift().shiftId();
        paymentService.payOrder(new PayOrderRequest(paidOrder.getId(), PaymentMethod.CASH, new BigDecimal("100000"), null, null));

        List<PaymentTransaction> transactions = paymentTransactionRepository
                .findByOrderIdAndBranchIdOrderByCreatedAtAsc(paidOrder.getId(), branch.getId());
        assertEquals(1, transactions.size());
        assertEquals(paymentShiftId, transactions.get(0).getWorkShift().getId());

        shiftService.closeShift(new CloseShiftRequest(paymentShiftId, new BigDecimal("100000"), "Close shift"));
        Order blockedOrder = order("A2", branch, "50000");
        assertThrows(ShiftNotOpenException.class,
                () -> paymentService.payOrder(new PayOrderRequest(blockedOrder.getId(), PaymentMethod.CASH, new BigDecimal("50000"), null, null)));
        assertEquals(PaymentStatus.UNPAID, orderRepository.findById(blockedOrder.getId()).orElseThrow().getPaymentStatus());
        assertEquals(0, paymentTransactionRepository
                .findByOrderIdAndBranchIdOrderByCreatedAtAsc(blockedOrder.getId(), branch.getId()).size());

        Long refundShiftId = shiftService.openShift(new OpenShiftRequest(BigDecimal.ZERO, "Refund shift")).shift().shiftId();
        paymentService.refundOrder(new RefundOrderRequest(paidOrder.getId(), "Customer request"));

        transactions = paymentTransactionRepository.findByOrderIdAndBranchIdOrderByCreatedAtAsc(paidOrder.getId(), branch.getId());
        assertEquals(2, transactions.size());
        assertEquals(refundShiftId, transactions.get(1).getWorkShift().getId());
        assertEquals(PaymentStatus.REFUNDED, orderRepository.findById(paidOrder.getId()).orElseThrow().getPaymentStatus());
    }

    @Test
    void cashierCannotPayOrderFromAnotherBranchEvenWithAnOpenShift() {
        Branch branchA = branchRepository.save(branch("Branch A"));
        Branch branchB = branchRepository.save(branch("Branch B"));
        User cashierA = userRepository.save(user("cashier-a-shift", branchA));
        Order branchBOrder = order("B1", branchB, "70000");
        authenticate(cashierA);
        shiftService.openShift(new OpenShiftRequest(BigDecimal.ZERO, "A shift"));

        assertThrows(ResourceNotFoundException.class,
                () -> paymentService.payOrder(new PayOrderRequest(branchBOrder.getId(), PaymentMethod.CASH, new BigDecimal("70000"), null, null)));
        assertEquals(PaymentStatus.UNPAID, orderRepository.findById(branchBOrder.getId()).orElseThrow().getPaymentStatus());
        assertEquals(0, paymentTransactionRepository
                .findByOrderIdAndBranchIdOrderByCreatedAtAsc(branchBOrder.getId(), branchB.getId()).size());
    }

    private void authenticate(User user) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                user.getUsername(), "N/A", List.of(new SimpleGrantedAuthority(user.getRole().getName()))));
    }

    private Branch branch(String name) {
        Branch branch = new Branch();
        branch.setName(name); branch.setAddress(name + " address"); branch.setPhone("000"); branch.setStatus("ACTIVE");
        return branch;
    }

    private User user(String username, Branch branch) {
        Role role = roleRepository.findByName("ROLE_CASHIER").orElseGet(() -> roleRepository.save(new Role(null, "ROLE_CASHIER")));
        User user = new User();
        user.setUsername(username); user.setPassword("password"); user.setFullName(username); user.setRole(role); user.setBranch(branch); user.setEnabled(true);
        return user;
    }

    private Order order(String tableNumber, Branch branch, String total) {
        RestaurantTable table = new RestaurantTable();
        table.setTableNumber(tableNumber); table.setBranch(branch); table.setStatus(TableStatus.FREE);
        table = tableRepository.save(table);
        Order order = new Order();
        order.setTable(table); order.setBranch(branch); order.setCustomerName("Customer");
        order.setSubtotal(new BigDecimal(total)); order.setTotalAmount(new BigDecimal(total));
        order.setStatus(OrderStatus.PENDING); order.setPaymentStatus(PaymentStatus.UNPAID);
        order.setPaymentMethod(PaymentMethod.CASH); order.setOrderType(OrderType.DINE_IN); order.setCreatedAt(LocalDateTime.now());
        return orderRepository.save(order);
    }
}
