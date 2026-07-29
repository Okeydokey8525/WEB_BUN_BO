package com.example.demo.repository;

import com.example.demo.model.OrderItem;
import com.example.demo.model.enums.OrderItemStatus;
import com.example.demo.repository.projection.TopDishProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    List<OrderItem> findByStatus(OrderItemStatus status);
    List<OrderItem> findByOrderBranchIdAndStatus(Long branchId, OrderItemStatus status);
    List<OrderItem> findByOrderBranchIdAndStatusIn(Long branchId, List<OrderItemStatus> statuses);
    Optional<OrderItem> findByIdAndOrderBranchId(Long id, Long branchId);

    @Query("select oi.dish.id as dishId, oi.dishNameSnapshot as dishName, "
            + "sum(oi.quantity) as quantitySold, sum(oi.lineTotal) as revenue, "
            + "count(distinct oi.order.id) as orderCount "
            + "from OrderItem oi where oi.order.branch.id = :branchId "
            + "and oi.status <> com.example.demo.model.enums.OrderItemStatus.CANCELLED "
            + "and exists (select pt.id from PaymentTransaction pt where pt.order = oi.order "
            + "and pt.branch.id = :branchId "
            + "and pt.transactionType = com.example.demo.model.enums.PaymentTransactionType.PAYMENT "
            + "and pt.status = com.example.demo.model.enums.PaymentTransactionStatus.COMPLETED "
            + "and pt.completedAt >= :fromInclusive and pt.completedAt < :toExclusive) "
            + "group by oi.dish.id, oi.dishNameSnapshot "
            + "order by sum(oi.quantity) desc, sum(oi.lineTotal) desc, oi.dishNameSnapshot asc")
    List<TopDishProjection> aggregateTopDishesByBranchAndPaidAt(
            @Param("branchId") Long branchId,
            @Param("fromInclusive") LocalDateTime fromInclusive,
            @Param("toExclusive") LocalDateTime toExclusive,
            Pageable pageable);
}
