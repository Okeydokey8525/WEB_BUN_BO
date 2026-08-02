package com.example.demo;

import com.example.demo.model.*;
import com.example.demo.model.enums.*;
import com.example.demo.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ShiftControllerSecurityIntegrationTests {

    @Autowired MockMvc mockMvc;
    @Autowired BranchRepository branchRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired UserRepository userRepository;
    @Autowired WorkShiftRepository workShiftRepository;
    @Autowired RestaurantTableRepository tableRepository;
    @Autowired OrderRepository orderRepository;
    @Autowired PaymentTransactionRepository paymentTransactionRepository;

    private Branch branchA;
    private Branch branchB;
    private User cashierA;
    private User cashierAOther;
    private User cashierB;
    private User adminA;

    @BeforeEach
    void setUp() {
        branchA = branchRepository.save(branch("Shift Branch A"));
        branchB = branchRepository.save(branch("Shift Branch B"));
        cashierA = userRepository.save(persistedUser("shift-cashier-a", "ROLE_CASHIER", branchA));
        cashierAOther = userRepository.save(persistedUser("shift-cashier-other", "ROLE_CASHIER", branchA));
        cashierB = userRepository.save(persistedUser("shift-cashier-b", "ROLE_CASHIER", branchB));
        adminA = userRepository.save(persistedUser("shift-admin-a", "ROLE_ADMIN", branchA));
        userRepository.save(persistedUser("shift-user-a", "ROLE_USER", branchA));
        userRepository.save(persistedUser("shift-kitchen-a", "ROLE_KITCHEN", branchA));
        userRepository.save(persistedUser("shift-waiter-a", "ROLE_WAITER", branchA));
        userRepository.save(persistedUser("shift-inventory-a", "ROLE_INVENTORY", branchA));
    }

    @Test
    void anonymousAndNonCashierRolesCannotAccessShiftRoutes() throws Exception {
        mockMvc.perform(get("/cashier/shifts/current")).andExpect(status().is3xxRedirection());
        for (String[] actor : new String[][]{{"shift-user-a", "USER"}, {"shift-kitchen-a", "KITCHEN"}, {"shift-waiter-a", "WAITER"}, {"shift-inventory-a", "INVENTORY"}}) {
            mockMvc.perform(get("/cashier/shifts/current").with(user(actor[0]).roles(actor[1])))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void cashierAndAdminCanViewCurrentShiftAndCsrfProtectsPosts() throws Exception {
        mockMvc.perform(get("/cashier/shifts/current").with(cashier(cashierA))).andExpect(status().isOk()).andExpect(view().name("cashier/shifts"));
        mockMvc.perform(get("/cashier/shifts/current").with(admin(adminA))).andExpect(status().isOk());
        mockMvc.perform(post("/cashier/shifts/open").with(cashier(cashierA)).param("openingCash", "0")).andExpect(status().isForbidden());
        mockMvc.perform(post("/cashier/shifts/1/close").with(cashier(cashierA)).param("shiftId", "1").param("actualCash", "0")).andExpect(status().isForbidden());
    }

    @Test
    void cashierAndAdminCanOpenOnlyOneShiftInTheirOwnBranch() throws Exception {
        mockMvc.perform(post("/cashier/shifts/open").with(cashier(cashierA)).with(csrf()).param("openingCash", "500000"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/cashier/shifts/current"));
        WorkShift cashierShift = workShiftRepository.findByCashierIdAndStatus(cashierA.getId(), ShiftStatus.OPEN).orElseThrow();
        assertEquals(branchA.getId(), cashierShift.getBranch().getId());
        mockMvc.perform(post("/cashier/shifts/open").with(cashier(cashierA)).with(csrf()).param("openingCash", "0"))
                .andExpect(status().is3xxRedirection());
        assertEquals(1, workShiftRepository.findByBranchIdAndCashierIdOrderByOpenedAtDesc(branchA.getId(), cashierA.getId()).size());

        mockMvc.perform(post("/cashier/shifts/open").with(admin(adminA)).with(csrf()).param("openingCash", "0"))
                .andExpect(status().is3xxRedirection());
        assertEquals(branchA.getId(), workShiftRepository.findByCashierIdAndStatus(adminA.getId(), ShiftStatus.OPEN).orElseThrow().getBranch().getId());
    }

    @Test
    void invalidOpeningCashDoesNotCreateShift() throws Exception {
        mockMvc.perform(post("/cashier/shifts/open").with(cashier(cashierA)).with(csrf()).param("openingCash", "-1"))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(post("/cashier/shifts/open").with(cashier(cashierA)).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertEquals(0, workShiftRepository.findByBranchIdAndCashierIdOrderByOpenedAtDesc(branchA.getId(), cashierA.getId()).size());
    }

    @Test
    void historyAndDetailEnforceOwnershipAndBranchIsolation() throws Exception {
        WorkShift own = openShift(cashierA, branchA);
        WorkShift other = openShift(cashierAOther, branchA);
        WorkShift foreign = openShift(cashierB, branchB);
        mockMvc.perform(get("/cashier/shifts/history").with(cashier(cashierA))).andExpect(status().isOk());
        mockMvc.perform(get("/cashier/shifts/{id}", own.getId()).with(cashier(cashierA))).andExpect(status().isOk());
        mockMvc.perform(get("/cashier/shifts/{id}", other.getId()).with(cashier(cashierA))).andExpect(status().isNotFound());
        mockMvc.perform(get("/cashier/shifts/{id}", foreign.getId()).with(cashier(cashierA))).andExpect(status().isNotFound());
        mockMvc.perform(get("/cashier/shifts/{id}", other.getId()).with(admin(adminA))).andExpect(status().isOk());
    }

    @Test
    void closeUsesPathIdAndEnforcesOwnership() throws Exception {
        WorkShift own = openShift(cashierA, branchA);
        WorkShift other = openShift(cashierAOther, branchA);
        mockMvc.perform(post("/cashier/shifts/{id}/close", own.getId()).with(cashier(cashierA)).with(csrf())
                        .param("shiftId", own.getId().toString()).param("actualCash", "25000"))
                .andExpect(status().is3xxRedirection());
        WorkShift closed = workShiftRepository.findById(own.getId()).orElseThrow();
        assertEquals(ShiftStatus.CLOSED, closed.getStatus());
        assertEquals(new BigDecimal("25000"), closed.getActualCash());
        assertEquals(new BigDecimal("25000"), closed.getCashDifference());

        mockMvc.perform(post("/cashier/shifts/{id}/close", other.getId()).with(cashier(cashierA)).with(csrf())
                        .param("shiftId", other.getId().toString()).param("actualCash", "0"))
                .andExpect(status().is3xxRedirection());
        assertEquals(ShiftStatus.OPEN, workShiftRepository.findById(other.getId()).orElseThrow().getStatus());
        mockMvc.perform(post("/cashier/shifts/{id}/close", other.getId()).with(admin(adminA)).with(csrf())
                        .param("shiftId", other.getId().toString()).param("actualCash", "0"))
                .andExpect(status().is3xxRedirection());
        assertEquals(ShiftStatus.CLOSED, workShiftRepository.findById(other.getId()).orElseThrow().getStatus());
    }

    @Test
    void closeValidationAndMismatchedHiddenIdDoNotChangeShift() throws Exception {
        WorkShift shift = openShift(cashierA, branchA);
        mockMvc.perform(post("/cashier/shifts/{id}/close", shift.getId()).with(cashier(cashierA)).with(csrf())
                        .param("shiftId", shift.getId().toString()).param("actualCash", "-1"))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(post("/cashier/shifts/{id}/close", shift.getId()).with(cashier(cashierA)).with(csrf())
                        .param("shiftId", "999999").param("actualCash", "0"))
                .andExpect(status().is3xxRedirection());
        assertEquals(ShiftStatus.OPEN, workShiftRepository.findById(shift.getId()).orElseThrow().getStatus());
    }

    @Test
    void paymentEndpointRequiresOpenShiftAndPersistsItsForeignKey() throws Exception {
        Order order = order(branchA, "Payment table", "10000");
        mockMvc.perform(post("/cashier/orders/{id}/pay", order.getId()).with(cashier(cashierA)).with(csrf())
                        .param("paymentMethod", "CASH").param("amountTendered", "10000"))
                .andExpect(status().isBadRequest());
        WorkShift shift = openShift(cashierA, branchA);
        mockMvc.perform(post("/cashier/orders/{id}/pay", order.getId()).with(cashier(cashierA)).with(csrf())
                        .param("paymentMethod", "CASH").param("amountTendered", "10000"))
                .andExpect(status().is3xxRedirection());
        PaymentTransaction transaction = paymentTransactionRepository.findByOrderIdAndBranchIdOrderByCreatedAtAsc(order.getId(), branchA.getId()).get(0);
        assertEquals(shift.getId(), transaction.getWorkShift().getId());
    }

    private SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor cashier(User user) { return user(user.getUsername()).roles("CASHIER"); }
    private SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor admin(User user) { return user(user.getUsername()).roles("ADMIN"); }
    private Branch branch(String name) { Branch branch = new Branch(); branch.setName(name); branch.setAddress(name); branch.setPhone("0"); branch.setStatus("ACTIVE"); return branch; }
    private User persistedUser(String username, String roleName, Branch branch) { Role role = roleRepository.findByName(roleName).orElseGet(() -> roleRepository.save(new Role(null, roleName))); User user = new User(); user.setUsername(username); user.setPassword("password"); user.setFullName(username); user.setRole(role); user.setBranch(branch); user.setEnabled(true); return user; }
    private WorkShift openShift(User cashier, Branch branch) { WorkShift shift = new WorkShift(); shift.setBranch(branch); shift.setCashier(cashier); shift.setOpenedBy(cashier); shift.setStatus(ShiftStatus.OPEN); shift.setOpenedAt(LocalDateTime.now()); shift.setOpeningCash(BigDecimal.ZERO); shift.setExpectedCash(BigDecimal.ZERO); shift.setActualCash(BigDecimal.ZERO); shift.setCashDifference(BigDecimal.ZERO); shift.setTotalSales(BigDecimal.ZERO); shift.setCashSales(BigDecimal.ZERO); shift.setTransferSales(BigDecimal.ZERO); shift.setCardSales(BigDecimal.ZERO); shift.setRefundTotal(BigDecimal.ZERO); shift.setOrderCount(0); return workShiftRepository.save(shift); }
    private Order order(Branch branch, String tableNumber, String total) { RestaurantTable table = new RestaurantTable(); table.setTableNumber(tableNumber); table.setBranch(branch); table.setStatus(TableStatus.FREE); table = tableRepository.save(table); Order order = new Order(); order.setTable(table); order.setBranch(branch); order.setCustomerName("Customer"); order.setSubtotal(new BigDecimal(total)); order.setTotalAmount(new BigDecimal(total)); order.setStatus(OrderStatus.PENDING); order.setPaymentStatus(PaymentStatus.UNPAID); order.setPaymentMethod(PaymentMethod.CASH); order.setOrderType(OrderType.DINE_IN); order.setCreatedAt(LocalDateTime.now()); return orderRepository.save(order); }
}
