package com.example.demo.service;

import com.example.demo.dto.request.ReportFilterRequest;
import com.example.demo.dto.response.PaymentMethodSummary;
import com.example.demo.dto.response.RevenueSummary;
import com.example.demo.exception.BranchAccessDeniedException;
import com.example.demo.exception.BusinessValidationException;
import com.example.demo.model.User;
import com.example.demo.model.enums.PaymentMethod;
import com.example.demo.model.enums.PaymentTransactionStatus;
import com.example.demo.repository.PaymentTransactionRepository;
import com.example.demo.repository.projection.PaymentMethodAggregateProjection;
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
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
        Long branchId = requireAdminBranchId();
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

    public List<PaymentMethodSummary> getPaymentMethodBreakdown(ReportFilterRequest filter) {
        validateFilter(filter);
        Long branchId = requireAdminBranchId();
        LocalDateTime fromInclusive = filter.fromDate().atStartOfDay();
        LocalDateTime toExclusive = filter.toDate().plusDays(1).atStartOfDay();
        List<PaymentMethodAggregateProjection> aggregates = paymentTransactionRepository
                .aggregateByPaymentMethodAndBranchAndCompletedAt(branchId, fromInclusive, toExclusive);
        Map<PaymentMethod, PaymentMethodAggregateProjection> byMethod = (aggregates == null ? List.<PaymentMethodAggregateProjection>of() : aggregates)
                .stream()
                .filter(aggregate -> aggregate != null && aggregate.getPaymentMethod() != null)
                .collect(Collectors.toMap(PaymentMethodAggregateProjection::getPaymentMethod, aggregate -> aggregate));

        return Arrays.stream(PaymentMethod.values())
                .sorted(Comparator.comparingInt(this::paymentMethodOrder).thenComparing(Enum::name))
                .map(method -> toPaymentMethodSummary(method, byMethod.get(method)))
                .toList();
    }

    private Long requireAdminBranchId() {
        User actor = currentUserService.getCurrentUser();
        if (actor.getRole() == null || !"ROLE_ADMIN".equals(actor.getRole().getName())) {
            throw new BranchAccessDeniedException("Khong co quyen truy cap bao cao doanh thu.");
        }
        return branchAccessService.requireScopedBranchId();
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

    private PaymentMethodSummary toPaymentMethodSummary(PaymentMethod method,
                                                        PaymentMethodAggregateProjection aggregate) {
        BigDecimal grossAmount = aggregate == null ? BigDecimal.ZERO : zero(aggregate.getGrossAmount());
        BigDecimal refundAmount = aggregate == null ? BigDecimal.ZERO : zero(aggregate.getRefundAmount());
        long paymentCount = aggregate == null || aggregate.getPaymentTransactionCount() == null
                ? 0L : aggregate.getPaymentTransactionCount();
        long refundCount = aggregate == null || aggregate.getRefundTransactionCount() == null
                ? 0L : aggregate.getRefundTransactionCount();
        long paidOrderCount = aggregate == null || aggregate.getPaidOrderCount() == null
                ? 0L : aggregate.getPaidOrderCount();
        return new PaymentMethodSummary(method, grossAmount, refundAmount, grossAmount.subtract(refundAmount),
                paymentCount, refundCount, paidOrderCount);
    }

    private int paymentMethodOrder(PaymentMethod method) {
        if (method == PaymentMethod.CASH) {
            return 0;
        }
        if (method == PaymentMethod.VIETQR) {
            return 1;
        }
        return 2;
    }
}
