package com.upitracker.backend.model;

import lombok.Data;
import java.time.Instant;

@Data
public class User {
    private String id;
    private String name;
    private String email;
    private String phone;
    private Boolean isVerified;
    private String createdAt;
    
    private java.util.Map<String, Double> budgets;
    private java.util.List<java.util.Map<String, Object>> goals;
    private java.util.Map<String, Double> balances;
    private Boolean enableNotifications;
}
