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

                String json = String.format("{\n" +
                        "  \"type\": \"service_account\",\n" +
                        "  \"project_id\": \"%s\",\n" +
                        "  \"private_key_id\": \"%s\",\n" +
                        "  \"private_key\": \"%s\",\n" +
                        "  \"client_email\": \"%s\",\n" +
                        "  \"client_id\": \"dummy_client_id\"\n" +
                        "}", projectId, privateKeyId, privateKey.replace("\\n", "\n"), clientEmail);

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
