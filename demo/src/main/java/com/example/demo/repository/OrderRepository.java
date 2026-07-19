package com.example.demo.repository;

import com.example.demo.model.Order;
import com.example.demo.model.enums.OrderStatus;
import com.example.demo.model.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByStatus(OrderStatus status);
    List<Order> findAllByOrderByCreatedAtDesc();
    List<Order> findByTableIdAndStatusNot(Long tableId, OrderStatus status);
    boolean existsByTableIdAndStatusNotIn(Long tableId, List<OrderStatus> statuses);
    
    List<Order> findByBranchId(Long branchId);
    List<Order> findByBranchIdAndStatus(Long branchId, OrderStatus status);
    List<Order> findByBranchIdOrderByCreatedAtDesc(Long branchId);
    List<Order> findByBranchIdAndTableIdAndStatusNot(Long branchId, Long tableId, OrderStatus status);
    List<Order> findByPaymentStatusAndStatusNot(PaymentStatus paymentStatus, OrderStatus status);
    List<Order> findByBranchIdAndPaymentStatusAndStatusNot(Long branchId, PaymentStatus paymentStatus, OrderStatus status);
    Optional<Order> findByIdAndBranchId(Long id, Long branchId);
    Optional<Order> findByIdAndPublicToken(Long id, String publicToken);
}
