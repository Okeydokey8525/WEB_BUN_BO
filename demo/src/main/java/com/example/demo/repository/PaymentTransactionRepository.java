package com.example.demo.repository;

import com.example.demo.model.PaymentTransaction;
import com.example.demo.model.enums.PaymentTransactionStatus;
import com.example.demo.model.enums.PaymentTransactionType;
import com.example.demo.repository.projection.ShiftPaymentAggregate;
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

    @Query(value = "select "
            + "coalesce(sum(case when t.transaction_type = 'PAYMENT' then t.amount else 0 end), 0) as \"grossPaymentTotal\", "
            + "coalesce(sum(case when t.transaction_type = 'REFUND' then t.amount else 0 end), 0) as \"refundTotal\", "
            + "coalesce(sum(case when t.transaction_type = 'PAYMENT' and t.payment_method = 'CASH' then t.amount else 0 end), 0) as \"cashPaymentTotal\", "
            + "coalesce(sum(case when t.transaction_type = 'REFUND' and t.payment_method = 'CASH' then t.amount else 0 end), 0) as \"cashRefundTotal\", "
            + "coalesce(sum(case when t.transaction_type = 'PAYMENT' and t.payment_method = 'VIETQR' then t.amount else 0 end), 0) as \"transferPaymentTotal\", "
            + "coalesce(sum(case when t.transaction_type = 'REFUND' and t.payment_method = 'VIETQR' then t.amount else 0 end), 0) as \"transferRefundTotal\", "
            + "cast(0 as numeric(19, 0)) as \"cardPaymentTotal\", cast(0 as numeric(19, 0)) as \"cardRefundTotal\", "
            + "count(distinct case when t.transaction_type = 'PAYMENT' then t.order_id end) as \"distinctOrderCount\" "
            + "from payment_transactions t where t.work_shift_id = :shiftId and t.status = 'COMPLETED'",
            nativeQuery = true)
    ShiftPaymentAggregate aggregateCompletedTransactionsByShiftId(@Param("shiftId") Long shiftId);
}
