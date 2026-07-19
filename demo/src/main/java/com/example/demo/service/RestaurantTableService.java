package com.example.demo.service;

import com.example.demo.exception.BusinessValidationException;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.model.Branch;
import com.example.demo.model.Order;
import com.example.demo.model.RestaurantTable;
import com.example.demo.model.enums.OrderStatus;
import com.example.demo.model.enums.TableStatus;
import com.example.demo.repository.OrderRepository;
import com.example.demo.repository.RestaurantTableRepository;
import com.example.demo.security.BranchAccessService;
import com.example.demo.security.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RestaurantTableService {
    private final RestaurantTableRepository tableRepository;
    private final OrderRepository orderRepository;
    private final BranchAccessService branchAccessService;
    private final CurrentUserService currentUserService;

    public List<RestaurantTable> listForCurrentBranch() {
        return tableRepository.findByBranchId(branchAccessService.requireScopedBranchId());
    }

    public RestaurantTable requireTableForCurrentBranch(Long id) {
        return tableRepository.findByIdAndBranchId(id, branchAccessService.requireScopedBranchId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy dữ liệu hoặc bạn không có quyền truy cập."));
    }

    @Transactional
    public void save(Long id, String tableNumber, TableStatus status) {
        Branch branch = currentUserService.requireCurrentBranch();
        RestaurantTable table = id == null || id <= 0 ? new RestaurantTable() : requireTableForCurrentBranch(id);
        if (id == null || id <= 0) {
            table.setBranch(branch);
        }
        table.setTableNumber(tableNumber);
        table.setStatus(status);
        tableRepository.save(table);
    }

    @Transactional
    public void delete(Long id) {
        RestaurantTable table = requireTableForCurrentBranch(id);
        List<Order> activeOrders = orderRepository.findByBranchIdAndTableIdAndStatusNot(
                branchAccessService.requireScopedBranchId(), id, OrderStatus.COMPLETED);
        boolean hasBlockingOrder = activeOrders.stream().anyMatch(o -> o.getStatus() != OrderStatus.CANCELLED);
        if (hasBlockingOrder) {
            throw new BusinessValidationException("Không thể xóa bàn đang có đơn hoạt động.");
        }
        tableRepository.delete(table);
    }
}
