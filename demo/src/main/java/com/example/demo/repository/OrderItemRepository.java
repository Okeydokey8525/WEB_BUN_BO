package com.example.demo.repository;

import com.example.demo.model.OrderItem;
import com.example.demo.model.enums.OrderItemStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    List<OrderItem> findByStatus(OrderItemStatus status);
    List<OrderItem> findByOrderBranchIdAndStatus(Long branchId, OrderItemStatus status);
    List<OrderItem> findByOrderBranchIdAndStatusIn(Long branchId, List<OrderItemStatus> statuses);
    Optional<OrderItem> findByIdAndOrderBranchId(Long id, Long branchId);
}
