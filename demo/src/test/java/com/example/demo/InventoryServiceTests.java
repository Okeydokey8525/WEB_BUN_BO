package com.example.demo;

import com.example.demo.exception.BusinessValidationException;
import com.example.demo.exception.BranchAccessDeniedException;
import com.example.demo.model.*;
import com.example.demo.model.enums.InventoryTransactionType;
import com.example.demo.repository.InventoryRepository;
import com.example.demo.repository.InventoryTransactionRepository;
import com.example.demo.repository.RecipeRepository;
import com.example.demo.security.BranchAccessService;
import com.example.demo.security.CurrentUserService;
import com.example.demo.service.InventoryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTests {
    @Mock InventoryRepository inventoryRepository;
    @Mock InventoryTransactionRepository inventoryTransactionRepository;
    @Mock RecipeRepository recipeRepository;
    @Mock BranchAccessService branchAccessService;
    @Mock CurrentUserService currentUserService;
    @InjectMocks InventoryService inventoryService;

    @Test
    void stockInUpdatesQuantityAndWritesLedger() {
        InventoryItem item = inventory(101L, "10.000");
        when(branchAccessService.requireScopedBranchId()).thenReturn(1L);
        when(inventoryRepository.findByIdAndBranchId(101L, 1L)).thenReturn(Optional.of(item));
        when(currentUserService.getCurrentUser()).thenReturn(user());
        when(inventoryTransactionRepository.save(any(InventoryTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        InventoryTransaction transaction = inventoryService.stockIn(101L, new BigDecimal("2.500"), "Delivery");

        assertEquals(new BigDecimal("12.500"), item.getQuantity());
        assertEquals(InventoryTransactionType.STOCK_IN, transaction.getTransactionType());
        assertEquals(new BigDecimal("10.000"), transaction.getQuantityBefore());
        assertEquals(new BigDecimal("12.500"), transaction.getQuantityAfter());
    }

    @Test
    void adjustmentCannotMakeStockNegative() {
        InventoryItem item = inventory(101L, "2.000");
        when(branchAccessService.requireScopedBranchId()).thenReturn(1L);
        when(inventoryRepository.findByIdAndBranchId(101L, 1L)).thenReturn(Optional.of(item));

        assertThrows(BusinessValidationException.class,
                () -> inventoryService.adjustStock(101L, new BigDecimal("-2.001"), "Count correction"));

        verifyNoInteractions(inventoryTransactionRepository);
        verify(inventoryRepository, never()).save(any());
    }

    @Test
    void canFulfillOrderReturnsFalseWhenInventoryIsInsufficient() {
        Order order = orderWithSingleRecipeItem("3.000");
        when(recipeRepository.findByDishIdAndBranchId(11L, 1L)).thenReturn(Optional.of(recipeFor(order, inventory(101L, "2.000"), "3.000")));

        assertFalse(inventoryService.canFulfillOrder(order));
    }

    @Test
    void consumeForOrderDeductsStockAndWritesConsumptionLedger() {
        InventoryItem item = inventory(101L, "10.000");
        Order order = orderWithSingleRecipeItem("3.000");
        when(branchAccessService.requireScopedBranchId()).thenReturn(1L);
        when(recipeRepository.findByDishIdAndBranchId(11L, 1L)).thenReturn(Optional.of(recipeFor(order, item, "3.000")));
        when(inventoryRepository.findByIdAndBranchId(101L, 1L)).thenReturn(Optional.of(item));
        when(inventoryTransactionRepository.existsByInventoryItemIdAndBranchIdAndReferenceTypeAndReferenceIdAndTransactionType(
                eq(101L), eq(1L), eq("ORDER"), eq(7L), eq(InventoryTransactionType.ORDER_CONSUMPTION))).thenReturn(false);
        when(currentUserService.getCurrentUser()).thenReturn(user());
        when(inventoryTransactionRepository.save(any(InventoryTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        List<InventoryTransaction> transactions = inventoryService.consumeForOrder(order);

        assertEquals(new BigDecimal("7.000"), item.getQuantity());
        assertEquals(1, transactions.size());
        assertEquals(InventoryTransactionType.ORDER_CONSUMPTION, transactions.get(0).getTransactionType());
        assertEquals(new BigDecimal("3.000"), transactions.get(0).getQuantity());
    }

    @Test
    void duplicateConsumptionIsRejectedBeforeChangingStock() {
        InventoryItem item = inventory(101L, "10.000");
        Order order = orderWithSingleRecipeItem("3.000");
        when(branchAccessService.requireScopedBranchId()).thenReturn(1L);
        when(recipeRepository.findByDishIdAndBranchId(11L, 1L)).thenReturn(Optional.of(recipeFor(order, item, "3.000")));
        when(inventoryRepository.findByIdAndBranchId(101L, 1L)).thenReturn(Optional.of(item));
        when(inventoryTransactionRepository.existsByInventoryItemIdAndBranchIdAndReferenceTypeAndReferenceIdAndTransactionType(
                eq(101L), eq(1L), eq("ORDER"), eq(7L), eq(InventoryTransactionType.ORDER_CONSUMPTION))).thenReturn(true);

        assertThrows(BusinessValidationException.class, () -> inventoryService.consumeForOrder(order));

        assertEquals(new BigDecimal("10.000"), item.getQuantity());
        verify(inventoryRepository, never()).save(any());
        verify(inventoryTransactionRepository, never()).save(any());
    }

    @Test
    void insufficientIngredientRollsBackBeforeAnyInventoryWrite() {
        InventoryItem first = inventory(101L, "10.000");
        InventoryItem second = inventory(102L, "1.000");
        Order order = orderWithTwoRecipeItems();
        when(branchAccessService.requireScopedBranchId()).thenReturn(1L);
        when(recipeRepository.findByDishIdAndBranchId(11L, 1L))
                .thenReturn(Optional.of(recipeFor(order, first, "2.000", second, "2.000")));
        when(inventoryRepository.findByIdAndBranchId(101L, 1L)).thenReturn(Optional.of(first));
        when(inventoryRepository.findByIdAndBranchId(102L, 1L)).thenReturn(Optional.of(second));
        when(inventoryTransactionRepository.existsByInventoryItemIdAndBranchIdAndReferenceTypeAndReferenceIdAndTransactionType(
                anyLong(), eq(1L), eq("ORDER"), eq(7L), eq(InventoryTransactionType.ORDER_CONSUMPTION))).thenReturn(false);

        assertThrows(BusinessValidationException.class, () -> inventoryService.consumeForOrder(order));

        assertEquals(new BigDecimal("10.000"), first.getQuantity());
        assertEquals(new BigDecimal("1.000"), second.getQuantity());
        verify(inventoryRepository, never()).save(any());
        verify(inventoryTransactionRepository, never()).save(any());
    }

    @Test
    void reverseConsumptionRestoresStockAndWritesReversalLedger() {
        InventoryItem item = inventory(101L, "7.000");
        Order order = orderWithSingleRecipeItem("3.000");
        InventoryTransaction consumption = new InventoryTransaction();
        consumption.setInventoryItem(item);
        consumption.setQuantity(new BigDecimal("3.000"));
        when(branchAccessService.requireScopedBranchId()).thenReturn(1L);
        when(inventoryTransactionRepository.findByBranchIdAndReferenceTypeAndReferenceIdAndTransactionType(
                1L, "ORDER", 7L, InventoryTransactionType.ORDER_CONSUMPTION)).thenReturn(List.of(consumption));
        when(inventoryRepository.findByIdAndBranchId(101L, 1L)).thenReturn(Optional.of(item));
        when(inventoryTransactionRepository.existsByInventoryItemIdAndBranchIdAndReferenceTypeAndReferenceIdAndTransactionType(
                101L, 1L, "ORDER", 7L, InventoryTransactionType.ORDER_REVERSAL)).thenReturn(false);
        when(currentUserService.getCurrentUser()).thenReturn(user());
        when(inventoryTransactionRepository.save(any(InventoryTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        List<InventoryTransaction> transactions = inventoryService.reverseConsumptionForOrder(order);

        assertEquals(new BigDecimal("10.000"), item.getQuantity());
        assertEquals(InventoryTransactionType.ORDER_REVERSAL, transactions.get(0).getTransactionType());
    }

    @Test
    void reverseConsumptionRequiresAnExistingConsumptionLedger() {
        Order order = orderWithSingleRecipeItem("3.000");
        when(inventoryTransactionRepository.findByBranchIdAndReferenceTypeAndReferenceIdAndTransactionType(
                1L, "ORDER", 7L, InventoryTransactionType.ORDER_CONSUMPTION)).thenReturn(List.of());

        assertThrows(BusinessValidationException.class, () -> inventoryService.reverseConsumptionForOrder(order));

        verify(inventoryRepository, never()).save(any());
        verify(inventoryTransactionRepository, never()).save(any());
    }

    @Test
    void crossBranchOrderIsRejectedBeforeRecipeLookup() {
        Order order = orderWithSingleRecipeItem("3.000");
        doThrow(new BranchAccessDeniedException("Denied")).when(branchAccessService).requireBranchAccess(1L);

        assertThrows(BranchAccessDeniedException.class, () -> inventoryService.canFulfillOrder(order));

        verifyNoInteractions(recipeRepository);
    }

    private Order orderWithSingleRecipeItem(String amount) {
        Order order = baseOrder();
        Dish dish = new Dish();
        dish.setId(11L);
        OrderItem orderItem = new OrderItem();
        orderItem.setDish(dish);
        orderItem.setQuantity(1);
        order.setOrderItems(List.of(orderItem));
        return order;
    }

    private Order orderWithTwoRecipeItems() {
        Order order = orderWithSingleRecipeItem("2.000");
        return order;
    }

    private Recipe recipeFor(Order order, InventoryItem item, String amount) {
        return recipeFor(order, item, amount, null, null);
    }

    private Recipe recipeFor(Order order, InventoryItem first, String firstAmount, InventoryItem second, String secondAmount) {
        Recipe recipe = new Recipe();
        RecipeItem firstItem = new RecipeItem();
        firstItem.setInventoryItem(first);
        firstItem.setAmount(new BigDecimal(firstAmount));
        if (second == null) {
            recipe.setRecipeItems(List.of(firstItem));
            return recipe;
        }
        RecipeItem secondItem = new RecipeItem();
        secondItem.setInventoryItem(second);
        secondItem.setAmount(new BigDecimal(secondAmount));
        recipe.setRecipeItems(List.of(firstItem, secondItem));
        return recipe;
    }

    private Order baseOrder() {
        Branch branch = new Branch();
        branch.setId(1L);
        Order order = new Order();
        order.setId(7L);
        order.setBranch(branch);
        return order;
    }

    private InventoryItem inventory(Long id, String quantity) {
        Branch branch = new Branch();
        branch.setId(1L);
        InventoryItem item = new InventoryItem();
        item.setId(id);
        item.setBranch(branch);
        item.setIngredientName("Ingredient " + id);
        item.setQuantity(new BigDecimal(quantity));
        item.setMinThreshold(BigDecimal.ZERO);
        item.setUnit("kg");
        return item;
    }

    private User user() {
        User user = new User();
        user.setId(9L);
        return user;
    }
}
