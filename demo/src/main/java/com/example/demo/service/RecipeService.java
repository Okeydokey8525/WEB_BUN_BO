package com.example.demo.service;

import com.example.demo.dto.request.CreateRecipeRequest;
import com.example.demo.dto.request.RecipeItemRequest;
import com.example.demo.exception.BusinessValidationException;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.model.Dish;
import com.example.demo.model.InventoryItem;
import com.example.demo.model.Recipe;
import com.example.demo.model.RecipeItem;
import com.example.demo.repository.DishRepository;
import com.example.demo.repository.InventoryRepository;
import com.example.demo.repository.RecipeItemRepository;
import com.example.demo.repository.RecipeRepository;
import com.example.demo.security.BranchAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RecipeService {
    private final RecipeRepository recipeRepository;
    private final RecipeItemRepository recipeItemRepository;
    private final DishRepository dishRepository;
    private final InventoryRepository inventoryRepository;
    private final BranchAccessService branchAccessService;

    @Transactional(readOnly = true)
    public List<Recipe> getRecipesForCurrentBranch() {
        Long branchId = branchAccessService.requireScopedBranchId();
        return recipeRepository.findAll().stream()
                .filter(recipe -> recipe.getBranch() != null && branchId.equals(recipe.getBranch().getId()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Recipe getRecipeForDish(Long dishId) {
        Long branchId = branchAccessService.requireScopedBranchId();
        return recipeRepository.findByDishIdAndBranchId(dishId, branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy công thức hoặc bạn không có quyền truy cập."));
    }

    @Transactional
    public Recipe createOrUpdateRecipe(CreateRecipeRequest request) {
        Long branchId = branchAccessService.requireScopedBranchId();
        Dish dish = dishRepository.findByIdAndBranchId(request.dishId(), branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy món ăn hoặc bạn không có quyền truy cập."));
        return recipeRepository.findByDishIdAndBranchId(dish.getId(), branchId)
                .orElseGet(() -> {
                    Recipe recipe = new Recipe();
                    recipe.setDish(dish);
                    recipe.setBranch(dish.getBranch());
                    return recipeRepository.save(recipe);
                });
    }

    @Transactional
    public RecipeItem addRecipeItem(Long recipeId, RecipeItemRequest request) {
        Long branchId = branchAccessService.requireScopedBranchId();
        Recipe recipe = requireRecipe(recipeId, branchId);
        InventoryItem inventoryItem = inventoryRepository.findByIdAndBranchId(request.inventoryItemId(), branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nguyên liệu hoặc bạn không có quyền truy cập."));
        if (recipeItemRepository.existsByRecipeIdAndInventoryItemIdAndRecipeBranchId(recipeId, inventoryItem.getId(), branchId)) {
            throw new BusinessValidationException("Nguyên liệu đã tồn tại trong công thức.");
        }
        RecipeItem item = new RecipeItem();
        item.setRecipe(recipe);
        item.setInventoryItem(inventoryItem);
        item.setIngredientName(inventoryItem.getIngredientName());
        item.setUnit(inventoryItem.getUnit());
        item.setAmount(request.quantityRequired());
        RecipeItem saved = recipeItemRepository.save(item);
        recipe.getRecipeItems().add(saved);
        return saved;
    }

    @Transactional
    public void removeRecipeItem(Long recipeId, Long itemId) {
        Long branchId = branchAccessService.requireScopedBranchId();
        RecipeItem item = recipeItemRepository.findByIdAndRecipeIdAndRecipeBranchId(itemId, recipeId, branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nguyên liệu công thức hoặc bạn không có quyền truy cập."));
        recipeItemRepository.delete(item);
    }

    private Recipe requireRecipe(Long recipeId, Long branchId) {
        return recipeRepository.findById(recipeId)
                .filter(recipe -> recipe.getBranch() != null && branchId.equals(recipe.getBranch().getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy công thức hoặc bạn không có quyền truy cập."));
    }
}
