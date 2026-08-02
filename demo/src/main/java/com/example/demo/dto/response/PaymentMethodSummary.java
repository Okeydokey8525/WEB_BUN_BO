package com.example.demo.dto.response;

import com.example.demo.model.enums.PaymentMethod;

import java.math.BigDecimal;

public record PaymentMethodSummary(
        PaymentMethod paymentMethod,
        BigDecimal grossAmount,
        BigDecimal refundAmount,
        BigDecimal netAmount,
        long paymentTransactionCount,
        long refundTransactionCount,
        long paidOrderCount
) {
}
