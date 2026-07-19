package com.example.demo.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateOrderItemRequest(
        @NotNull Long dishId,
        @NotNull @Min(1) @Max(50) Integer quantity,
        @Size(max = 255) String note
) {
}
