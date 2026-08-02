package com.example.demo.repository;

import com.example.demo.model.InventoryTransaction;
import com.example.demo.model.enums.InventoryTransactionType;
import com.example.demo.repository.projection.InventoryConsumptionProjection;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, Long> {
    List<InventoryTransaction> findByInventoryItemIdAndBranchIdOrderByCreatedAtDesc(
            Long inventoryItemId, Long branchId);

    List<InventoryTransaction> findByBranchIdAndReferenceTypeAndReferenceIdAndTransactionType(
            Long branchId,
            String referenceType,
            Long referenceId,
            InventoryTransactionType transactionType);

    boolean existsByInventoryItemIdAndBranchIdAndReferenceTypeAndReferenceIdAndTransactionType(
            Long inventoryItemId,
            Long branchId,
            String referenceType,
            Long referenceId,
            InventoryTransactionType transactionType);

    @Query("select t.inventoryItem.id as inventoryItemId, t.inventoryItem.ingredientName as inventoryItemName, "
            + "t.inventoryItem.unit as unit, "
            + "coalesce(sum(case when t.transactionType = com.example.demo.model.enums.InventoryTransactionType.ORDER_CONSUMPTION then t.quantity else 0 end), 0) as consumedQuantity, "
            + "coalesce(sum(case when t.transactionType = com.example.demo.model.enums.InventoryTransactionType.ORDER_REVERSAL then t.quantity else 0 end), 0) as reversedQuantity, "
            + "coalesce(sum(case when t.transactionType = com.example.demo.model.enums.InventoryTransactionType.WASTE then t.quantity else 0 end), 0) as wasteQuantity "
            + "from InventoryTransaction t where t.branch.id = :branchId and t.inventoryItem.branch.id = :branchId "
            + "and t.createdAt >= :fromInclusive and t.createdAt < :toExclusive "
            + "group by t.inventoryItem.id, t.inventoryItem.ingredientName, t.inventoryItem.unit "
            + "order by (sum(case when t.transactionType = com.example.demo.model.enums.InventoryTransactionType.ORDER_CONSUMPTION then t.quantity else 0 end) "
            + "- sum(case when t.transactionType = com.example.demo.model.enums.InventoryTransactionType.ORDER_REVERSAL then t.quantity else 0 end)) desc, "
            + "sum(case when t.transactionType = com.example.demo.model.enums.InventoryTransactionType.ORDER_CONSUMPTION then t.quantity else 0 end) desc, "
            + "t.inventoryItem.ingredientName asc")
    List<InventoryConsumptionProjection> aggregateConsumptionByBranchAndCreatedAt(
            @Param("branchId") Long branchId,
            @Param("fromInclusive") LocalDateTime fromInclusive,
            @Param("toExclusive") LocalDateTime toExclusive,
            Pageable pageable);
}
