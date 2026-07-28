package com.example.demo.service;

import com.example.demo.dto.request.CloseShiftRequest;
import com.example.demo.dto.request.OpenShiftRequest;
import com.example.demo.dto.response.CloseShiftResult;
import com.example.demo.dto.response.OpenShiftResult;
import com.example.demo.dto.response.ShiftSummary;
import com.example.demo.exception.BusinessValidationException;
import com.example.demo.exception.ShiftAlreadyOpenException;
import com.example.demo.exception.ShiftAccessDeniedException;
import com.example.demo.exception.ShiftNotOpenException;
import com.example.demo.exception.ShiftAlreadyClosedException;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.model.Branch;
import com.example.demo.model.User;
import com.example.demo.model.WorkShift;
import com.example.demo.model.enums.ShiftStatus;
import com.example.demo.repository.PaymentTransactionRepository;
import com.example.demo.repository.WorkShiftRepository;
import com.example.demo.repository.projection.ShiftPaymentAggregate;
import com.example.demo.security.BranchAccessService;
import com.example.demo.security.CurrentUserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class ShiftService {
    private final WorkShiftRepository workShiftRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final CurrentUserService currentUserService;
    private final BranchAccessService branchAccessService;

    public ShiftService(WorkShiftRepository workShiftRepository,
                        PaymentTransactionRepository paymentTransactionRepository,
                        CurrentUserService currentUserService,
                        BranchAccessService branchAccessService) {
        this.workShiftRepository = workShiftRepository;
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.currentUserService = currentUserService;
        this.branchAccessService = branchAccessService;
    }

    public OpenShiftResult openShift(OpenShiftRequest request) {
        if (request == null || request.openingCash() == null || request.openingCash().signum() < 0) {
            throw new BusinessValidationException("Tiền đầu ca phải lớn hơn hoặc bằng 0.");
        }
        User actor = currentUserService.getCurrentUser();
        if (actor.getRole() == null || (!"ROLE_CASHIER".equals(actor.getRole().getName())
                && !"ROLE_ADMIN".equals(actor.getRole().getName()))) {
            throw new BusinessValidationException("Chỉ thu ngân hoặc quản trị viên được mở ca.");
        }
        Branch branch = currentUserService.requireCurrentBranch();
        if (workShiftRepository.findOpenShiftForCashierForUpdate(actor.getId(), ShiftStatus.OPEN).isPresent()) {
            throw new ShiftAlreadyOpenException();
        }
        WorkShift shift = new WorkShift();
        shift.setBranch(branch); shift.setCashier(actor); shift.setStatus(ShiftStatus.OPEN);
        shift.setOpenedAt(LocalDateTime.now()); shift.setOpenedBy(actor);
        shift.setOpeningCash(request.openingCash()); shift.setNote(request.note());
        shift.setExpectedCash(BigDecimal.ZERO); shift.setActualCash(BigDecimal.ZERO); shift.setCashDifference(BigDecimal.ZERO);
        shift.setTotalSales(BigDecimal.ZERO); shift.setCashSales(BigDecimal.ZERO); shift.setTransferSales(BigDecimal.ZERO);
        shift.setCardSales(BigDecimal.ZERO); shift.setRefundTotal(BigDecimal.ZERO); shift.setOrderCount(0);
        WorkShift saved = workShiftRepository.save(shift);
        return new OpenShiftResult(toSummary(saved));
    }

    @Transactional(readOnly = true)
    public ShiftSummary getCurrentShift() {
        User actor = currentUserService.getCurrentUser();
        currentUserService.requireCurrentBranch();
        WorkShift shift = workShiftRepository.findByCashierIdAndStatus(actor.getId(), ShiftStatus.OPEN)
                .orElseThrow(ShiftNotOpenException::new);
        return toSummary(shift);
    }

    @Transactional(readOnly = true)
    public ShiftSummary getShift(Long shiftId) {
        if (shiftId == null) {
            throw new BusinessValidationException("Mã ca làm việc là bắt buộc.");
        }
        User actor = currentUserService.getCurrentUser();
        Branch branch = currentUserService.requireCurrentBranch();
        if (actor.getRole() == null || (!"ROLE_CASHIER".equals(actor.getRole().getName())
                && !"ROLE_ADMIN".equals(actor.getRole().getName()))) {
            throw new ShiftAccessDeniedException();
        }
        WorkShift shift = workShiftRepository.findByIdAndBranchId(shiftId, branch.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ca làm việc."));
        if ("ROLE_CASHIER".equals(actor.getRole().getName()) && !actor.getId().equals(shift.getCashier().getId())) {
            throw new ShiftAccessDeniedException();
        }
        return toSummary(shift);
    }

    public CloseShiftResult closeShift(CloseShiftRequest request) {
        if (request == null || request.shiftId() == null || request.actualCash() == null || request.actualCash().signum() < 0) {
            throw new BusinessValidationException("Thông tin đóng ca không hợp lệ.");
        }
        User actor = currentUserService.getCurrentUser();
        Branch branch = currentUserService.requireCurrentBranch();
        if (actor.getRole() == null || (!"ROLE_CASHIER".equals(actor.getRole().getName()) && !"ROLE_ADMIN".equals(actor.getRole().getName()))) throw new ShiftAccessDeniedException();
        WorkShift shift = workShiftRepository.findForUpdateByIdAndBranchId(request.shiftId(), branch.getId()).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ca làm việc."));
        if ("ROLE_CASHIER".equals(actor.getRole().getName()) && !actor.getId().equals(shift.getCashier().getId())) throw new ShiftAccessDeniedException();
        if (shift.getStatus() == ShiftStatus.CLOSED) throw new ShiftAlreadyClosedException();
        if (shift.getStatus() != ShiftStatus.OPEN) throw new ShiftNotOpenException();
        ShiftPaymentAggregate a = paymentTransactionRepository.aggregateCompletedTransactionsByShiftId(shift.getId());
        BigDecimal cashIn = zero(a.getCashPaymentTotal()), cashOut = zero(a.getCashRefundTotal());
        BigDecimal expected = zero(shift.getOpeningCash()).add(cashIn).subtract(cashOut);
        BigDecimal total = zero(a.getGrossPaymentTotal()).subtract(zero(a.getRefundTotal()));
        shift.setExpectedCash(expected); shift.setActualCash(request.actualCash()); shift.setCashDifference(request.actualCash().subtract(expected));
        shift.setTotalSales(total); shift.setCashSales(cashIn.subtract(cashOut)); shift.setTransferSales(zero(a.getTransferPaymentTotal()).subtract(zero(a.getTransferRefundTotal()))); shift.setCardSales(zero(a.getCardPaymentTotal()).subtract(zero(a.getCardRefundTotal()))); shift.setRefundTotal(zero(a.getRefundTotal())); shift.setOrderCount(a.getDistinctOrderCount() == null ? 0 : a.getDistinctOrderCount());
        shift.setClosedAt(LocalDateTime.now()); shift.setClosedBy(actor); shift.setStatus(ShiftStatus.CLOSED);
        if (request.note() != null && !request.note().isBlank()) shift.setNote(shift.getNote() == null || shift.getNote().isBlank() ? request.note() : shift.getNote() + " | Close: " + request.note());
        WorkShift saved = workShiftRepository.save(shift);
        return new CloseShiftResult(saved.getId(), saved.getStatus(), saved.getClosedAt(), saved.getExpectedCash(), saved.getActualCash(), saved.getCashDifference(), saved.getTotalSales(), saved.getCashSales(), saved.getTransferSales(), saved.getCardSales(), saved.getRefundTotal(), saved.getOrderCount());
    }

    @Transactional(readOnly = true)
    public List<ShiftSummary> getBranchShifts() {
        User actor = currentUserService.getCurrentUser();
        Branch branch = currentUserService.requireCurrentBranch();
        if (actor.getRole() == null) throw new ShiftAccessDeniedException();
        List<WorkShift> shifts;
        if ("ROLE_ADMIN".equals(actor.getRole().getName())) {
            shifts = workShiftRepository.findByBranchIdOrderByOpenedAtDesc(branch.getId());
        } else if ("ROLE_CASHIER".equals(actor.getRole().getName())) {
            shifts = workShiftRepository.findByBranchIdAndCashierIdOrderByOpenedAtDesc(branch.getId(), actor.getId());
        } else {
            throw new ShiftAccessDeniedException();
        }
        return shifts.stream().map(this::toSummary).toList();
    }

    private ShiftSummary toSummary(WorkShift shift) {
        return new ShiftSummary(shift.getId(), shift.getStatus(), shift.getBranch().getId(), shift.getBranch().getName(),
                shift.getCashier().getId(), shift.getCashier().getUsername(), shift.getOpenedAt(), shift.getClosedAt(),
                shift.getOpeningCash(), shift.getExpectedCash(), shift.getActualCash(), shift.getCashDifference(),
                shift.getTotalSales(), shift.getCashSales(), shift.getTransferSales(), shift.getCardSales(),
                shift.getRefundTotal(), shift.getOrderCount(), shift.getNote());
    }
    private BigDecimal zero(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
}
