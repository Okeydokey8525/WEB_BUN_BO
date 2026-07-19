package com.example.demo.service;

import com.example.demo.exception.InvalidOrderStateException;
import com.example.demo.model.enums.OrderStatus;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Service
public class OrderStateTransitionService {
    private final Map<OrderStatus, Set<OrderStatus>> allowedTransitions = new EnumMap<>(OrderStatus.class);

    public OrderStateTransitionService() {
        allowedTransitions.put(OrderStatus.PENDING, EnumSet.of(OrderStatus.CONFIRMED, OrderStatus.CANCELLED));
        allowedTransitions.put(OrderStatus.CONFIRMED, EnumSet.of(OrderStatus.COOKING, OrderStatus.CANCELLED));
        allowedTransitions.put(OrderStatus.COOKING, EnumSet.of(OrderStatus.READY, OrderStatus.CANCELLED));
        allowedTransitions.put(OrderStatus.READY, EnumSet.of(OrderStatus.SERVED));
        allowedTransitions.put(OrderStatus.SERVED, EnumSet.of(OrderStatus.COMPLETED));
        allowedTransitions.put(OrderStatus.COMPLETED, EnumSet.noneOf(OrderStatus.class));
        allowedTransitions.put(OrderStatus.CANCELLED, EnumSet.noneOf(OrderStatus.class));
    }

    public void validate(OrderStatus current, OrderStatus next) {
        if (current == next) {
            return;
        }
        if (!allowedTransitions.getOrDefault(current, Set.of()).contains(next)) {
            throw new InvalidOrderStateException("Không thể chuyển trạng thái đơn từ " + current + " sang " + next + ".");
        }
    }
}
