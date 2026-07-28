package com.example.demo;

import com.example.demo.exception.BusinessValidationException;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.model.*;
import com.example.demo.model.enums.InventoryTransactionType;
import com.example.demo.model.enums.OrderItemStatus;
import com.example.demo.model.enums.OrderStatus;
import com.example.demo.repository.*;
import com.example.demo.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@WithMockUser(username = "inventory-admin", roles = "ADMIN")
class OrderInventoryIntegrationTests {
    @Autowired private OrderService orderService;
    @Autowired private BranchRepository branchRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private DishRepository dishRepository;
    @Autowired private RestaurantTableRepository tableRepository;
    @Autowired private InventoryRepository inventoryRepository;
    @Autowired private RecipeRepository recipeRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderItemRepository orderItemRepository;
    @Autowired private InventoryTransactionRepository inventoryTransactionRepository;

    private Branch branchA;
    private Branch branchB;
    private Dish dishA;
    private InventoryItem inventoryA;

    @BeforeEach
    void setUp() {
        branchA = branchRepository.save(branch("Inventory A"));
        branchB = branchRepository.save(branch("Inventory B"));
        Role admin = roleRepository.findByName("ROLE_ADMIN").orElseGet(() -> roleRepository.save(new Role(null, "ROLE_ADMIN")));
        userRepository.save(user("inventory-admin", admin, branchA));
        dishA = dishRepository.save(dish("Dish A", branchA));
        inventoryA = inventoryRepository.save(inventory("Ingredient A", branchA, "10.000"));
    }

    @Test
    void pendingOrderConfirmConsumesInventoryAndWritesLedger() {
        Order order = orderWithRecipe(branchA, dishA, inventoryA, "2.000");

        orderService.updateStatus(order.getId(), OrderStatus.CONFIRMED);

        assertEquals(OrderStatus.CONFIRMED, orderRepository.findById(order.getId()).orElseThrow().getStatus());
        assertEquals(new BigDecimal("8.000"), inventoryRepository.findById(inventoryA.getId()).orElseThrow().getQuantity());
        assertEquals(1, ledger(order, InventoryTransactionType.ORDER_CONSUMPTION).size());
    }

    @Test
    void insufficientInventoryRollsBackConfirmWithoutLedger() {
        inventoryA.setQuantity(new BigDecimal("1.000"));
        inventoryRepository.save(inventoryA);
        Order order = orderWithRecipe(branchA, dishA, inventoryA, "2.000");

        assertThrows(BusinessValidationException.class,
                () -> orderService.updateStatus(order.getId(), OrderStatus.CONFIRMED));

        assertEquals(OrderStatus.PENDING, orderRepository.findById(order.getId()).orElseThrow().getStatus());
        assertEquals(new BigDecimal("1.000"), inventoryRepository.findById(inventoryA.getId()).orElseThrow().getQuantity());
        assertEquals(0, ledger(order, InventoryTransactionType.ORDER_CONSUMPTION).size());
    }

    @Test
    void duplicateConfirmDoesNotConsumeInventoryTwice() {
        Order order = orderWithRecipe(branchA, dishA, inventoryA, "2.000");

        orderService.updateStatus(order.getId(), OrderStatus.CONFIRMED);
        orderService.updateStatus(order.getId(), OrderStatus.CONFIRMED);

        assertEquals(new BigDecimal("8.000"), inventoryRepository.findById(inventoryA.getId()).orElseThrow().getQuantity());
        assertEquals(1, ledger(order, InventoryTransactionType.ORDER_CONSUMPTION).size());
    }

    @Test
    void cancellingPendingItemsReversesConsumptionExactlyOnce() {
        Order order = orderWithRecipe(branchA, dishA, inventoryA, "2.000");
        orderService.updateStatus(order.getId(), OrderStatus.CONFIRMED);

        orderService.updateStatus(order.getId(), OrderStatus.CANCELLED);
        orderService.updateStatus(order.getId(), OrderStatus.CANCELLED);

        assertEquals(OrderStatus.CANCELLED, orderRepository.findById(order.getId()).orElseThrow().getStatus());
        assertEquals(new BigDecimal("10.000"), inventoryRepository.findById(inventoryA.getId()).orElseThrow().getQuantity());
        assertEquals(1, ledger(order, InventoryTransactionType.ORDER_CONSUMPTION).size());
        assertEquals(1, ledger(order, InventoryTransactionType.ORDER_REVERSAL).size());
    }

