package com.example.demo.repository.projection;

import java.math.BigDecimal;

public interface ShiftPaymentAggregate {
    BigDecimal getGrossPaymentTotal();
    BigDecimal getRefundTotal();
    BigDecimal getCashPaymentTotal();
    BigDecimal getCashRefundTotal();
    BigDecimal getTransferPaymentTotal();
    BigDecimal getTransferRefundTotal();
    BigDecimal getCardPaymentTotal();
    BigDecimal getCardRefundTotal();
    Long getDistinctOrderCount();
}
