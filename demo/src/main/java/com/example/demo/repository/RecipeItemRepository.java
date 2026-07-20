package com.example.demo.repository;

import com.example.demo.model.RecipeItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecipeItemRepository extends JpaRepository<RecipeItem, Long> {
    List<RecipeItem> findByRecipeIdAndRecipeBranchId(Long recipeId, Long branchId);
}
