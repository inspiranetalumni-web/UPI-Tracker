package com.upitracker.backend.dto;

import lombok.Data;
import com.upitracker.backend.model.User;

@Data
public class AuthResponse {
    private String token;
    private User user;
    private Boolean newUser;
    private String phone;
    private String message;
    
    public AuthResponse(String token, User user) {
        this.token = token;
        this.user = user;
    }
    
    public AuthResponse(Boolean newUser, String phone, String message) {
        this.newUser = newUser;
        this.phone = phone;
        this.message = message;
    }
}
