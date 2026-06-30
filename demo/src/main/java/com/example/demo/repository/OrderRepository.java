package com.example.demo.repository;

import com.example.demo.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByStatus(String status);
    List<Order> findAllByOrderByCreatedAtDesc();
    List<Order> findByTableIdAndStatusNot(Long tableId, String status);
    
    // Multi-branch queries
    List<Order> findByBranchId(Long branchId);
    List<Order> findByBranchIdAndStatus(Long branchId, String status);
    List<Order> findByBranchIdOrderByCreatedAtDesc(Long branchId);
    List<Order> findByBranchIdAndTableIdAndStatusNot(Long branchId, Long tableId, String status);
    List<Order> findByPaymentStatusAndStatusNot(String paymentStatus, String status);
}
