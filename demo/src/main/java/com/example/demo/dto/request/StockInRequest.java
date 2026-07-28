package com.example.demo.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record StockInRequest(
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal quantity,
        @Size(max = 500) String reason
) {
}
