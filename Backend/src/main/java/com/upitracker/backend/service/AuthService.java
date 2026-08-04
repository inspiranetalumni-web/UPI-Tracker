package com.upitracker.backend.service;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import com.google.firebase.auth.UserRecord;
import com.google.firebase.cloud.FirestoreClient;
import com.upitracker.backend.config.JwtTokenProvider;
import com.upitracker.backend.dto.AuthResponse;
import com.upitracker.backend.model.User;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Service
public class AuthService {

    private final JwtTokenProvider tokenProvider;

    public AuthService(JwtTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    private Firestore getDb() {
        return FirestoreClient.getFirestore();
    }

    public AuthResponse verifyFirebaseToken(String idToken, String name, String email) throws Exception {
        FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdToken(idToken);
        String phoneNumber = (String) decodedToken.getClaims().get("phone_number");

        if (phoneNumber == null) {
            throw new IllegalArgumentException("Token does not contain a verified phone number.");
        }

        String rawPhone = phoneNumber;
        if (phoneNumber.startsWith("+91") && phoneNumber.length() == 13) {
            rawPhone = phoneNumber.substring(3);
        }

        Firestore db = getDb();
        QuerySnapshot userSnapshot = db.collection("users").whereEqualTo("phone", rawPhone).limit(1).get().get();
        if (userSnapshot.isEmpty()) {
            userSnapshot = db.collection("users").whereEqualTo("phone", phoneNumber).limit(1).get().get();
        }

        if (!userSnapshot.isEmpty()) {
            QueryDocumentSnapshot userDoc = userSnapshot.getDocuments().get(0);
            User user = userDoc.toObject(User.class);
            user.setId(userDoc.getId());
            String token = tokenProvider.generateToken(user.getId());
            return new AuthResponse(token, user);
        }

        if (name == null || email == null) {
            return new AuthResponse(true, rawPhone, "Account not found. Please provide name and email to complete registration.");
        }

        String normalizedEmail = email.toLowerCase().trim();
        QuerySnapshot emailSnapshot = db.collection("users").whereEqualTo("email", normalizedEmail).limit(1).get().get();
        if (!emailSnapshot.isEmpty()) {
            throw new IllegalArgumentException("Email already registered.");
        }

        String firebaseUid = decodedToken.getUid();
        
        Map<String, Object> userData = new HashMap<>();
        userData.put("name", name.trim());
        userData.put("email", normalizedEmail);
        userData.put("phone", rawPhone);
        userData.put("isVerified", true);
        userData.put("createdAt", Instant.now().toString());

        db.collection("users").document(firebaseUid).set(userData).get();

        User newUser = new User();
        newUser.setId(firebaseUid);
        newUser.setName(name.trim());
        newUser.setEmail(normalizedEmail);
        newUser.setPhone(rawPhone);
        
        try {
            UserRecord.UpdateRequest request = new UserRecord.UpdateRequest(firebaseUid)
                .setDisplayName(name.trim())
                .setEmail(normalizedEmail);
            FirebaseAuth.getInstance().updateUser(request);
        } catch (Exception ignored) { }

        String token = tokenProvider.generateToken(firebaseUid);
        return new AuthResponse(token, newUser);
    }

    public User getMe(String userId) throws Exception {
        Firestore db = getDb();
        var doc = db.collection("users").document(userId).get().get();
        if (!doc.exists()) throw new IllegalArgumentException("User not found");
        User u = doc.toObject(User.class);
        u.setId(doc.getId());
        return u;
    }

    public User updateProfile(String userId, com.upitracker.backend.dto.ProfileUpdateRequest req) throws Exception {
        Firestore db = getDb();
        var docRef = db.collection("users").document(userId);
        var doc = docRef.get().get();
        if (!doc.exists()) throw new IllegalArgumentException("User not found.");

        Map<String, Object> update = new HashMap<>();
        User currentUser = doc.toObject(User.class);

        if (req.getName() != null) {
            String trimmedName = req.getName().trim();
            if (trimmedName.isEmpty()) throw new IllegalArgumentException("Name cannot be empty.");
            update.put("name", trimmedName);
        }

        if (req.getEmail() != null) {
            String normalizedEmail = req.getEmail().toLowerCase().trim();
            if (normalizedEmail.isEmpty()) throw new IllegalArgumentException("Email cannot be empty.");
            if (!normalizedEmail.equals(currentUser.getEmail())) {
                throw new SecurityException("Email updates require verification. Please use the dedicated email change flow.");
            }
        }

        if (req.getPhone() != null) {
            String trimmedPhone = req.getPhone().trim();
            if (trimmedPhone.length() < 10) throw new IllegalArgumentException("Mobile number must be at least 10 digits.");
            
            QuerySnapshot phoneSnapshot = db.collection("users").whereEqualTo("phone", trimmedPhone).get().get();
            boolean exists = phoneSnapshot.getDocuments().stream().anyMatch(d -> !d.getId().equals(userId));
            if (exists) throw new IllegalArgumentException("Mobile number already in use.");
            update.put("phone", trimmedPhone);
        }

        if (req.getBudgets() != null) {
            update.put("budgets", req.getBudgets());
        }
        if (req.getGoals() != null) {
            update.put("goals", req.getGoals());
        }
        if (req.getBalances() != null) {
            update.put("balances", req.getBalances());
        }
        if (req.getEnableNotifications() != null) {
            update.put("enableNotifications", req.getEnableNotifications());
        }

        if (!update.isEmpty()) {
            docRef.update(update).get();
        }

        var updatedDoc = docRef.get().get();
        User updatedUser = updatedDoc.toObject(User.class);
        updatedUser.setId(updatedDoc.getId());
        
        try {
            UserRecord.UpdateRequest request = new UserRecord.UpdateRequest(userId)
                .setDisplayName(updatedUser.getName())
                .setEmail(updatedUser.getEmail());
            FirebaseAuth.getInstance().updateUser(request);
        } catch (Exception ignored) {}

        return updatedUser;
    }
}
