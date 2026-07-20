package com.example.demo.repository;

import com.example.demo.model.InventoryTransaction;
import com.example.demo.model.enums.InventoryTransactionType;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
