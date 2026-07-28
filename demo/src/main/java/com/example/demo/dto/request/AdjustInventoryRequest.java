package com.example.demo.dto.request;

import com.example.demo.model.enums.InventoryTransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record AdjustInventoryRequest(
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal quantity,
        @NotNull InventoryTransactionType direction,
        @NotBlank @Size(max = 500) String reason
) {
}
