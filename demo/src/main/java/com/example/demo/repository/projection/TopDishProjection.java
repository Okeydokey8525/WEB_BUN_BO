package com.example.demo.repository.projection;

import java.math.BigDecimal;

public interface TopDishProjection {
    Long getDishId();

    String getDishName();

    Long getQuantitySold();

    BigDecimal getRevenue();

    Long getOrderCount();
}
