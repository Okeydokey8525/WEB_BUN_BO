package com.example.demo.dto.request;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record ReportFilterRequest(
        @NotNull LocalDate fromDate,
        @NotNull LocalDate toDate
) {
}
