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
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.model.Branch;
import com.example.demo.model.User;
import com.example.demo.model.WorkShift;
import com.example.demo.model.enums.ShiftStatus;
import com.example.demo.repository.PaymentTransactionRepository;
import com.example.demo.repository.WorkShiftRepository;
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
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Transactional(readOnly = true)
    public List<ShiftSummary> getBranchShifts() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    private ShiftSummary toSummary(WorkShift shift) {
        return new ShiftSummary(shift.getId(), shift.getStatus(), shift.getBranch().getId(), shift.getBranch().getName(),
                shift.getCashier().getId(), shift.getCashier().getUsername(), shift.getOpenedAt(), shift.getClosedAt(),
                shift.getOpeningCash(), shift.getExpectedCash(), shift.getActualCash(), shift.getCashDifference(),
                shift.getTotalSales(), shift.getCashSales(), shift.getTransferSales(), shift.getCardSales(),
                shift.getRefundTotal(), shift.getOrderCount(), shift.getNote());
    }
}
