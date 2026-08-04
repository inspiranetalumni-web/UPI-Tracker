package com.upitracker.backend.dto;

import lombok.Data;

@Data
public class ProfileUpdateRequest {
    private String name;
    private String email;
    private String phone;
    
    private java.util.Map<String, Double> budgets;
    private java.util.List<java.util.Map<String, Object>> goals;
    private java.util.Map<String, Double> balances;
    private Boolean enableNotifications;
}
