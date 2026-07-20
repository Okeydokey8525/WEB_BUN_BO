package com.example.demo.dto.request;

import com.example.demo.model.enums.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record PayOrderRequest(
        @NotNull Long orderId,
        @NotNull PaymentMethod paymentMethod,
        @DecimalMin(value = "0", inclusive = true) BigDecimal amountTendered,
        @Size(max = 100) String referenceCode,
        @Size(max = 500) String note
) {
}
