package com.upitracker.backend.controller;

import com.upitracker.backend.dto.AuthResponse;
import com.upitracker.backend.dto.ProfileUpdateRequest;
import com.upitracker.backend.dto.TokenVerificationRequest;
import com.upitracker.backend.model.User;
import com.upitracker.backend.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/verify-firebase-token")
    public ResponseEntity<?> verifyFirebaseToken(@Valid @RequestBody TokenVerificationRequest req) {
        try {
            AuthResponse response = authService.verifyFirebaseToken(req.getIdToken(), req.getName(), req.getEmail());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<?> getMe(Authentication authentication) {
        try {
            User user = authService.getMe(authentication.getName());
            return ResponseEntity.ok(java.util.Map.of("user", user));
        } catch (Exception e) {
            return ResponseEntity.status(404).body(java.util.Map.of("error", e.getMessage()));
        }
    }

    @PatchMapping("/profile")
    public ResponseEntity<?> updateProfile(Authentication authentication, @RequestBody ProfileUpdateRequest req) {
        try {
            User updatedUser = authService.updateProfile(authentication.getName(), req.getName(), req.getEmail(), req.getPhone());
            return ResponseEntity.ok(java.util.Map.of("user", updatedUser));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", e.getMessage()));
        }
    }
}
