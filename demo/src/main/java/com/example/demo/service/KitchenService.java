package com.example.demo.service;

import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.model.Order;
import com.example.demo.model.OrderItem;
import com.example.demo.model.enums.OrderItemStatus;
import com.example.demo.model.enums.OrderStatus;
import com.example.demo.repository.OrderItemRepository;
import com.example.demo.security.BranchAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class KitchenService {
    private final OrderItemRepository orderItemRepository;
    private final BranchAccessService branchAccessService;
    private final OrderItemStateTransitionService orderItemStateTransitionService;

    public List<OrderItem> pendingItemsForCurrentBranch() {
        return orderItemRepository.findByOrderBranchIdAndStatusIn(branchAccessService.requireScopedBranchId(), List.of(OrderItemStatus.PENDING, OrderItemStatus.PREPARING));
    }

    public List<OrderItem> readyItemsForCurrentBranch() {
        return orderItemRepository.findByOrderBranchIdAndStatus(branchAccessService.requireScopedBranchId(), OrderItemStatus.READY);
    }

    @Transactional
    public void markReady(Long id) {
        OrderItem item = requireItem(id);
        OrderItemStatus target = item.getStatus() == OrderItemStatus.PENDING ? OrderItemStatus.PREPARING : OrderItemStatus.READY;
        orderItemStateTransitionService.validate(item.getStatus(), target);
        item.setStatus(target);
        if (target == OrderItemStatus.PREPARING) {
            item.getOrder().setStatus(OrderStatus.COOKING);
        }
        if (target == OrderItemStatus.READY) {
            synchronizeOrderFromItems(item.getOrder());
        }
    }

    @Transactional
    public void markServed(Long id) {
        OrderItem item = requireItem(id);
        orderItemStateTransitionService.validate(item.getStatus(), OrderItemStatus.SERVED);
        item.setStatus(OrderItemStatus.SERVED);
        synchronizeOrderFromItems(item.getOrder());
    }

    private OrderItem requireItem(Long id) {
        return orderItemRepository.findByIdAndOrderBranchId(id, branchAccessService.requireScopedBranchId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy dữ liệu hoặc bạn không có quyền truy cập."));
    }

    private void synchronizeOrderFromItems(Order order) {
        List<OrderItem> activeItems = order.getOrderItems().stream()
                .filter(item -> item.getStatus() != OrderItemStatus.CANCELLED)
                .toList();
        if (!activeItems.isEmpty() && activeItems.stream().allMatch(item -> item.getStatus() == OrderItemStatus.SERVED)) {
            order.setStatus(OrderStatus.SERVED);
        } else if (!activeItems.isEmpty() && activeItems.stream().allMatch(item -> item.getStatus() == OrderItemStatus.READY || item.getStatus() == OrderItemStatus.SERVED)) {
            order.setStatus(OrderStatus.READY);
        }
    }
}
