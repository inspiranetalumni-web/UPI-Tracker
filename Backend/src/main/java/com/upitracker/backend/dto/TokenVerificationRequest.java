package com.upitracker.backend.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;

@Data
public class TokenVerificationRequest {
    @NotBlank(message = "Firebase ID Token is required.")
    private String idToken;
    private String name;
    private String email;
}
