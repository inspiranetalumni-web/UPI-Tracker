package com.upitracker.backend.dto;

import lombok.Data;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

@Data
public class ExpenseRequest {
    @NotNull(message = "amount is required.")
    @Positive(message = "amount must be positive.")
    private Double amount;
    
    @NotBlank(message = "payee is required.")
    private String payee;
    
    private String category;
    private String upiApp;
    private String upiRef;
    private String note;
    @NotBlank(message = "date is required.")
    private String date; // ISO string
    private String type;
    private Double accountBalance;
    private String accountName;
}
