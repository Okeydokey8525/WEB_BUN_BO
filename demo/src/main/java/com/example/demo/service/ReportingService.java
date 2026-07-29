package com.example.demo.service;

import com.example.demo.dto.request.ReportFilterRequest;
import com.example.demo.dto.response.RevenueSummary;
import com.example.demo.exception.BranchAccessDeniedException;
import com.example.demo.exception.BusinessValidationException;
import com.example.demo.model.User;
import com.example.demo.model.enums.PaymentTransactionStatus;
import com.example.demo.repository.PaymentTransactionRepository;
import com.example.demo.repository.projection.RevenueAggregateProjection;
import com.example.demo.security.BranchAccessService;
import com.example.demo.security.CurrentUserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Service
@Transactional(readOnly = true)
public class ReportingService {
    private static final long MAX_REPORTING_DAYS = 366;

    private final PaymentTransactionRepository paymentTransactionRepository;
    private final CurrentUserService currentUserService;
    private final BranchAccessService branchAccessService;

    public ReportingService(PaymentTransactionRepository paymentTransactionRepository,
                            CurrentUserService currentUserService,
                            BranchAccessService branchAccessService) {
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.currentUserService = currentUserService;
        this.branchAccessService = branchAccessService;
    }

    public RevenueSummary getRevenueSummary(ReportFilterRequest filter) {
        validateFilter(filter);

        User actor = currentUserService.getCurrentUser();
        if (actor.getRole() == null || !"ROLE_ADMIN".equals(actor.getRole().getName())) {
            throw new BranchAccessDeniedException("Khong co quyen truy cap bao cao doanh thu.");
        }

        Long branchId = branchAccessService.requireScopedBranchId();
        LocalDateTime fromInclusive = filter.fromDate().atStartOfDay();
        LocalDateTime toExclusive = filter.toDate().plusDays(1).atStartOfDay();
        RevenueAggregateProjection aggregate = paymentTransactionRepository.aggregateRevenueByBranchAndCompletedAt(
                branchId, fromInclusive, toExclusive, PaymentTransactionStatus.COMPLETED);

        BigDecimal grossSales = aggregate == null ? BigDecimal.ZERO : zero(aggregate.getGrossSales());
        BigDecimal refundTotal = aggregate == null ? BigDecimal.ZERO : zero(aggregate.getRefundTotal());
        long paidOrderCount = aggregate == null || aggregate.getPaidOrderCount() == null
                ? 0L : aggregate.getPaidOrderCount();
        BigDecimal netRevenue = grossSales.subtract(refundTotal);
        BigDecimal averageOrderValue = paidOrderCount > 0
                ? netRevenue.divide(BigDecimal.valueOf(paidOrderCount), 0, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return new RevenueSummary(grossSales, refundTotal, netRevenue, paidOrderCount, averageOrderValue);
    }

    private void validateFilter(ReportFilterRequest filter) {
        if (filter == null || filter.fromDate() == null || filter.toDate() == null) {
            throw new BusinessValidationException("Khoang thoi gian bao cao la bat buoc.");
        }
        LocalDate fromDate = filter.fromDate();
        LocalDate toDate = filter.toDate();
        if (fromDate.isAfter(toDate)) {
            throw new BusinessValidationException("Ngay bat dau khong duoc sau ngay ket thuc.");
        }
        if (ChronoUnit.DAYS.between(fromDate, toDate) >= MAX_REPORTING_DAYS) {
            throw new BusinessValidationException("Khoang thoi gian bao cao khong duoc qua 366 ngay.");
        }
    }

    private BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
