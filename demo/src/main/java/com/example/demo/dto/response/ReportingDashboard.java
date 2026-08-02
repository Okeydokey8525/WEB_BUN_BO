package com.example.demo.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record ReportingDashboard(
        LocalDate fromDate,
        LocalDate toDate,
        RevenueSummary revenueSummary,
        List<PaymentMethodSummary> paymentMethods,
        List<DailyRevenueSummary> dailyRevenue,
        List<TopDishSummary> topDishes,
        List<InventoryConsumptionSummary> inventoryConsumption,
        List<ShiftReportSummary> shiftReports,
        int topDishLimit,
        int inventoryLimit,
        LocalDateTime generatedAt
) {
}
