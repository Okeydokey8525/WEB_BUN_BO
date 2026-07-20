package com.example.demo;

import com.example.demo.model.*;
import com.example.demo.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.model.enums.OrderItemStatus;
import com.example.demo.model.enums.OrderStatus;
import com.example.demo.model.enums.PaymentMethod;
import com.example.demo.model.enums.PaymentStatus;
import com.example.demo.model.enums.TableStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class BranchSecurityIntegrationTests {

    @Autowired MockMvc mockMvc;
    @Autowired BranchRepository branchRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired UserRepository userRepository;
    @Autowired DishRepository dishRepository;
    @Autowired RestaurantTableRepository tableRepository;
    @Autowired InventoryRepository inventoryRepository;
    @Autowired OrderRepository orderRepository;
    @Autowired OrderItemRepository orderItemRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private Branch branchA;
    private Branch branchB;
    private Dish dishA;
    private Dish dishB;
    private RestaurantTable tableA;
    private RestaurantTable tableB;
    private InventoryItem inventoryA;
    private InventoryItem inventoryB;
    private Order orderA;
    private Order orderB;
    private OrderItem itemA;
    private OrderItem itemB;

    @BeforeEach
    void setUp() {
        branchA = branchRepository.save(branch("Branch A"));
        branchB = branchRepository.save(branch("Branch B"));
        Role admin = role("ROLE_ADMIN");
        Role inventory = role("ROLE_INVENTORY");
        Role kitchen = role("ROLE_KITCHEN");
        Role cashier = role("ROLE_CASHIER");
        userRepository.save(persistUser("admin-a", admin, branchA));
        userRepository.save(persistUser("admin-b", admin, branchB));
        userRepository.save(persistUser("inventory-a", inventory, branchA));
        userRepository.save(persistUser("kitchen-a", kitchen, branchA));
        userRepository.save(persistUser("cashier-a", cashier, branchA));
        userRepository.save(persistUser("cashier-b", cashier, branchB));

        dishA = dishRepository.save(dish("Dish A", branchA));
        dishB = dishRepository.save(dish("Dish B", branchB));
        tableA = tableRepository.save(table("A1", branchA));
        tableB = tableRepository.save(table("B1", branchB));
        inventoryA = inventoryRepository.save(inventory("Stock A", branchA));
        inventoryB = inventoryRepository.save(inventory("Stock B", branchB));
        orderA = orderRepository.save(order(tableA, branchA));
        orderB = orderRepository.save(order(tableB, branchB));
        itemA = orderItemRepository.save(item(orderA, dishA));
        itemB = orderItemRepository.save(item(orderB, dishB));
    }

    @Test
    void adminAOnlySeesDishAAndCannotToggleDishB() throws Exception {
        mockMvc.perform(get("/admin/menu").with(user("admin-a").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Dish A")))
                .andExpect(content().string(not(containsString("Dish B"))));

        mockMvc.perform(post("/admin/menu/toggle/{id}", dishB.getId()).with(user("admin-a").roles("ADMIN")).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminACannotEditOrDeleteDishB() throws Exception {
        mockMvc.perform(post("/admin/menu/save")
                        .with(user("admin-a").roles("ADMIN")).with(csrf())
                        .param("id", dishB.getId().toString())
                        .param("name", "Cross Branch Edit")
                        .param("price", "1000")
                        .param("category", "Bún Bò"))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/admin/menu/delete/{id}", dishB.getId()).with(user("admin-a").roles("ADMIN")).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminACannotViewEditOrDeleteTableB() throws Exception {
        mockMvc.perform(get("/admin/table-detail?id={id}", tableB.getId()).with(user("admin-a").roles("ADMIN")))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/admin/tables/save")
                        .with(user("admin-a").roles("ADMIN")).with(csrf())
                        .param("id", tableB.getId().toString())
                        .param("tableNumber", "B9")
                        .param("status", "FREE"))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/admin/tables/delete/{id}", tableB.getId()).with(user("admin-a").roles("ADMIN")).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void inventoryUserAOnlySeesAndUpdatesInventoryA() throws Exception {
        mockMvc.perform(get("/admin/inventory").with(user("inventory-a").roles("INVENTORY")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Stock A")))
                .andExpect(content().string(not(containsString("Stock B"))));

        mockMvc.perform(post("/admin/inventory/update")
                        .with(user("inventory-a").roles("INVENTORY")).with(csrf())
                        .param("itemId", inventoryB.getId().toString())
                        .param("quantity", "999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminACannotOperateOnOrderB() throws Exception {
        mockMvc.perform(post("/admin/order/{id}/update-status", orderB.getId())
                        .with(user("admin-a").roles("ADMIN")).with(csrf())
                        .param("status", "COMPLETED"))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/admin/order/{id}/mark-paid", orderB.getId()).with(user("admin-a").roles("ADMIN")).with(csrf()))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/admin/order/{id}/print", orderB.getId()).with(user("admin-a").roles("ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void kitchenUserAOnlySeesAndUpdatesBranchAItems() throws Exception {
        mockMvc.perform(get("/kitchen/dashboard").with(user("kitchen-a").roles("KITCHEN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Dish A")))
                .andExpect(content().string(not(containsString("Dish B"))));

        mockMvc.perform(post("/kitchen/ready/{id}", itemB.getId()).with(user("kitchen-a").roles("KITCHEN")).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void roleAndAnonymousRestrictionsAreEnforced() throws Exception {
        mockMvc.perform(post("/admin/menu/toggle/{id}", dishA.getId()).with(user("cashier-a").roles("CASHIER")).with(csrf()))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/admin/inventory/update")
                        .with(user("kitchen-a").roles("KITCHEN")).with(csrf())
                        .param("itemId", inventoryA.getId().toString())
                        .param("quantity", "11"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/admin/order/{id}/update-status", orderA.getId())
                        .with(user("inventory-a").roles("INVENTORY")).with(csrf())
                        .param("status", "COOKING"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/admin/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void publicOrderTrackingRequiresMatchingToken() throws Exception {
        mockMvc.perform(get("/order/status/{id}", orderA.getId()).param("token", orderA.getPublicToken()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/order/status/{id}", orderA.getId()).param("token", "wrong-token"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/order/status/{id}", orderA.getId()))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/order/status/{id}", orderB.getId()).param("token", orderA.getPublicToken()))
                .andExpect(status().isNotFound());
    }


    @Test
    void createOrderCalculatesBackendTotalsAndRejectsCrossBranchDish() throws Exception {
        mockMvc.perform(post("/order/place")
                        .with(csrf())
                        .param("tableId", tableA.getId().toString())
                        .param("customerName", "Guest A")
                        .param("paymentMethod", "CASH")
                        .param("orderType", "DINE_IN")
                        .param("items[0].dishId", dishA.getId().toString())
                        .param("items[0].quantity", "2"))
                .andExpect(status().is3xxRedirection());

        Order created = orderRepository.findAllByOrderByCreatedAtDesc().get(0);
        assertNotNull(created.getPublicToken());
        assertFalse(created.getPublicToken().isBlank());
        assertEquals(0, created.getTotalAmount().compareTo(new BigDecimal("20000")));
        assertEquals(TableStatus.ORDERING, tableRepository.findByIdAndBranchId(tableA.getId(), branchA.getId()).orElseThrow().getStatus());

        mockMvc.perform(post("/order/place")
                        .with(csrf())
                        .param("tableId", tableA.getId().toString())
                        .param("customerName", "Guest A")
                        .param("paymentMethod", "CASH")
                        .param("orderType", "DINE_IN")
                        .param("items[0].dishId", dishB.getId().toString())
                        .param("items[0].quantity", "1"))
                .andExpect(status().isNotFound());
    }

    @Test
    void createOrderRejectsInvalidCartAndUnavailableDish() throws Exception {
        mockMvc.perform(post("/order/place")
                        .with(csrf())
                        .param("tableId", tableA.getId().toString())
                        .param("customerName", "Guest A")
                        .param("paymentMethod", "CASH")
                        .param("orderType", "DINE_IN"))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(post("/order/place")
                        .with(csrf())
                        .param("tableId", tableA.getId().toString())
                        .param("customerName", "Guest A")
                        .param("paymentMethod", "CASH")
                        .param("orderType", "DINE_IN")
                        .param("items[0].dishId", dishA.getId().toString())
                        .param("items[0].quantity", "0"))
                .andExpect(status().is3xxRedirection());

        dishA.setAvailable(false);
        dishRepository.save(dishA);
        mockMvc.perform(post("/order/place")
                        .with(csrf())
                        .param("tableId", tableA.getId().toString())
                        .param("customerName", "Guest A")
                        .param("paymentMethod", "CASH")
                        .param("orderType", "DINE_IN")
                        .param("items[0].dishId", dishA.getId().toString())
                        .param("items[0].quantity", "1"))
                .andExpect(status().isNotFound());
    }

    @Test
    void orderCompletionDoesNotAutomaticallyMarkPaid() throws Exception {
        orderA.setStatus(OrderStatus.SERVED);
        orderA.setPaymentStatus(PaymentStatus.UNPAID);
        orderRepository.save(orderA);

        mockMvc.perform(post("/admin/order/{id}/update-status", orderA.getId())
                        .with(user("admin-a").roles("ADMIN")).with(csrf())
                        .param("status", "COMPLETED"))
                .andExpect(status().is3xxRedirection());

        Order completed = orderRepository.findByIdAndBranchId(orderA.getId(), branchA.getId()).orElseThrow();
        assertEquals(OrderStatus.COMPLETED, completed.getStatus());
        assertEquals(PaymentStatus.UNPAID, completed.getPaymentStatus());
    }

    @Test
    void cashierPaymentEndpointsEnforceAuthenticationRoleAndCsrf() throws Exception {
        mockMvc.perform(get("/cashier/dashboard"))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(post("/cashier/orders/{id}/pay", orderA.getId())
                        .with(user("kitchen-a").roles("KITCHEN")).with(csrf())
                        .param("paymentMethod", "CASH").param("amountTendered", "10000"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/cashier/orders/{id}/pay", orderA.getId())
                        .with(user("cashier-a").roles("CASHIER"))
                        .param("paymentMethod", "CASH").param("amountTendered", "10000"))
                .andExpect(status().isForbidden());
    }

    @Test
    void cashierCanPayOnlyOrdersInOwnBranch() throws Exception {
        mockMvc.perform(post("/cashier/orders/{id}/pay", orderA.getId())
                        .with(user("cashier-a").roles("CASHIER")).with(csrf())
                        .param("paymentMethod", "CASH").param("amountTendered", "10000"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cashier/orders/" + orderA.getId()));
        assertEquals(PaymentStatus.PAID, orderRepository.findByIdAndBranchId(orderA.getId(), branchA.getId()).orElseThrow().getPaymentStatus());

        mockMvc.perform(post("/cashier/orders/{id}/pay", orderA.getId())
                        .with(user("cashier-b").roles("CASHIER")).with(csrf())
                        .param("paymentMethod", "CASH").param("amountTendered", "10000"))
                .andExpect(status().isNotFound());
    }

    private Branch branch(String name) {
        Branch branch = new Branch();
        branch.setName(name);
        branch.setAddress(name + " Address");
        branch.setPhone("000");
        branch.setStatus("ACTIVE");
        return branch;
    }

    private Role role(String name) {
        return roleRepository.findByName(name).orElseGet(() -> roleRepository.save(new Role(null, name)));
    }

    private User persistUser(String username, Role role, Branch branch) {
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode("password"));
        user.setFullName(username);
        user.setRole(role);
        user.setBranch(branch);
        user.setEnabled(true);
        return user;
    }

    private Dish dish(String name, Branch branch) {
        Dish dish = new Dish();
        dish.setName(name);
        dish.setPrice(new BigDecimal("10000"));
        dish.setCategory("Bún Bò");
        dish.setImageUrl("https://example.test/" + name + ".jpg");
        dish.setAvailable(true);
        dish.setBranch(branch);
        return dish;
    }

    private RestaurantTable table(String number, Branch branch) {
        RestaurantTable table = new RestaurantTable();
        table.setTableNumber(number);
        table.setStatus(TableStatus.FREE);
        table.setBranch(branch);
        return table;
    }

    private InventoryItem inventory(String name, Branch branch) {
        InventoryItem item = new InventoryItem();
        item.setIngredientName(name);
        item.setQuantity(10.0);
        item.setUnit("kg");
        item.setMinThreshold(2.0);
        item.setBranch(branch);
        return item;
    }

    private Order order(RestaurantTable table, Branch branch) {
        Order order = new Order();
        order.setTable(table);
        order.setBranch(branch);
        order.setCustomerName("Customer " + branch.getName());
        order.setSubtotal(new BigDecimal("10000"));
        order.setTotalAmount(new BigDecimal("10000"));
        order.setStatus(OrderStatus.PENDING);
        order.setPaymentMethod(PaymentMethod.CASH);
        order.setPaymentStatus(PaymentStatus.UNPAID);
        order.setCreatedAt(LocalDateTime.now());
        return order;
    }

    private OrderItem item(Order order, Dish dish) {
        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setDish(dish);
        item.setQuantity(1);
        item.setPrice(dish.getPrice());
        item.setDishNameSnapshot(dish.getName());
        item.setLineTotal(dish.getPrice());
        item.setStatus(OrderItemStatus.PENDING);
        return item;
    }
}
