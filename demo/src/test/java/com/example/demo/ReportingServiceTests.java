package com.example.demo;

import com.example.demo.dto.request.ReportFilterRequest;
import com.example.demo.dto.response.RevenueSummary;
import com.example.demo.exception.BranchAccessDeniedException;
import com.example.demo.exception.BusinessValidationException;
import com.example.demo.model.Branch;
import com.example.demo.model.Role;
import com.example.demo.model.User;
import com.example.demo.model.enums.PaymentTransactionStatus;
import com.example.demo.repository.PaymentTransactionRepository;
import com.example.demo.repository.projection.RevenueAggregateProjection;
import com.example.demo.security.BranchAccessService;
import com.example.demo.security.CurrentUserService;
import com.example.demo.service.ReportingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportingServiceTests {
    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private BranchAccessService branchAccessService;
    @InjectMocks
    private ReportingService reportingService;

    private User admin;
    private ReportFilterRequest filter;

    @BeforeEach
    void setUp() {
        Branch branch = new Branch();
        branch.setId(10L);
        admin = user("ROLE_ADMIN", branch);
        filter = new ReportFilterRequest(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));
        lenient().when(currentUserService.getCurrentUser()).thenReturn(admin);
        lenient().when(branchAccessService.requireScopedBranchId()).thenReturn(10L);
    }

    @Test
    void mapsGrossSales() {
        stubAggregate("200000", "0", 2L);

        assertEquals(new BigDecimal("200000"), reportingService.getRevenueSummary(filter).grossSales());
    }

    @Test
    void mapsRefundTotal() {
        stubAggregate("200000", "50000", 2L);

        assertEquals(new BigDecimal("50000"), reportingService.getRevenueSummary(filter).refundTotal());
    }

    @Test
    void calculatesNetRevenue() {
        stubAggregate("200000", "50000", 2L);

        assertEquals(new BigDecimal("150000"), reportingService.getRevenueSummary(filter).netRevenue());
    }

    @Test
    void mapsPaidOrderCount() {
        stubAggregate("200000", "0", 2L);

        assertEquals(2L, reportingService.getRevenueSummary(filter).paidOrderCount());
    }

    @Test
    void calculatesWholeAverageOrderValue() {
        stubAggregate("200000", "50000", 2L);

        assertEquals(new BigDecimal("75000"), reportingService.getRevenueSummary(filter).averageOrderValue());
    }

    @Test
    void roundsAverageOrderValueHalfUp() {
        stubAggregate("101", "0", 2L);

        assertEquals(new BigDecimal("51"), reportingService.getRevenueSummary(filter).averageOrderValue());
    }

    @Test
    void returnsZeroAverageWhenThereAreNoPaidOrders() {
        stubAggregate("0", "0", 0L);

        assertEquals(BigDecimal.ZERO, reportingService.getRevenueSummary(filter).averageOrderValue());
    }

    @Test
    void preservesNegativeNetRevenue() {
        stubAggregate("10000", "15000", 1L);

        assertEquals(new BigDecimal("-5000"), reportingService.getRevenueSummary(filter).netRevenue());
    }

    @Test
    void normalizesNullProjectionFieldsToZero() {
        RevenueAggregateProjection projection = mock(RevenueAggregateProjection.class);
        when(paymentTransactionRepository.aggregateRevenueByBranchAndCompletedAt(anyLong(), any(), any(), any()))
                .thenReturn(projection);

        RevenueSummary summary = reportingService.getRevenueSummary(filter);

        assertEquals(BigDecimal.ZERO, summary.grossSales());
        assertEquals(BigDecimal.ZERO, summary.refundTotal());
        assertEquals(BigDecimal.ZERO, summary.netRevenue());
        assertEquals(0L, summary.paidOrderCount());
        assertEquals(BigDecimal.ZERO, summary.averageOrderValue());
    }

    @Test
    void returnsZeroSummaryWhenRepositoryReturnsNull() {
        when(paymentTransactionRepository.aggregateRevenueByBranchAndCompletedAt(anyLong(), any(), any(), any()))
                .thenReturn(null);

        RevenueSummary summary = reportingService.getRevenueSummary(filter);

        assertEquals(BigDecimal.ZERO, summary.grossSales());
        assertEquals(BigDecimal.ZERO, summary.refundTotal());
        assertEquals(BigDecimal.ZERO, summary.netRevenue());
        assertEquals(0L, summary.paidOrderCount());
        assertEquals(BigDecimal.ZERO, summary.averageOrderValue());
    }

    @Test
    void adminWithBranchMayReadRevenue() {
        stubAggregate("0", "0", 0L);

        reportingService.getRevenueSummary(filter);

        verify(paymentTransactionRepository).aggregateRevenueByBranchAndCompletedAt(anyLong(), any(), any(),
                eq(PaymentTransactionStatus.COMPLETED));
    }

    @Test
    void cashierIsDenied() {
        assertDeniedRole("ROLE_CASHIER");
    }

    @Test
    void userIsDenied() {
        assertDeniedRole("ROLE_USER");
    }

    @Test
    void kitchenIsDenied() {
        assertDeniedRole("ROLE_KITCHEN");
    }

    @Test
    void waiterIsDenied() {
        assertDeniedRole("ROLE_WAITER");
    }

    @Test
    void inventoryIsDenied() {
        assertDeniedRole("ROLE_INVENTORY");
    }

    @Test
    void userWithoutBranchIsDenied() {
        when(branchAccessService.requireScopedBranchId()).thenThrow(new BranchAccessDeniedException("No branch"));

        assertThrows(BranchAccessDeniedException.class, () -> reportingService.getRevenueSummary(filter));
        verify(paymentTransactionRepository, never()).aggregateRevenueByBranchAndCompletedAt(anyLong(), any(), any(), any());
    }

    @Test
    void nullFilterIsRejectedBeforeRepositoryAccess() {
        assertInvalidFilter(null);
    }

    @Test
    void nullFromDateIsRejectedBeforeRepositoryAccess() {
        assertInvalidFilter(new ReportFilterRequest(null, LocalDate.of(2026, 7, 1)));
    }

    @Test
    void nullToDateIsRejectedBeforeRepositoryAccess() {
        assertInvalidFilter(new ReportFilterRequest(LocalDate.of(2026, 7, 1), null));
    }

    @Test
    void reversedDateRangeIsRejectedBeforeRepositoryAccess() {
        assertInvalidFilter(new ReportFilterRequest(LocalDate.of(2026, 7, 2), LocalDate.of(2026, 7, 1)));
    }

    @Test
    void sameDayRangeIsAllowed() {
        stubAggregate("0", "0", 0L);

        reportingService.getRevenueSummary(new ReportFilterRequest(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 1)));

        verify(paymentTransactionRepository).aggregateRevenueByBranchAndCompletedAt(anyLong(), any(), any(), any());
    }

    @Test
    void inclusiveRangeOf366DaysIsAllowed() {
        stubAggregate("0", "0", 0L);

        reportingService.getRevenueSummary(new ReportFilterRequest(LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1)));

        verify(paymentTransactionRepository).aggregateRevenueByBranchAndCompletedAt(anyLong(), any(), any(), any());
    }

    @Test
    void rangeLongerThan366DaysIsRejected() {
        assertInvalidFilter(new ReportFilterRequest(LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 2)));
    }

    @Test
    void queriesCurrentScopedBranchWithInclusiveExclusiveDayBoundsOnce() {
        stubAggregate("0", "0", 0L);

        reportingService.getRevenueSummary(filter);

        ArgumentCaptor<Long> branchId = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<LocalDateTime> from = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> to = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(paymentTransactionRepository).aggregateRevenueByBranchAndCompletedAt(
                branchId.capture(), from.capture(), to.capture(), eq(PaymentTransactionStatus.COMPLETED));
        assertEquals(10L, branchId.getValue());
        assertEquals(LocalDateTime.of(2026, 7, 1, 0, 0), from.getValue());
        assertEquals(LocalDateTime.of(2026, 8, 1, 0, 0), to.getValue());
    }

    private void assertDeniedRole(String roleName) {
        admin = user(roleName, admin.getBranch());
        when(currentUserService.getCurrentUser()).thenReturn(admin);

        assertThrows(BranchAccessDeniedException.class, () -> reportingService.getRevenueSummary(filter));
        verify(paymentTransactionRepository, never()).aggregateRevenueByBranchAndCompletedAt(anyLong(), any(), any(), any());
    }

    private void assertInvalidFilter(ReportFilterRequest invalidFilter) {
        assertThrows(BusinessValidationException.class, () -> reportingService.getRevenueSummary(invalidFilter));
        verify(paymentTransactionRepository, never()).aggregateRevenueByBranchAndCompletedAt(anyLong(), any(), any(), any());
    }

    private void stubAggregate(String gross, String refund, Long paidOrderCount) {
        RevenueAggregateProjection projection = revenueProjection(gross, refund, paidOrderCount);
        when(paymentTransactionRepository.aggregateRevenueByBranchAndCompletedAt(anyLong(), any(), any(), any()))
                .thenReturn(projection);
    }

    private RevenueAggregateProjection revenueProjection(String gross, String refund, Long paidOrderCount) {
        RevenueAggregateProjection projection = mock(RevenueAggregateProjection.class);
        when(projection.getGrossSales()).thenReturn(new BigDecimal(gross));
        when(projection.getRefundTotal()).thenReturn(new BigDecimal(refund));
        when(projection.getPaidOrderCount()).thenReturn(paidOrderCount);
        return projection;
    }

    private User user(String roleName, Branch branch) {
        Role role = new Role();
        role.setName(roleName);
        User user = new User();
        user.setId(5L);
        user.setUsername("admin-a");
        user.setRole(role);
        user.setBranch(branch);
        return user;
    }
}
