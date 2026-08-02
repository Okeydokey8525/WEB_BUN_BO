package com.example.demo;

import com.example.demo.dto.request.CreateRecipeRequest;
import com.example.demo.dto.request.RecipeItemRequest;
import com.example.demo.exception.BusinessValidationException;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.model.*;
import com.example.demo.repository.*;
import com.example.demo.security.BranchAccessService;
import com.example.demo.service.RecipeService;
import com.example.demo.service.AuditService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
class RecipeServiceTests {
    @Mock RecipeRepository recipeRepository;
    @Mock RecipeItemRepository recipeItemRepository;
    @Mock DishRepository dishRepository;
    @Mock InventoryRepository inventoryRepository;
    @Mock BranchAccessService branchAccessService;
    @Mock AuditService auditService;
    @InjectMocks RecipeService recipeService;

    @Test
    void createsRecipeForCurrentBranch() {
        Dish dish = dish(1L, 10L);
        when(branchAccessService.requireScopedBranchId()).thenReturn(10L);
        when(dishRepository.findByIdAndBranchId(1L, 10L)).thenReturn(Optional.of(dish));
        when(recipeRepository.findByDishIdAndBranchId(1L, 10L)).thenReturn(Optional.empty());
        when(recipeRepository.save(any(Recipe.class))).thenAnswer(i -> i.getArgument(0));
        assertEquals(dish, recipeService.createOrUpdateRecipe(new CreateRecipeRequest(1L)).getDish());
    }

    @Test
    void addItemRejectsDuplicateAndCrossBranchInventory() {
        Recipe recipe = recipe(3L, 10L);
        when(branchAccessService.requireScopedBranchId()).thenReturn(10L);
        when(recipeRepository.findById(3L)).thenReturn(Optional.of(recipe));
        when(inventoryRepository.findByIdAndBranchId(7L, 10L)).thenReturn(Optional.of(inventory(7L, 10L)));
        when(recipeItemRepository.existsByRecipeIdAndInventoryItemIdAndRecipeBranchId(3L, 7L, 10L)).thenReturn(true);
        assertThrows(BusinessValidationException.class, () -> recipeService.addRecipeItem(3L, new RecipeItemRequest(7L, BigDecimal.ONE)));
        when(inventoryRepository.findByIdAndBranchId(8L, 10L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> recipeService.addRecipeItem(3L, new RecipeItemRequest(8L, BigDecimal.ONE)));
    }

    @Test
    void addAndRemoveItemAreBranchScoped() {
        Recipe recipe = recipe(3L, 10L);
        InventoryItem inventory = inventory(7L, 10L);
        when(branchAccessService.requireScopedBranchId()).thenReturn(10L);
        when(recipeRepository.findById(3L)).thenReturn(Optional.of(recipe));
        when(inventoryRepository.findByIdAndBranchId(7L, 10L)).thenReturn(Optional.of(inventory));
        when(recipeItemRepository.existsByRecipeIdAndInventoryItemIdAndRecipeBranchId(3L, 7L, 10L)).thenReturn(false);
        when(recipeItemRepository.save(any(RecipeItem.class))).thenAnswer(i -> i.getArgument(0));
        assertEquals(inventory, recipeService.addRecipeItem(3L, new RecipeItemRequest(7L, BigDecimal.ONE)).getInventoryItem());
        RecipeItem item = new RecipeItem(); item.setId(9L);
        when(recipeItemRepository.findByIdAndRecipeIdAndRecipeBranchId(9L, 3L, 10L)).thenReturn(Optional.of(item));
        recipeService.removeRecipeItem(3L, 9L);
        verify(recipeItemRepository).delete(item);
    }

    @Test
    void invalidQuantityAndCrossBranchDishAreRejected() {
        when(branchAccessService.requireScopedBranchId()).thenReturn(10L);
        assertThrows(BusinessValidationException.class, () -> recipeService.addRecipeItem(3L, new RecipeItemRequest(7L, BigDecimal.ZERO)));
        when(dishRepository.findByIdAndBranchId(1L, 10L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> recipeService.createOrUpdateRecipe(new CreateRecipeRequest(1L)));
    }

    @Test
    void listingOnlyReturnsCurrentBranchRecipes() {
        when(branchAccessService.requireScopedBranchId()).thenReturn(10L);
        when(recipeRepository.findAll()).thenReturn(List.of(recipe(1L, 10L), recipe(2L, 20L)));
        assertEquals(1, recipeService.getRecipesForCurrentBranch().size());
    }

    private Recipe recipe(Long id, Long branchId) {
        Recipe recipe = new Recipe(); recipe.setId(id); recipe.setBranch(branch(branchId)); recipe.setRecipeItems(new java.util.ArrayList<>()); return recipe;
    }
    private Dish dish(Long id, Long branchId) { Dish dish = new Dish(); dish.setId(id); dish.setBranch(branch(branchId)); return dish; }
    private InventoryItem inventory(Long id, Long branchId) { InventoryItem item = new InventoryItem(); item.setId(id); item.setBranch(branch(branchId)); item.setIngredientName("I"); item.setUnit("kg"); return item; }
    private Branch branch(Long id) { Branch branch = new Branch(); branch.setId(id); return branch; }
}
