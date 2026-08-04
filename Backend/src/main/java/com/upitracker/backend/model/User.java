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
}
