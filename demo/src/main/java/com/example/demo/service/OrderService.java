package com.example.demo.service;

import com.example.demo.dto.request.CreateOrderItemRequest;
import com.example.demo.dto.request.CreateOrderRequest;
import com.example.demo.dto.response.CreateOrderResult;
import com.example.demo.exception.BusinessValidationException;
import com.example.demo.exception.InvalidPaymentStateException;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.model.Dish;
import com.example.demo.model.Order;
import com.example.demo.model.OrderItem;
import com.example.demo.model.RestaurantTable;
import com.example.demo.model.enums.OrderItemStatus;
import com.example.demo.model.enums.OrderStatus;
import com.example.demo.model.enums.PaymentStatus;
import com.example.demo.model.enums.TableStatus;
import com.example.demo.repository.DishRepository;
import com.example.demo.repository.OrderRepository;
import com.example.demo.repository.RestaurantTableRepository;
import com.example.demo.security.BranchAccessService;
import com.example.demo.security.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {
    private final OrderRepository orderRepository;
    private final RestaurantTableRepository tableRepository;
    private final DishRepository dishRepository;
    private final BranchAccessService branchAccessService;
    private final CurrentUserService currentUserService;
    private final OrderPricingService orderPricingService;
    private final OrderStateTransitionService orderStateTransitionService;

    public List<Order> listForCurrentBranch() {
        return orderRepository.findByBranchIdOrderByCreatedAtDesc(branchAccessService.requireScopedBranchId());
    }

    public List<Order> unpaidForCurrentBranch() {
        return orderRepository.findByBranchIdAndPaymentStatusAndStatusNot(
                branchAccessService.requireScopedBranchId(), PaymentStatus.UNPAID, OrderStatus.CANCELLED);
    }

    public Order requireOrderForCurrentBranch(Long id) {
        return orderRepository.findByIdAndBranchId(id, branchAccessService.requireScopedBranchId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy dữ liệu hoặc bạn không có quyền truy cập."));
    }

    public List<Order> activeOrdersForTable(Long tableId) {
        Long branchId = branchAccessService.requireScopedBranchId();
        return orderRepository.findByBranchIdAndTableIdAndStatusNot(branchId, tableId, OrderStatus.COMPLETED).stream()
                .filter(o -> o.getStatus() != OrderStatus.CANCELLED)
                .toList();
    }

    @Transactional
    public CreateOrderResult createOrder(CreateOrderRequest request) {
        RestaurantTable table = tableRepository.findById(request.tableId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bàn ăn."));
        if (table.getBranch() == null) {
            throw new BusinessValidationException("Bàn chưa được gán chi nhánh.");
        }
        if (table.getStatus() == TableStatus.OUT_OF_SERVICE) {
            throw new BusinessValidationException("Bàn đang ngừng sử dụng.");
        }
        if (request.items() == null || request.items().isEmpty()) {
            throw new BusinessValidationException("Giỏ hàng không được rỗng.");
        }

        Order order = new Order();
        order.setTable(table);
        order.setBranch(table.getBranch());
        order.setCustomerName(request.customerName());
        order.setPaymentMethod(request.paymentMethod());
        order.setOrderType(request.orderType());
        order.setStatus(OrderStatus.PENDING);
        order.setPaymentStatus(PaymentStatus.UNPAID);
        order.setCreatedAt(LocalDateTime.now());

        for (CreateOrderItemRequest itemRequest : request.items()) {
            Dish dish = dishRepository.findByIdAndBranchId(itemRequest.dishId(), table.getBranch().getId())
                    .filter(Dish::isAvailable)
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy món ăn hoặc món không còn bán."));
            if (dish.getPrice() == null || dish.getPrice().signum() < 0) {
                throw new BusinessValidationException("Giá món không hợp lệ.");
            }
            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setDish(dish);
            orderItem.setDishNameSnapshot(dish.getName());
            orderItem.setQuantity(itemRequest.quantity());
            orderItem.setPrice(dish.getPrice());
            orderItem.setLineTotal(dish.getPrice().multiply(BigDecimal.valueOf(itemRequest.quantity())));
            orderItem.setNote(itemRequest.note());
            orderItem.setStatus(OrderItemStatus.PENDING);
            order.getOrderItems().add(orderItem);
        }

        orderPricingService.applyTotals(order);
        table.setStatus(TableStatus.ORDERING);
        tableRepository.save(table);
        Order savedOrder = orderRepository.save(order);
        return new CreateOrderResult(savedOrder.getId(), savedOrder.getPublicToken());
    }

    @Transactional
    public void updateStatus(Long id, OrderStatus status) {
        Order order = requireOrderForCurrentBranch(id);
        orderStateTransitionService.validate(order.getStatus(), status);
        order.setStatus(status);
        RestaurantTable table = order.getTable();
        if (table != null) {
            branchAccessService.requireBranchAccess(table.getBranch() == null ? null : table.getBranch().getId());
            if (status == OrderStatus.COMPLETED || status == OrderStatus.CANCELLED) {
                if (!orderRepository.existsByTableIdAndStatusNotIn(table.getId(), List.of(OrderStatus.COMPLETED, OrderStatus.CANCELLED))) {
                    table.setStatus(TableStatus.FREE);
                }
            } else if (status == OrderStatus.COOKING || status == OrderStatus.READY || status == OrderStatus.SERVED) {
                table.setStatus(status == OrderStatus.SERVED ? TableStatus.WAITING_PAYMENT : TableStatus.OCCUPIED);
            }
            tableRepository.save(table);
        }
    }

    @Transactional
    public void markPaid(Long id) {
        Order order = requireOrderForCurrentBranch(id);
        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            throw new InvalidPaymentStateException("Đơn hàng đã được ghi nhận thanh toán.");
        }
        if (order.getPaymentStatus() == PaymentStatus.REFUNDED || order.getPaymentStatus() == PaymentStatus.CANCELLED) {
            throw new InvalidPaymentStateException("Không thể ghi nhận thanh toán cho trạng thái hiện tại.");
        }
        order.setPaymentStatus(PaymentStatus.PAID);
        order.setPaidAt(LocalDateTime.now());
        order.setPaidBy(currentUserService.getCurrentUser());
    }
}
