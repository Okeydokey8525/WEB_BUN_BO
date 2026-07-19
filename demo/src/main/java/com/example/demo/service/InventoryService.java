package com.example.demo.service;

import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.model.InventoryItem;
import com.example.demo.repository.InventoryRepository;
import com.example.demo.security.BranchAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class InventoryService {
    private final InventoryRepository inventoryRepository;
    private final BranchAccessService branchAccessService;

    public List<InventoryItem> listForCurrentBranch() {
        return inventoryRepository.findByBranchId(branchAccessService.requireScopedBranchId());
    }

    public List<InventoryItem> lowStockForCurrentBranch() {
        return inventoryRepository.findLowStockItemsByBranch(branchAccessService.requireScopedBranchId());
    }

    @Transactional
    public void updateQuantity(Long itemId, Double quantity) {
        InventoryItem item = inventoryRepository.findByIdAndBranchId(itemId, branchAccessService.requireScopedBranchId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy dữ liệu hoặc bạn không có quyền truy cập."));
        item.setQuantity(quantity);
    }
}
