package com.example.demo.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RefundOrderRequest(
        @NotNull Long orderId,
        @NotBlank @Size(max = 500) String note
) {
}
