package com.example.demo.repository.projection;

import java.math.BigDecimal;

public interface InventoryConsumptionProjection {
    Long getInventoryItemId();

    String getInventoryItemName();

    String getUnit();

    BigDecimal getConsumedQuantity();

    BigDecimal getReversedQuantity();

    BigDecimal getWasteQuantity();
}
