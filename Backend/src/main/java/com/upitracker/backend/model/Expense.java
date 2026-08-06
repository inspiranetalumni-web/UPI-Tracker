package com.upitracker.backend.model;

import lombok.Data;
import com.google.cloud.Timestamp;

@Data
public class Expense {
    private String _id;
    private String userId;
    private Double amount;
    private String payee;
    private String category;
    private String upiApp;
    private String upiRef;
    private String note;
    private Timestamp date;
    private String type;
    private Double accountBalance;
    private String accountName;
    private String createdAt;
    private Boolean isSelfTransfer;
}
