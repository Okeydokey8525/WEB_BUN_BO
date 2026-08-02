package com.example.demo.model;

import com.example.demo.model.enums.ShiftStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "work_shifts")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkShift {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cashier_id", nullable = false)
    private User cashier;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ShiftStatus status;

    @Column(name = "opened_at", nullable = false)
    private LocalDateTime openedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "opening_cash", nullable = false, precision = 19, scale = 0)
    private BigDecimal openingCash;

    @Column(name = "expected_cash", precision = 19, scale = 0)
    private BigDecimal expectedCash;

    @Column(name = "actual_cash", precision = 19, scale = 0)
    private BigDecimal actualCash;

    @Column(name = "cash_difference", precision = 19, scale = 0)
    private BigDecimal cashDifference;

    @Column(name = "total_sales", precision = 19, scale = 0)
    private BigDecimal totalSales;

    @Column(name = "cash_sales", precision = 19, scale = 0)
    private BigDecimal cashSales;

    @Column(name = "transfer_sales", precision = 19, scale = 0)
    private BigDecimal transferSales;

    @Column(name = "card_sales", precision = 19, scale = 0)
    private BigDecimal cardSales;

    @Column(name = "refund_total", precision = 19, scale = 0)
    private BigDecimal refundTotal;

    @Column(name = "order_count", nullable = false)
    private long orderCount;

    @Column(length = 500)
    private String note;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "opened_by", nullable = false)
    private User openedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "closed_by")
    private User closedBy;

    @Version
    private Long version;
}
