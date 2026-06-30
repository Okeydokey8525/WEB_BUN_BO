package com.example.demo.repository;

import com.example.demo.model.InventoryItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryRepository extends JpaRepository<InventoryItem, Long> {
    
    @Query("SELECT i FROM InventoryItem i WHERE i.quantity < i.minThreshold")
    List<InventoryItem> findLowStockItems();
    
    // Multi-branch queries
    List<InventoryItem> findByBranchId(Long branchId);
    
    Optional<InventoryItem> findByBranchIdAndIngredientName(Long branchId, String ingredientName);
    
    @Query("SELECT i FROM InventoryItem i WHERE i.branch.id = :branchId AND i.quantity < i.minThreshold")
    List<InventoryItem> findLowStockItemsByBranch(@Param("branchId") Long branchId);
}
