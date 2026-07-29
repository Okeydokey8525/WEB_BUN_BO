package com.example.demo.repository.projection;

import java.math.BigDecimal;

public interface RevenueAggregateProjection {
    BigDecimal getGrossSales();
    BigDecimal getRefundTotal();
    Long getPaidOrderCount();
}
