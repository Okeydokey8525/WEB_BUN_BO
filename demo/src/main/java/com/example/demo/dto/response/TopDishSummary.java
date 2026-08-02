package com.example.demo.dto.response;

import java.math.BigDecimal;

public record TopDishSummary(
        Long dishId,
        String dishName,
        long quantitySold,
        BigDecimal revenue,
        long orderCount
) {
}
