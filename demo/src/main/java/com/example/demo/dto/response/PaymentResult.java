package com.example.demo.dto.response;

import java.math.BigDecimal;

public record PaymentResult(
        Long transactionId,
        Long orderId,
        BigDecimal amount,
        BigDecimal changeAmount
) {
}
