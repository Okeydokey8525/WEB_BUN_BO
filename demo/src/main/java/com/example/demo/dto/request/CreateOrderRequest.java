package com.example.demo.dto.request;

import com.example.demo.model.enums.OrderType;
import com.example.demo.model.enums.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateOrderRequest(
        @NotNull Long tableId,
        @Size(max = 100) String customerName,
        @NotNull PaymentMethod paymentMethod,
        @NotNull OrderType orderType,
        @NotEmpty @Valid List<CreateOrderItemRequest> items
) {
}
