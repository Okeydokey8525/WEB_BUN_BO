package com.example.demo.repository;

import com.example.demo.model.RestaurantTable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface RestaurantTableRepository extends JpaRepository<RestaurantTable, Long> {
    Optional<RestaurantTable> findByTableNumber(String tableNumber);
    
    // Multi-branch queries
    List<RestaurantTable> findByBranchId(Long branchId);
    Optional<RestaurantTable> findByBranchIdAndTableNumber(Long branchId, String tableNumber);
    Optional<RestaurantTable> findByIdAndBranchId(Long id, Long branchId);
}
