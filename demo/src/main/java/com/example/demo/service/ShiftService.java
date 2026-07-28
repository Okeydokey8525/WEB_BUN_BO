package com.example.demo.service;

import com.example.demo.dto.request.CloseShiftRequest;
import com.example.demo.dto.request.OpenShiftRequest;
import com.example.demo.dto.response.CloseShiftResult;
import com.example.demo.dto.response.OpenShiftResult;
import com.example.demo.dto.response.ShiftSummary;
import com.example.demo.repository.PaymentTransactionRepository;
import com.example.demo.repository.WorkShiftRepository;
import com.example.demo.security.BranchAccessService;
import com.example.demo.security.CurrentUserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Transactional(readOnly = true)
    public ShiftSummary getCurrentShift() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Transactional(readOnly = true)
    public ShiftSummary getShift(Long shiftId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public CloseShiftResult closeShift(CloseShiftRequest request) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Transactional(readOnly = true)
    public List<ShiftSummary> getBranchShifts() {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
