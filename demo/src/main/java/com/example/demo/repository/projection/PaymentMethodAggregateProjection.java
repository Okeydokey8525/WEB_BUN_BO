package com.example.demo.repository.projection;

import com.example.demo.model.enums.PaymentMethod;

import java.math.BigDecimal;

public interface PaymentMethodAggregateProjection {
    PaymentMethod getPaymentMethod();

    BigDecimal getGrossAmount();

    BigDecimal getRefundAmount();

    Long getPaymentTransactionCount();

    Long getRefundTransactionCount();

    Long getPaidOrderCount();
}
