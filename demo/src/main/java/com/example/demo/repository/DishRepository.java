package com.example.demo.repository;

import com.example.demo.model.Dish;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface DishRepository extends JpaRepository<Dish, Long> {
    List<Dish> findByCategory(String category);
    List<Dish> findByIsAvailableTrue();
    List<Dish> findByCategoryAndIsAvailableTrue(String category);
    
    // Multi-branch queries
    List<Dish> findByBranchId(Long branchId);
    List<Dish> findByBranchIdAndCategory(Long branchId, String category);
    List<Dish> findByBranchIdAndIsAvailableTrue(Long branchId);
    List<Dish> findByBranchIdAndCategoryAndIsAvailableTrue(Long branchId, String category);
    Optional<Dish> findByIdAndBranchId(Long id, Long branchId);
}
