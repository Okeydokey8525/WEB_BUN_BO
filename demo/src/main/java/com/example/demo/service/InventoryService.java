package com.example.demo.service;

import com.example.demo.exception.BusinessValidationException;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.model.InventoryItem;
import com.example.demo.model.InventoryTransaction;
import com.example.demo.model.Order;
import com.example.demo.model.OrderItem;
import com.example.demo.model.Recipe;
import com.example.demo.model.RecipeItem;
import com.example.demo.model.User;
import com.example.demo.model.enums.InventoryTransactionType;
import com.example.demo.model.enums.AuditAction;
import com.example.demo.model.enums.AuditEntityType;
import com.example.demo.repository.InventoryRepository;
import com.example.demo.repository.InventoryTransactionRepository;
import com.example.demo.repository.RecipeRepository;
import com.example.demo.security.BranchAccessService;
import com.example.demo.security.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class InventoryService {
    private static final String ORDER_REFERENCE_TYPE = "ORDER";
    private final InventoryRepository inventoryRepository;
    private final InventoryTransactionRepository inventoryTransactionRepository;
    private final RecipeRepository recipeRepository;
    private final BranchAccessService branchAccessService;
    private final CurrentUserService currentUserService;
    private final AuditService auditService;

    public List<InventoryItem> listForCurrentBranch() {
        return inventoryRepository.findByBranchId(branchAccessService.requireScopedBranchId());
    }

    public List<InventoryItem> lowStockForCurrentBranch() {
        return inventoryRepository.findLowStockItemsByBranch(branchAccessService.requireScopedBranchId());
    }

    @Transactional(readOnly = true)
    public InventoryItem getForCurrentBranch(Long itemId) {
        return requireItemForCurrentBranch(itemId);
    }

    @Transactional(readOnly = true)
    public List<InventoryTransaction> getTransactionsForCurrentBranch(Long itemId) {
        InventoryItem item = requireItemForCurrentBranch(itemId);
        return inventoryTransactionRepository.findByInventoryItemIdAndBranchIdOrderByCreatedAtDesc(
                item.getId(), item.getBranch().getId());
    }

    @Transactional
    public void updateQuantity(Long itemId, BigDecimal quantity) {
        InventoryItem item = inventoryRepository.findByIdAndBranchId(itemId, branchAccessService.requireScopedBranchId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy dữ liệu hoặc bạn không có quyền truy cập."));
        item.setQuantity(quantity);
    }

    @Transactional
    public InventoryTransaction stockIn(Long itemId, BigDecimal quantity, String reason) {
        requirePositiveQuantity(quantity);
        InventoryItem item = requireItemForCurrentBranch(itemId);
        BigDecimal before = item.getQuantity();
        BigDecimal after = before.add(quantity);
        item.setQuantity(after);
        inventoryRepository.save(item);

        InventoryTransaction saved = inventoryTransactionRepository.save(transaction(
                item, InventoryTransactionType.STOCK_IN, quantity, before, after, null, null, reason));
        auditService.record(AuditAction.STOCK_IN, AuditEntityType.INVENTORY_TRANSACTION, saved.getId(),
                "Stock received", Map.of("inventoryItemId", item.getId(), "quantity", quantity,
                        "beforeQuantity", before, "afterQuantity", after, "reason", reason));
        return saved;
    }

    @Transactional
    public InventoryTransaction adjustStock(Long itemId, BigDecimal adjustment, String reason) {
        if (adjustment == null || adjustment.signum() == 0) {
            throw new BusinessValidationException("Điều chỉnh tồn kho không được bằng 0.");
        }
        if (reason == null || reason.isBlank()) {
            throw new BusinessValidationException("Lý do điều chỉnh tồn kho là bắt buộc.");
        }
        InventoryItem item = requireItemForCurrentBranch(itemId);
        BigDecimal before = item.getQuantity();
        BigDecimal after = before.add(adjustment);
        if (after.signum() < 0) {
            throw new BusinessValidationException("Điều chỉnh sẽ làm tồn kho âm.");
        }
        item.setQuantity(after);
        inventoryRepository.save(item);

        InventoryTransactionType type = adjustment.signum() > 0
                ? InventoryTransactionType.ADJUSTMENT_IN
                : InventoryTransactionType.ADJUSTMENT_OUT;
        InventoryTransaction saved = inventoryTransactionRepository.save(transaction(
                item, type, adjustment.abs(), before, after, null, null, reason));
        auditService.record(AuditAction.STOCK_ADJUST, AuditEntityType.INVENTORY_TRANSACTION, saved.getId(),
                "Stock adjusted", Map.of("inventoryItemId", item.getId(), "quantity", adjustment,
                        "beforeQuantity", before, "afterQuantity", after, "reason", reason));
        return saved;
    }

    @Transactional(readOnly = true)
    public boolean canFulfillOrder(Order order) {
        requireOrderBranchAccess(order);
        return requirementsFor(order).values().stream()
                .allMatch(requirement -> requirement.item().getQuantity().compareTo(requirement.quantity()) >= 0);
    }

    @Transactional(readOnly = true)
    public boolean hasConsumptionForOrder(Order order) {
        requireOrderBranchAccess(order);
        if (order.getId() == null) {
            return false;
        }
        return !inventoryTransactionRepository.findByBranchIdAndReferenceTypeAndReferenceIdAndTransactionType(
                order.getBranch().getId(), ORDER_REFERENCE_TYPE, order.getId(),
                InventoryTransactionType.ORDER_CONSUMPTION).isEmpty();
    }

    @Transactional
    public List<InventoryTransaction> consumeForOrder(Order order) {
        requireOrderBranchAccess(order);
        if (order.getId() == null) {
            throw new BusinessValidationException("Đơn hàng phải được lưu trước khi trừ kho.");
        }

        List<PreparedInventoryChange> changes = new ArrayList<>();
        for (RequiredInventory requirement : requirementsFor(order).values()) {
            InventoryItem item = requireItemForCurrentBranch(requirement.item().getId());
            if (inventoryTransactionRepository
                    .existsByInventoryItemIdAndBranchIdAndReferenceTypeAndReferenceIdAndTransactionType(
                            item.getId(), item.getBranch().getId(), ORDER_REFERENCE_TYPE, order.getId(),
                            InventoryTransactionType.ORDER_CONSUMPTION)) {
                throw new BusinessValidationException("Đơn hàng đã được trừ kho.");
            }
            if (item.getQuantity().compareTo(requirement.quantity()) < 0) {
                throw new BusinessValidationException("Không đủ tồn kho để xác nhận đơn hàng.");
            }
            changes.add(new PreparedInventoryChange(item, requirement.quantity(), item.getQuantity(),
                    item.getQuantity().subtract(requirement.quantity())));
        }

        List<InventoryTransaction> transactions = new ArrayList<>();
        for (PreparedInventoryChange change : changes) {
            change.item().setQuantity(change.after());
            inventoryRepository.save(change.item());
            transactions.add(inventoryTransactionRepository.save(transaction(
                    change.item(), InventoryTransactionType.ORDER_CONSUMPTION, change.quantity(),
                    change.before(), change.after(), ORDER_REFERENCE_TYPE, order.getId(),
                    "Order consumption")));
        }
        auditService.record(AuditAction.ORDER_STOCK_CONSUMED, AuditEntityType.ORDER, order.getId(),
                "Order stock consumed", Map.of("transactionCount", transactions.size()));
        return transactions;
    }

    @Transactional
    public List<InventoryTransaction> reverseConsumptionForOrder(Order order) {
        requireOrderBranchAccess(order);
        if (order.getId() == null) {
            throw new BusinessValidationException("Đơn hàng phải được lưu trước khi hoàn kho.");
        }
        Long branchId = order.getBranch().getId();
        List<InventoryTransaction> consumptions = inventoryTransactionRepository
                .findByBranchIdAndReferenceTypeAndReferenceIdAndTransactionType(
                        branchId, ORDER_REFERENCE_TYPE, order.getId(), InventoryTransactionType.ORDER_CONSUMPTION);
        if (consumptions.isEmpty()) {
            throw new BusinessValidationException("Đơn hàng chưa có giao dịch trừ kho để hoàn.");
        }

        List<PreparedInventoryChange> changes = new ArrayList<>();
        for (InventoryTransaction consumption : consumptions) {
            InventoryItem item = requireItemForCurrentBranch(consumption.getInventoryItem().getId());
            if (inventoryTransactionRepository
                    .existsByInventoryItemIdAndBranchIdAndReferenceTypeAndReferenceIdAndTransactionType(
                            item.getId(), branchId, ORDER_REFERENCE_TYPE, order.getId(),
                            InventoryTransactionType.ORDER_REVERSAL)) {
                throw new BusinessValidationException("Đơn hàng đã được hoàn kho.");
            }
            BigDecimal before = item.getQuantity();
            changes.add(new PreparedInventoryChange(item, consumption.getQuantity(), before,
                    before.add(consumption.getQuantity())));
        }

        List<InventoryTransaction> transactions = new ArrayList<>();
        for (PreparedInventoryChange change : changes) {
            change.item().setQuantity(change.after());
            inventoryRepository.save(change.item());
            transactions.add(inventoryTransactionRepository.save(transaction(
                    change.item(), InventoryTransactionType.ORDER_REVERSAL, change.quantity(),
                    change.before(), change.after(), ORDER_REFERENCE_TYPE, order.getId(),
                    "Order consumption reversal")));
        }
        auditService.record(AuditAction.ORDER_STOCK_REVERSED, AuditEntityType.ORDER, order.getId(),
                "Order stock reversed", Map.of("transactionCount", transactions.size()));
        return transactions;
    }

    private InventoryItem requireItemForCurrentBranch(Long itemId) {
        return inventoryRepository.findByIdAndBranchId(itemId, branchAccessService.requireScopedBranchId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy dữ liệu hoặc bạn không có quyền truy cập."));
    }

    private void requirePositiveQuantity(BigDecimal quantity) {
        if (quantity == null || quantity.signum() <= 0) {
            throw new BusinessValidationException("Số lượng phải lớn hơn 0.");
        }
    }

    private void requireOrderBranchAccess(Order order) {
        if (order == null || order.getBranch() == null || order.getBranch().getId() == null) {
            throw new BusinessValidationException("Đơn hàng phải thuộc một chi nhánh.");
        }
        branchAccessService.requireBranchAccess(order.getBranch().getId());
    }

    private Map<Long, RequiredInventory> requirementsFor(Order order) {
        Map<Long, RequiredInventory> requirements = new HashMap<>();
        for (OrderItem orderItem : order.getOrderItems()) {
            if (orderItem.getDish() == null || orderItem.getDish().getId() == null
                    || orderItem.getQuantity() == null || orderItem.getQuantity() <= 0) {
                throw new BusinessValidationException("Đơn hàng có món không hợp lệ.");
            }
            Recipe recipe = recipeRepository.findByDishIdAndBranchId(
                            orderItem.getDish().getId(), order.getBranch().getId())
                    .orElseThrow(() -> new BusinessValidationException("Món ăn chưa có công thức tại chi nhánh này."));
            for (RecipeItem recipeItem : recipe.getRecipeItems()) {
                InventoryItem item = recipeItem.getInventoryItem();
                if (item == null || item.getId() == null || item.getBranch() == null
                        || !order.getBranch().getId().equals(item.getBranch().getId())) {
                    throw new BusinessValidationException("Công thức tham chiếu nguyên liệu không hợp lệ.");
                }
                if (recipeItem.getAmount() == null || recipeItem.getAmount().signum() <= 0) {
                    throw new BusinessValidationException("Số lượng nguyên liệu trong công thức không hợp lệ.");
                }
                BigDecimal required = recipeItem.getAmount().multiply(BigDecimal.valueOf(orderItem.getQuantity()));
                requirements.merge(item.getId(), new RequiredInventory(item, required),
                        (existing, added) -> new RequiredInventory(existing.item(), existing.quantity().add(added.quantity())));
            }
        }
        return requirements;
    }

    private InventoryTransaction transaction(
            InventoryItem item,
            InventoryTransactionType type,
            BigDecimal quantity,
            BigDecimal before,
            BigDecimal after,
            String referenceType,
            Long referenceId,
            String reason) {
        User actor = currentUserService.getCurrentUser();
        InventoryTransaction transaction = new InventoryTransaction();
        transaction.setInventoryItem(item);
        transaction.setBranch(item.getBranch());
        transaction.setTransactionType(type);
        transaction.setQuantity(quantity);
        transaction.setQuantityBefore(before);
        transaction.setQuantityAfter(after);
        transaction.setReferenceType(referenceType);
        transaction.setReferenceId(referenceId);
        transaction.setReason(reason);
        transaction.setCreatedBy(actor);
        return transaction;
    }

    private record RequiredInventory(InventoryItem item, BigDecimal quantity) {
    }

    private record PreparedInventoryChange(
            InventoryItem item,
            BigDecimal quantity,
            BigDecimal before,
            BigDecimal after) {
    }
}
