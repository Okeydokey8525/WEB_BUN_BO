package com.example.demo.controller;

import com.example.demo.dto.request.CreateRecipeRequest;
import com.example.demo.dto.request.RecipeItemRequest;
import com.example.demo.exception.BusinessValidationException;
import com.example.demo.service.InventoryService;
import com.example.demo.service.RecipeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/recipes")
@RequiredArgsConstructor
public class RecipeController {
    private final RecipeService recipeService;
    private final InventoryService inventoryService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("recipes", recipeService.getRecipesForCurrentBranch());
        model.addAttribute("inventoryOptions", inventoryService.listForCurrentBranch());
        return inventoryModel(model);
    }

    @GetMapping("/dish/{dishId}")
    public String byDish(@PathVariable Long dishId, Model model) {
        model.addAttribute("recipe", recipeService.getRecipeForDish(dishId));
        model.addAttribute("inventoryOptions", inventoryService.listForCurrentBranch());
        return inventoryModel(model);
    }

    @PostMapping("/dish/{dishId}")
    public String create(@PathVariable Long dishId, @Valid @ModelAttribute CreateRecipeRequest request,
                         RedirectAttributes redirectAttributes) {
        if (!dishId.equals(request.dishId())) {
            throw new BusinessValidationException("Món ăn không hợp lệ.");
        }
        recipeService.createOrUpdateRecipe(request);
        redirectAttributes.addFlashAttribute("successMessage", "Đã lưu công thức.");
        return "redirect:/admin/recipes/dish/" + dishId;
    }

    @PostMapping("/{recipeId}/items")
    public String addItem(@PathVariable Long recipeId, @Valid @ModelAttribute RecipeItemRequest request,
                          RedirectAttributes redirectAttributes) {
        recipeService.addRecipeItem(recipeId, request);
        redirectAttributes.addFlashAttribute("successMessage", "Đã thêm nguyên liệu.");
        return "redirect:/admin/recipes";
    }

    @PostMapping("/{recipeId}/items/{itemId}/delete")
    public String deleteItem(@PathVariable Long recipeId, @PathVariable Long itemId,
                             RedirectAttributes redirectAttributes) {
        recipeService.removeRecipeItem(recipeId, itemId);
        redirectAttributes.addFlashAttribute("successMessage", "Đã xóa nguyên liệu.");
        return "redirect:/admin/recipes";
    }

    private String inventoryModel(Model model) {
        model.addAttribute("inventoryItems", inventoryService.listForCurrentBranch());
        model.addAttribute("lowStockCount", inventoryService.lowStockForCurrentBranch().size());
        return "admin/inventory";
    }
}
