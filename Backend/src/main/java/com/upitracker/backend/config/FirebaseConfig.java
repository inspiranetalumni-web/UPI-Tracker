package com.upitracker.backend.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.springframework.context.annotation.Configuration;
import jakarta.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

@Configuration
public class FirebaseConfig {

    @PostConstruct
    public void initialize() {
        try {
            // Check if already initialized
            if (FirebaseApp.getApps().isEmpty()) {
                String projectId = System.getProperty("FIREBASE_PROJECT_ID", System.getenv("FIREBASE_PROJECT_ID"));
                String clientEmail = System.getProperty("FIREBASE_CLIENT_EMAIL", System.getenv("FIREBASE_CLIENT_EMAIL"));
                String privateKey = System.getProperty("FIREBASE_PRIVATE_KEY", System.getenv("FIREBASE_PRIVATE_KEY"));

                String privateKeyId = System.getProperty("FIREBASE_PRIVATE_KEY_ID", System.getenv("FIREBASE_PRIVATE_KEY_ID"));
                if (privateKeyId == null) privateKeyId = "dummy_key_id";

                if (projectId == null || clientEmail == null || privateKey == null) {
                    System.out.println("Warning: Firebase environment variables not set. Firebase not initialized.");
                    return;
                }

                java.util.Map<String, String> creds = new java.util.HashMap<>();
                creds.put("type", "service_account");
                creds.put("project_id", projectId);
                creds.put("private_key_id", privateKeyId);
                creds.put("private_key", privateKey.replace("\\n", "\n"));
                creds.put("client_email", clientEmail);
                creds.put("client_id", "dummy_client_id");

                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                String json = mapper.writeValueAsString(creds);

                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))))
                        .build();

                FirebaseApp.initializeApp(options);
                System.out.println("Firebase successfully initialized.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
