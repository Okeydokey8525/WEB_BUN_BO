package com.example.demo;

import com.example.demo.dto.request.PayOrderRequest;
import com.example.demo.dto.request.RefundOrderRequest;
import com.example.demo.model.enums.PaymentMethod;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertFalse;

class PaymentRequestValidationTests {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void rejectsMissingPaymentMethod() {
        assertFalse(validator.validate(new PayOrderRequest(1L, null, BigDecimal.TEN, null, null)).isEmpty());
    }

    @Test
    void rejectsReferenceCodeLongerThanLimit() {
        assertFalse(validator.validate(new PayOrderRequest(1L, PaymentMethod.VIETQR, null, "x".repeat(101), null)).isEmpty());
    }

    @Test
    void rejectsBlankRefundReason() {
        assertFalse(validator.validate(new RefundOrderRequest(1L, " ")).isEmpty());
    }
}
