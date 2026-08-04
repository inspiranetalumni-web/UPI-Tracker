package com.upitracker.backend.controller;

import com.google.cloud.firestore.Firestore;
import com.google.firebase.cloud.FirestoreClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
public class HealthController {

    @GetMapping("/health")
    public ResponseEntity<?> healthCheck() {
        String dbStatus = "disconnected";
        try {
            Firestore db = FirestoreClient.getFirestore();
            db.collection("health_checks").limit(1).get().get();
            dbStatus = "connected";
        } catch (Exception e) {
            dbStatus = "error: " + e.getMessage();
        }

        boolean isHealthy = "connected".equals(dbStatus);
        
        return ResponseEntity.status(isHealthy ? 200 : 503).body(Map.of(
            "status", isHealthy ? "healthy" : "unhealthy",
            "services", Map.of(
                "database", Map.of("status", dbStatus, "type", "Firestore")
            )
        ));
    }

    @GetMapping("/")
    public ResponseEntity<?> welcome() {
        return ResponseEntity.ok(Map.of(
            "message", "Welcome to UPI Tracker API (Java Spring Boot)",
            "status", "running",
            "healthCheck", "/health"
        ));
    }
}
