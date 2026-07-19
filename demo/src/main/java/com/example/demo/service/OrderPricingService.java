package com.example.demo.service;

import com.example.demo.exception.BusinessValidationException;
import com.example.demo.model.Order;
import com.example.demo.model.OrderItem;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class OrderPricingService {

    public void applyTotals(Order order) {
        BigDecimal subtotal = order.getOrderItems().stream()
                .map(OrderItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (subtotal.signum() < 0) {
            throw new BusinessValidationException("Tổng tiền không được âm.");
        }
        order.setSubtotal(subtotal);
        order.setDiscountAmount(nonNegative(order.getDiscountAmount(), "Giảm giá"));
        if (order.getDiscountAmount().compareTo(subtotal) > 0) {
            throw new BusinessValidationException("Giảm giá không được lớn hơn tổng tạm tính.");
        }
        order.setServiceCharge(nonNegative(order.getServiceCharge(), "Phí dịch vụ"));
        order.setDeliveryFee(nonNegative(order.getDeliveryFee(), "Phí giao hàng"));
        order.setTaxAmount(nonNegative(order.getTaxAmount(), "Thuế"));
        order.setTotalAmount(subtotal
                .subtract(order.getDiscountAmount())
                .add(order.getServiceCharge())
                .add(order.getDeliveryFee())
                .add(order.getTaxAmount()));
        if (order.getTotalAmount().signum() < 0) {
            throw new BusinessValidationException("Tổng tiền không được âm.");
        }
    }

    private BigDecimal nonNegative(BigDecimal value, String fieldName) {
        BigDecimal safeValue = value == null ? BigDecimal.ZERO : value;
        if (safeValue.signum() < 0) {
            throw new BusinessValidationException(fieldName + " không được âm.");
        }
        return safeValue;
    }
}
