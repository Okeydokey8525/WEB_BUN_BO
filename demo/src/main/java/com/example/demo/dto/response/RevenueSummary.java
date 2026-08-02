package com.example.demo.dto.response;

import java.math.BigDecimal;

public record RevenueSummary(
        BigDecimal grossSales,
        BigDecimal refundTotal,
        BigDecimal netRevenue,
        long paidOrderCount,
        BigDecimal averageOrderValue
) {
}
