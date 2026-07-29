package com.example.demo.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailyRevenueSummary(
        LocalDate date,
        BigDecimal grossSales,
        BigDecimal refundTotal,
        BigDecimal netRevenue,
        long paidOrderCount,
        BigDecimal averageOrderValue
) {
}
