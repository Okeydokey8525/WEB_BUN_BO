package com.example.demo.service;

import com.example.demo.dto.request.ReportFilterRequest;
import com.example.demo.dto.response.DailyRevenueSummary;
import com.example.demo.dto.response.PaymentMethodSummary;
import com.example.demo.dto.response.RevenueSummary;
import com.example.demo.dto.response.TopDishSummary;
import com.example.demo.dto.response.InventoryConsumptionSummary;
import com.example.demo.exception.BranchAccessDeniedException;
import com.example.demo.exception.BusinessValidationException;
import com.example.demo.model.User;
import com.example.demo.model.enums.PaymentMethod;
import com.example.demo.model.enums.PaymentTransactionStatus;
import com.example.demo.repository.PaymentTransactionRepository;
import com.example.demo.repository.OrderItemRepository;
import com.example.demo.repository.InventoryTransactionRepository;
import com.example.demo.repository.projection.PaymentMethodAggregateProjection;
import com.example.demo.repository.projection.DailyRevenueProjection;
import com.example.demo.repository.projection.RevenueAggregateProjection;
import com.example.demo.repository.projection.TopDishProjection;
import com.example.demo.repository.projection.InventoryConsumptionProjection;
import com.example.demo.security.BranchAccessService;
import com.example.demo.security.CurrentUserService;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ReportingService {
    private static final long MAX_REPORTING_DAYS = 366;

    private final PaymentTransactionRepository paymentTransactionRepository;
    private final OrderItemRepository orderItemRepository;
    private final InventoryTransactionRepository inventoryTransactionRepository;
    private final CurrentUserService currentUserService;
    private final BranchAccessService branchAccessService;

    public ReportingService(PaymentTransactionRepository paymentTransactionRepository,
                            OrderItemRepository orderItemRepository,
                            InventoryTransactionRepository inventoryTransactionRepository,
                            CurrentUserService currentUserService,
                            BranchAccessService branchAccessService) {
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.orderItemRepository = orderItemRepository;
        this.inventoryTransactionRepository = inventoryTransactionRepository;
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

    public List<DailyRevenueSummary> getDailyRevenue(ReportFilterRequest filter) {
        validateFilter(filter);
        Long branchId = requireAdminBranchId();
        LocalDateTime fromInclusive = filter.fromDate().atStartOfDay();
        LocalDateTime toExclusive = filter.toDate().plusDays(1).atStartOfDay();
        List<DailyRevenueProjection> aggregates = paymentTransactionRepository
                .aggregateDailyRevenueByBranchAndCompletedAt(branchId, fromInclusive, toExclusive);
        Map<LocalDate, DailyRevenueProjection> byDate = new HashMap<>();
        for (DailyRevenueProjection aggregate : aggregates == null ? List.<DailyRevenueProjection>of() : aggregates) {
            if (aggregate == null || aggregate.getRevenueDate() == null) {
                continue;
            }
            LocalDate date = aggregate.getRevenueDate();
            if (date.isBefore(filter.fromDate()) || date.isAfter(filter.toDate())) {
                throw new BusinessValidationException("Du lieu doanh thu nam ngoai khoang bao cao.");
            }
            if (byDate.putIfAbsent(date, aggregate) != null) {
                throw new BusinessValidationException("Du lieu doanh thu theo ngay bi trung lap.");
            }
        }

        return filter.fromDate().datesUntil(filter.toDate().plusDays(1))
                .map(date -> toDailyRevenueSummary(date, byDate.get(date)))
                .toList();
    }

    public List<TopDishSummary> getTopDishes(ReportFilterRequest filter, int limit) {
        validateFilter(filter);
        if (limit < 1 || limit > 100) {
            throw new BusinessValidationException("So luong mon top phai nam trong khoang tu 1 den 100.");
        }
        Long branchId = requireAdminBranchId();
        LocalDateTime fromInclusive = filter.fromDate().atStartOfDay();
        LocalDateTime toExclusive = filter.toDate().plusDays(1).atStartOfDay();
        List<TopDishProjection> aggregates = orderItemRepository.aggregateTopDishesByBranchAndPaidAt(
                branchId, fromInclusive, toExclusive, PageRequest.of(0, limit));
        return (aggregates == null ? List.<TopDishProjection>of() : aggregates).stream()
                .filter(aggregate -> aggregate != null)
                .map(this::toTopDishSummary)
                .toList();
    }

    public List<InventoryConsumptionSummary> getInventoryConsumption(ReportFilterRequest filter, int limit) {
        validateFilter(filter);
        if (limit < 1 || limit > 100) {
            throw new BusinessValidationException("So luong nguyen lieu phai nam trong khoang tu 1 den 100.");
        }
        Long branchId = requireAdminBranchId();
        List<InventoryConsumptionProjection> aggregates = inventoryTransactionRepository.aggregateConsumptionByBranchAndCreatedAt(
                branchId, filter.fromDate().atStartOfDay(), filter.toDate().plusDays(1).atStartOfDay(), PageRequest.of(0, limit));
        return (aggregates == null ? List.<InventoryConsumptionProjection>of() : aggregates).stream()
                .filter(aggregate -> aggregate != null).map(this::toInventoryConsumptionSummary).toList();
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

    private DailyRevenueSummary toDailyRevenueSummary(LocalDate date, DailyRevenueProjection aggregate) {
        BigDecimal grossSales = aggregate == null ? BigDecimal.ZERO : zero(aggregate.getGrossSales());
        BigDecimal refundTotal = aggregate == null ? BigDecimal.ZERO : zero(aggregate.getRefundTotal());
        long paidOrderCount = aggregate == null || aggregate.getPaidOrderCount() == null
                ? 0L : aggregate.getPaidOrderCount();
        BigDecimal netRevenue = grossSales.subtract(refundTotal);
        BigDecimal averageOrderValue = paidOrderCount > 0
                ? netRevenue.divide(BigDecimal.valueOf(paidOrderCount), 0, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        return new DailyRevenueSummary(date, grossSales, refundTotal, netRevenue, paidOrderCount, averageOrderValue);
    }

    private TopDishSummary toTopDishSummary(TopDishProjection aggregate) {
        long quantitySold = aggregate.getQuantitySold() == null ? 0L : aggregate.getQuantitySold();
        long orderCount = aggregate.getOrderCount() == null ? 0L : aggregate.getOrderCount();
        return new TopDishSummary(aggregate.getDishId(), aggregate.getDishName(), quantitySold,
                zero(aggregate.getRevenue()), orderCount);
    }

    private InventoryConsumptionSummary toInventoryConsumptionSummary(InventoryConsumptionProjection aggregate) {
        BigDecimal consumed = zero(aggregate.getConsumedQuantity());
        BigDecimal reversed = zero(aggregate.getReversedQuantity());
        return new InventoryConsumptionSummary(aggregate.getInventoryItemId(), aggregate.getInventoryItemName(), aggregate.getUnit(),
                consumed, reversed, consumed.subtract(reversed), zero(aggregate.getWasteQuantity()));
    }
}
