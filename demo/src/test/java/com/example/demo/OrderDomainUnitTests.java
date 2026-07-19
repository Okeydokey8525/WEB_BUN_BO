package com.example.demo;

import com.example.demo.dto.request.CreateOrderItemRequest;
import com.example.demo.dto.request.CreateOrderRequest;
import com.example.demo.exception.BusinessValidationException;
import com.example.demo.exception.InvalidOrderItemStateException;
import com.example.demo.exception.InvalidOrderStateException;
import com.example.demo.model.Order;
import com.example.demo.model.OrderItem;
import com.example.demo.model.enums.OrderItemStatus;
import com.example.demo.model.enums.OrderStatus;
import com.example.demo.model.enums.OrderType;
import com.example.demo.model.enums.PaymentMethod;
import com.example.demo.service.OrderItemStateTransitionService;
import com.example.demo.service.OrderPricingService;
import com.example.demo.service.OrderStateTransitionService;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OrderDomainUnitTests {

    private final OrderPricingService pricingService = new OrderPricingService();
    private final OrderStateTransitionService orderTransitions = new OrderStateTransitionService();
    private final OrderItemStateTransitionService itemTransitions = new OrderItemStateTransitionService();
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void pricingUsesBigDecimalWithoutFloatingPointError() {
        Order order = new Order();
        order.getOrderItems().add(item("65000", 1));
        order.getOrderItems().add(item("12000", 3));

        pricingService.applyTotals(order);

        assertEquals(new BigDecimal("101000"), order.getSubtotal());
        assertEquals(order.getSubtotal(), order.getTotalAmount());
    }

    @Test
    void pricingRejectsNegativeTotals() {
        Order order = new Order();
        order.getOrderItems().add(item("10000", 1));
        order.setDiscountAmount(new BigDecimal("10001"));

        assertThrows(BusinessValidationException.class, () -> pricingService.applyTotals(order));
    }

    @Test
    void orderTransitionsAllowValidAndRejectInvalid() {
        assertDoesNotThrow(() -> orderTransitions.validate(OrderStatus.PENDING, OrderStatus.CONFIRMED));
        assertDoesNotThrow(() -> orderTransitions.validate(OrderStatus.SERVED, OrderStatus.COMPLETED));
        assertThrows(InvalidOrderStateException.class, () -> orderTransitions.validate(OrderStatus.COMPLETED, OrderStatus.PENDING));
        assertThrows(InvalidOrderStateException.class, () -> orderTransitions.validate(OrderStatus.CANCELLED, OrderStatus.COOKING));
    }

    @Test
    void orderItemTransitionsAllowValidAndRejectCancelledRollback() {
        assertDoesNotThrow(() -> itemTransitions.validate(OrderItemStatus.PENDING, OrderItemStatus.PREPARING));
        assertDoesNotThrow(() -> itemTransitions.validate(OrderItemStatus.PREPARING, OrderItemStatus.READY));
        assertDoesNotThrow(() -> itemTransitions.validate(OrderItemStatus.READY, OrderItemStatus.SERVED));
        assertThrows(InvalidOrderItemStateException.class, () -> itemTransitions.validate(OrderItemStatus.CANCELLED, OrderItemStatus.PREPARING));
    }

    @Test
    void dtoValidationRejectsInvalidCart() {
        assertTrue(validator.validate(new CreateOrderRequest(1L, "Khách", PaymentMethod.CASH, OrderType.DINE_IN, List.of(new CreateOrderItemRequest(1L, 1, null)))).isEmpty());
        assertFalse(validator.validate(new CreateOrderRequest(1L, "Khách", PaymentMethod.CASH, OrderType.DINE_IN, List.of(new CreateOrderItemRequest(1L, 0, null)))).isEmpty());
        assertFalse(validator.validate(new CreateOrderRequest(1L, "Khách", PaymentMethod.CASH, OrderType.DINE_IN, List.of(new CreateOrderItemRequest(1L, -1, null)))).isEmpty());
        assertFalse(validator.validate(new CreateOrderRequest(1L, "Khách", PaymentMethod.CASH, OrderType.DINE_IN, List.of(new CreateOrderItemRequest(1L, 51, null)))).isEmpty());
        assertFalse(validator.validate(new CreateOrderRequest(1L, "Khách", PaymentMethod.CASH, OrderType.DINE_IN, List.of(new CreateOrderItemRequest(null, 1, null)))).isEmpty());
        assertFalse(validator.validate(new CreateOrderRequest(1L, "x".repeat(101), PaymentMethod.CASH, OrderType.DINE_IN, List.of(new CreateOrderItemRequest(1L, 1, null)))).isEmpty());
        assertFalse(validator.validate(new CreateOrderRequest(1L, "Khách", PaymentMethod.CASH, OrderType.DINE_IN, List.of(new CreateOrderItemRequest(1L, 1, "x".repeat(256))))).isEmpty());
        assertFalse(validator.validate(new CreateOrderRequest(1L, "Khách", PaymentMethod.CASH, OrderType.DINE_IN, List.of())).isEmpty());
    }

    private OrderItem item(String price, int quantity) {
        OrderItem item = new OrderItem();
        item.setPrice(new BigDecimal(price));
        item.setQuantity(quantity);
        item.setLineTotal(new BigDecimal(price).multiply(BigDecimal.valueOf(quantity)));
        return item;
    }
}
