package com.example.demo.dto.response;

import com.example.demo.model.enums.ShiftStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ShiftSummary(Long shiftId, ShiftStatus status, Long branchId, String branchName,
                           Long cashierId, String cashierUsername, LocalDateTime openedAt, LocalDateTime closedAt,
                           BigDecimal openingCash, BigDecimal expectedCash, BigDecimal actualCash,
                           BigDecimal cashDifference, BigDecimal totalSales, BigDecimal cashSales,
                           BigDecimal transferSales, BigDecimal cardSales, BigDecimal refundTotal,
                           long orderCount, String note) { }
