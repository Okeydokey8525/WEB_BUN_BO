package com.example.demo.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CloseShiftRequest(
        @NotNull Long shiftId,
        @NotNull @DecimalMin(value = "0", inclusive = true) BigDecimal actualCash,
        @Size(max = 500) String note
) { }
