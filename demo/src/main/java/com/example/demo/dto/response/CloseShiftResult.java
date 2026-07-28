package com.example.demo.dto.response;

import com.example.demo.model.enums.ShiftStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CloseShiftResult(
        Long shiftId,
        ShiftStatus status,
        LocalDateTime closedAt,
        BigDecimal expectedCash,
        BigDecimal actualCash,
        BigDecimal cashDifference,
        BigDecimal totalSales,
        BigDecimal cashSales,
        BigDecimal transferSales,
        BigDecimal cardSales,
        BigDecimal refundTotal,
        long orderCount
) { }
