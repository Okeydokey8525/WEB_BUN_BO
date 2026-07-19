package com.example.demo.repository;

import com.example.demo.model.PaymentTransaction;
import com.example.demo.model.enums.PaymentTransactionStatus;
import com.example.demo.model.enums.PaymentTransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {
    List<PaymentTransaction> findByOrderIdAndBranchIdOrderByCreatedAtAsc(Long orderId, Long branchId);

    List<PaymentTransaction> findByBranchIdOrderByCreatedAtDesc(Long branchId);

    boolean existsByOrderIdAndBranchIdAndTransactionTypeAndStatus(
            Long orderId, Long branchId, PaymentTransactionType transactionType, PaymentTransactionStatus status);

    @Query("select coalesce(sum(t.amount), 0) from PaymentTransaction t "
            + "where t.order.id = :orderId and t.branch.id = :branchId "
            + "and t.transactionType = :type and t.status = :status")
    BigDecimal sumAmountByOrderIdAndBranchIdAndTypeAndStatus(
            @Param("orderId") Long orderId,
            @Param("branchId") Long branchId,
            @Param("type") PaymentTransactionType type,
            @Param("status") PaymentTransactionStatus status);
}
