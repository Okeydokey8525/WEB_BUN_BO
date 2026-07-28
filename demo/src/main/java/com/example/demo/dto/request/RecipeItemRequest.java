package com.example.demo.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record RecipeItemRequest(
        @NotNull Long inventoryItemId,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal quantityRequired
) {
}
