package com.upitracker.backend.dto;

import lombok.Data;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

@Data
public class ExpenseRequest {
    @NotNull(message = "amount is required.")
    @Positive(message = "amount must be positive.")
    @DecimalMax(value = "10000000.00", message = "amount must not exceed 1 crore.")
    private Double amount;

    @NotBlank(message = "payee is required.")
    @Size(max = 200, message = "payee must not exceed 200 characters.")
    private String payee;

    @Size(max = 100, message = "category must not exceed 100 characters.")
    private String category;

    @Size(max = 100, message = "upiApp must not exceed 100 characters.")
    private String upiApp;

    @Size(max = 100, message = "upiRef must not exceed 100 characters.")
    private String upiRef;

    @Size(max = 500, message = "note must not exceed 500 characters.")
    private String note;

    @NotBlank(message = "date is required.")
    @Size(max = 50, message = "date must not exceed 50 characters.")
    private String date; // ISO string

    @Pattern(regexp = "^(debit|credit|transfer|autopay_cancelled|autopay_created)$",
             message = "type must be one of: debit, credit, transfer, autopay_cancelled, autopay_created")
    private String type;

    private Double accountBalance;

    @Size(max = 150, message = "accountName must not exceed 150 characters.")
    private String accountName;

    private Boolean isSelfTransfer;
}

