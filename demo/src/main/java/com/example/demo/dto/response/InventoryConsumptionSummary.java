package com.example.demo.dto.response;

import java.math.BigDecimal;

public record InventoryConsumptionSummary(
        Long inventoryItemId,
        String inventoryItemName,
        String unit,
        BigDecimal consumedQuantity,
        BigDecimal reversedQuantity,
        BigDecimal netConsumedQuantity,
        BigDecimal wasteQuantity
) {
}
