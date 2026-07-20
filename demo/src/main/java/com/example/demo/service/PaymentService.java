package com.example.demo.service;

import com.example.demo.repository.OrderRepository;
import com.example.demo.repository.PaymentTransactionRepository;
import com.example.demo.security.BranchAccessService;
import com.example.demo.security.CurrentUserService;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.exception.InvalidPaymentAmountException;
import com.example.demo.exception.OrderAlreadyPaidException;
import com.example.demo.exception.PaymentNotAllowedException;
import com.example.demo.exception.RefundNotAllowedException;
import com.example.demo.model.Order;
import com.example.demo.model.PaymentTransaction;
import com.example.demo.model.User;
import com.example.demo.dto.request.PayOrderRequest;
import com.example.demo.dto.request.RefundOrderRequest;
import com.example.demo.dto.response.PaymentResult;
import com.example.demo.model.enums.OrderStatus;
import com.example.demo.model.enums.PaymentMethod;
import com.example.demo.model.enums.PaymentStatus;
import com.example.demo.model.enums.PaymentTransactionStatus;
import com.example.demo.model.enums.PaymentTransactionType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class PaymentService {

    private final OrderRepository orderRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final CurrentUserService currentUserService;
    private final BranchAccessService branchAccessService;

    public PaymentService(
            OrderRepository orderRepository,
            PaymentTransactionRepository paymentTransactionRepository,
            CurrentUserService currentUserService,
            BranchAccessService branchAccessService) {
        this.orderRepository = orderRepository;
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.currentUserService = currentUserService;
        this.branchAccessService = branchAccessService;
    }

    private Order requireOrderForCurrentBranch(Long orderId) {
        return orderRepository.findByIdAndBranchId(orderId, branchAccessService.requireScopedBranchId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng hoặc bạn không có quyền truy cập."));
    }

    public PaymentResult payOrder(PayOrderRequest request) {
        Order order = requireOrderForCurrentBranch(request.orderId());
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new PaymentNotAllowedException("Không thể thanh toán đơn đã hủy.");
        }
        Long branchId = order.getBranch().getId();
        if (order.getPaymentStatus() == PaymentStatus.PAID || paymentTransactionRepository
                .existsByOrderIdAndBranchIdAndTransactionTypeAndStatus(order.getId(), branchId,
                        PaymentTransactionType.PAYMENT, PaymentTransactionStatus.COMPLETED)) {
            throw new OrderAlreadyPaidException();
        }
        BigDecimal total = order.getTotalAmount();
        if (total == null || total.signum() <= 0) {
            throw new PaymentNotAllowedException("Đơn hàng không có số tiền hợp lệ để thanh toán.");
        }
        BigDecimal tendered = request.paymentMethod() == PaymentMethod.CASH ? request.amountTendered() : total;
        if (tendered == null || tendered.compareTo(total) < 0) {
            throw new InvalidPaymentAmountException("Số tiền khách đưa không đủ.");
        }
        BigDecimal change = request.paymentMethod() == PaymentMethod.CASH ? tendered.subtract(total) : BigDecimal.ZERO;
        User actor = currentUserService.getCurrentUser();
        LocalDateTime now = LocalDateTime.now();
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setOrder(order); transaction.setBranch(order.getBranch()); transaction.setCreatedBy(actor);
        transaction.setTransactionType(PaymentTransactionType.PAYMENT); transaction.setPaymentMethod(request.paymentMethod());
        transaction.setAmount(total); transaction.setAmountTendered(tendered); transaction.setChangeAmount(change);
        transaction.setReferenceCode(request.referenceCode()); transaction.setNote(request.note());
        transaction.setStatus(PaymentTransactionStatus.COMPLETED); transaction.setCreatedAt(now); transaction.setCompletedAt(now);
        PaymentTransaction saved = paymentTransactionRepository.save(transaction);
        order.setPaymentStatus(PaymentStatus.PAID); order.setPaymentMethod(request.paymentMethod());
        order.setPaidAt(now); order.setPaidBy(actor);
        orderRepository.saveAndFlush(order);
        return new PaymentResult(saved.getId(), order.getId(), total, change);
    }

    public PaymentResult refundOrder(RefundOrderRequest request) {
        Order order = requireOrderForCurrentBranch(request.orderId());
        if (order.getPaymentStatus() != PaymentStatus.PAID) {
            throw new RefundNotAllowedException("Đơn hàng chưa đủ điều kiện hoàn tiền.");
        }
        Long branchId = order.getBranch().getId();
        BigDecimal paid = paymentTransactionRepository.sumAmountByOrderIdAndBranchIdAndTypeAndStatus(order.getId(), branchId,
                PaymentTransactionType.PAYMENT, PaymentTransactionStatus.COMPLETED);
        BigDecimal refunded = paymentTransactionRepository.sumAmountByOrderIdAndBranchIdAndTypeAndStatus(order.getId(), branchId,
                PaymentTransactionType.REFUND, PaymentTransactionStatus.COMPLETED);
        if (paid == null || paid.signum() <= 0 || (refunded != null && refunded.compareTo(paid) >= 0)) {
            throw new RefundNotAllowedException("Đơn hàng đã được hoàn tiền hoặc không có giao dịch thanh toán hợp lệ.");
        }
        User actor = currentUserService.getCurrentUser();
        LocalDateTime now = LocalDateTime.now();
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setOrder(order); transaction.setBranch(order.getBranch()); transaction.setCreatedBy(actor);
        transaction.setTransactionType(PaymentTransactionType.REFUND); transaction.setPaymentMethod(order.getPaymentMethod());
        transaction.setAmount(paid); transaction.setStatus(PaymentTransactionStatus.COMPLETED);
        transaction.setNote(request.note()); transaction.setCreatedAt(now); transaction.setCompletedAt(now);
        PaymentTransaction saved = paymentTransactionRepository.save(transaction);
        order.setPaymentStatus(PaymentStatus.REFUNDED);
        orderRepository.saveAndFlush(order);
        return new PaymentResult(saved.getId(), order.getId(), paid, BigDecimal.ZERO);
    }

    @Transactional(readOnly = true)
    public List<PaymentTransaction> getTransactionsForOrder(Long orderId) {
        Order order = requireOrderForCurrentBranch(orderId);
        return paymentTransactionRepository.findByOrderIdAndBranchIdOrderByCreatedAtAsc(order.getId(), order.getBranch().getId());
    }

    @Transactional(readOnly = true)
    public List<PaymentTransaction> getBranchTransactions() {
        return paymentTransactionRepository.findByBranchIdOrderByCreatedAtDesc(branchAccessService.requireScopedBranchId());
    }
}
