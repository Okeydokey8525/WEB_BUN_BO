package com.example.demo.service;

import com.example.demo.exception.InvalidOrderItemStateException;
import com.example.demo.model.enums.OrderItemStatus;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Service
public class OrderItemStateTransitionService {
    private final Map<OrderItemStatus, Set<OrderItemStatus>> allowedTransitions = new EnumMap<>(OrderItemStatus.class);

    public OrderItemStateTransitionService() {
        allowedTransitions.put(OrderItemStatus.PENDING, EnumSet.of(OrderItemStatus.PREPARING, OrderItemStatus.CANCELLED));
        allowedTransitions.put(OrderItemStatus.PREPARING, EnumSet.of(OrderItemStatus.READY));
        allowedTransitions.put(OrderItemStatus.READY, EnumSet.of(OrderItemStatus.SERVED));
        allowedTransitions.put(OrderItemStatus.SERVED, EnumSet.noneOf(OrderItemStatus.class));
        allowedTransitions.put(OrderItemStatus.CANCELLED, EnumSet.noneOf(OrderItemStatus.class));
    }

    public void validate(OrderItemStatus current, OrderItemStatus next) {
        if (current == next) {
            return;
        }
        if (!allowedTransitions.getOrDefault(current, Set.of()).contains(next)) {
            throw new InvalidOrderItemStateException("Không thể chuyển trạng thái món từ " + current + " sang " + next + ".");
        }
    }
}