    @Test
    void cancellationWithPreparingItemDoesNotReverseOrCancelOrder() {
        Order order = orderWithRecipe(branchA, dishA, inventoryA, "2.000");
        orderService.updateStatus(order.getId(), OrderStatus.CONFIRMED);
        OrderItem item = orderItemRepository.findByOrderBranchIdAndStatus(
                        branchA.getId(), OrderItemStatus.PENDING).stream()
                .filter(candidate -> candidate.getOrder().getId().equals(order.getId()))
                .findFirst().orElseThrow();
        item.setStatus(OrderItemStatus.PREPARING);
        orderItemRepository.save(item);

        assertThrows(BusinessValidationException.class,
                () -> orderService.updateStatus(order.getId(), OrderStatus.CANCELLED));

        assertEquals(OrderStatus.CONFIRMED, orderRepository.findById(order.getId()).orElseThrow().getStatus());
        assertEquals(new BigDecimal("8.000"), inventoryRepository.findById(inventoryA.getId()).orElseThrow().getQuantity());
        assertEquals(0, ledger(order, InventoryTransactionType.ORDER_REVERSAL).size());
    }

    @Test
    void crossBranchOrderCannotBeConfirmedOrConsumed() {
        Dish dishB = dishRepository.save(dish("Dish B", branchB));
        InventoryItem inventoryB = inventoryRepository.save(inventory("Ingredient B", branchB, "10.000"));
        Order orderB = orderWithRecipe(branchB, dishB, inventoryB, "2.000");

        assertThrows(ResourceNotFoundException.class,
                () -> orderService.updateStatus(orderB.getId(), OrderStatus.CONFIRMED));

        assertEquals(OrderStatus.PENDING, orderRepository.findById(orderB.getId()).orElseThrow().getStatus());
        assertEquals(new BigDecimal("10.000"), inventoryRepository.findById(inventoryB.getId()).orElseThrow().getQuantity());
        assertEquals(0, ledger(orderB, InventoryTransactionType.ORDER_CONSUMPTION).size());
    }

    private Order orderWithRecipe(Branch branch, Dish dish, InventoryItem item, String recipeAmount) {
        RestaurantTable table = tableRepository.save(table(branch));
        Order order = new Order();
        order.setBranch(branch);
        order.setTable(table);
        order.setCustomerName("Customer");
        order.setStatus(OrderStatus.PENDING);
        order = orderRepository.save(order);

        OrderItem orderItem = new OrderItem();
        orderItem.setOrder(order);
        orderItem.setDish(dish);
        orderItem.setDishNameSnapshot(dish.getName());
        orderItem.setQuantity(1);
        orderItem.setPrice(new BigDecimal("10000"));
        orderItem.setLineTotal(new BigDecimal("10000"));
        orderItem.setStatus(OrderItemStatus.PENDING);
        order.getOrderItems().add(orderItem);
        orderItemRepository.save(orderItem);

        Recipe recipe = new Recipe();
        recipe.setDish(dish);
        recipe.setBranch(branch);
        RecipeItem recipeItem = new RecipeItem();
        recipeItem.setRecipe(recipe);
        recipeItem.setIngredientName(item.getIngredientName());
        recipeItem.setInventoryItem(item);
        recipeItem.setAmount(new BigDecimal(recipeAmount));
        recipeItem.setUnit(item.getUnit());
        recipe.setRecipeItems(List.of(recipeItem));
        recipeRepository.save(recipe);
        return order;
    }

    private List<InventoryTransaction> ledger(Order order, InventoryTransactionType type) {
        return inventoryTransactionRepository.findByBranchIdAndReferenceTypeAndReferenceIdAndTransactionType(
                order.getBranch().getId(), "ORDER", order.getId(), type);
    }

    private Branch branch(String name) {
        Branch branch = new Branch();
        branch.setName(name);
        branch.setAddress(name + " address");
        branch.setPhone("000");
        branch.setStatus("ACTIVE");
        return branch;
    }

    private User user(String username, Role role, Branch branch) {
        User user = new User();
        user.setUsername(username);
        user.setPassword("password");
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
        dish.setCategory("Food");
        dish.setImageUrl("https://example.test/" + name + ".jpg");
        dish.setAvailable(true);
        dish.setBranch(branch);
        return dish;
    }

    private InventoryItem inventory(String name, Branch branch, String quantity) {
        InventoryItem item = new InventoryItem();
        item.setIngredientName(name);
        item.setQuantity(new BigDecimal(quantity));
        item.setMinThreshold(BigDecimal.ZERO);
        item.setUnit("kg");
        item.setBranch(branch);
        return item;
    }

    private RestaurantTable table(Branch branch) {
        RestaurantTable table = new RestaurantTable();
        table.setTableNumber("T-" + System.nanoTime());
        table.setBranch(branch);
        return table;
    }
}
