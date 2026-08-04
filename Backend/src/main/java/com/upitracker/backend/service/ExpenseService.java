package com.upitracker.backend.service;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.AggregateQuery;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.firebase.cloud.FirestoreClient;
import com.upitracker.backend.dto.ExpenseRequest;
import com.upitracker.backend.model.Expense;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ExpenseService {

    @Value("${GEMINI_API_KEY:}")
    private String geminiApiKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    private Firestore getDb() {
        return FirestoreClient.getFirestore();
    }

    private String getCategoryFromGemini(String payee) {
        if (geminiApiKey == null || geminiApiKey.isEmpty()) return "Other";
        try {
            String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=" + geminiApiKey;
            // STRICT PRIVACY: Prompt only contains the payee name string, no other data.
            // HIGH EFFICIENCY: Few-shot prompt forces strict output format and improves accuracy for Indian names.
            String prompt = "Classify the Indian merchant into one of: Food & Dining, Transport, Grocery, Bills, Health, Shopping, Transfer, Other.\n" +
                            "Rule: Reply with ONLY the exact category name.\n" +
                            "Examples:\n" +
                            "Swiggy -> Food & Dining\n" +
                            "Dmart -> Grocery\n" +
                            "Apollo -> Health\n" +
                            "Uber -> Transport\n" +
                            "Amazon -> Shopping\n" +
                            "Airtel -> Bills\n" +
                            "Rahul -> Transfer\n" +
                            "Merchant: " + payee + "\n" +
                            "Category:";
            
            Map<String, Object> body = new HashMap<>();
            body.put("contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))));
            
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            org.springframework.http.HttpEntity<Map<String, Object>> entity = new org.springframework.http.HttpEntity<>(body, headers);
            
            String response = restTemplate.postForObject(url, entity, String.class);
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(response);
            String text = root.path("candidates").get(0).path("content").path("parts").get(0).path("text").asText().trim();
            
            List<String> allowed = Arrays.asList("Food & Dining", "Transport", "Grocery", "Bills", "Health", "Shopping", "Transfer");
            for (String c : allowed) {
                if (text.equalsIgnoreCase(c)) return c;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "Other";
    }

    public Object createExpense(String userId, ExpenseRequest req) throws Exception {
        Firestore db = getDb();
        boolean isCancelled = "autopay_cancelled".equals(req.getType());
        
        if (!isCancelled && (req.getAmount() == null || req.getAmount() <= 0)) {
            throw new IllegalArgumentException("amount must be a positive number.");
        }

        String type = req.getType() != null ? req.getType() : "debit";
        String dateStr = req.getDate();
        if (dateStr != null && !dateStr.endsWith("Z") && !dateStr.contains("+") && dateStr.length() >= 19) {
            dateStr += "Z";
        }
        Instant expDate = dateStr != null ? Instant.parse(dateStr) : Instant.now();
        Timestamp timestamp = Timestamp.ofTimeSecondsAndNanos(expDate.getEpochSecond(), expDate.getNano());

        Instant threeMinutesAgo = expDate.minusSeconds(3 * 60);

        var dupSnapshot = db.collection("expenses")
                .whereEqualTo("userId", userId)
                .whereEqualTo("amount", req.getAmount())
                .whereGreaterThanOrEqualTo("date", Timestamp.ofTimeSecondsAndNanos(threeMinutesAgo.getEpochSecond(), 0))
                .get().get();

        boolean isDuplicate = false;
        QueryDocumentSnapshot duplicateDoc = null;

        for (QueryDocumentSnapshot doc : dupSnapshot.getDocuments()) {
            Expense e = doc.toObject(Expense.class);
            if (e.getDate() != null && e.getType().equals(type)) {
                long diff = Math.abs(e.getDate().toDate().getTime() - expDate.toEpochMilli());
                if (diff <= 3 * 60 * 1000) {
                    if (req.getUpiRef() != null && req.getUpiRef().equals(e.getUpiRef())) {
                        isDuplicate = true;
                        duplicateDoc = doc;
                    } else {
                        String p1 = e.getPayee().toLowerCase();
                        String p2 = req.getPayee().toLowerCase();
                        if (p1.equals(p2) || p1.equals("unknown") || p2.equals("unknown")) {
                            isDuplicate = true;
                            duplicateDoc = doc;
                        }
                    }
                }
            }
        }

        if (isDuplicate && duplicateDoc != null) {
            Map<String, Object> res = new HashMap<>();
            res.put("duplicate", true);
            res.put("message", "Transaction already logged.");
            return res;
        }

        String finalCategory = req.getCategory() != null ? req.getCategory() : "Other";
        if ("Other".equalsIgnoreCase(finalCategory) || "Unknown".equalsIgnoreCase(finalCategory)) {
            finalCategory = getCategoryFromGemini(req.getPayee().trim());
        }

        Map<String, Object> data = new HashMap<>();
        data.put("userId", userId);
        data.put("amount", req.getAmount());
        data.put("payee", req.getPayee().trim());
        data.put("category", finalCategory);
        data.put("upiApp", req.getUpiApp() != null ? req.getUpiApp() : "Other");
        data.put("upiRef", req.getUpiRef());
        data.put("note", req.getNote());
        data.put("date", timestamp);
        data.put("type", type);
        data.put("createdAt", Instant.now().toString());
        
        if (req.getAccountBalance() != null) {
            data.put("accountBalance", req.getAccountBalance());
        }
        if (req.getAccountName() != null) {
            data.put("accountName", req.getAccountName());
        }

        var docRef = db.collection("expenses").add(data).get();
        data.put("_id", docRef.getId());
        data.put("date", expDate.toString());
        return data;
    }

    public Map<String, Object> getExpenses(String userId, int page, int limit, String startDate, String endDate, String category) throws Exception {
        Firestore db = getDb();
        Query query = db.collection("expenses").whereEqualTo("userId", userId);

        if (category != null) {
            query = query.whereEqualTo("category", category);
        }

        if (startDate != null && endDate != null) {
            String sDate = startDate;
            String eDate = endDate;
            if (!sDate.endsWith("Z") && !sDate.contains("+") && sDate.length() >= 19) sDate += "Z";
            if (!eDate.endsWith("Z") && !eDate.contains("+") && eDate.length() >= 19) eDate += "Z";
            
            Instant startInst = Instant.parse(sDate);
            Instant endInst = Instant.parse(eDate);
            
            Timestamp start = Timestamp.ofTimeSecondsAndNanos(startInst.getEpochSecond(), startInst.getNano());
            Timestamp end = Timestamp.ofTimeSecondsAndNanos(endInst.getEpochSecond(), endInst.getNano());

            query = query.whereGreaterThanOrEqualTo("date", start)
                         .whereLessThanOrEqualTo("date", end);
        }

        query = query.orderBy("date", Query.Direction.DESCENDING);

        // Firestore native count aggregation (does not cost document reads)
        AggregateQuery countQuery = query.count();
        long total = countQuery.get().get().getCount();

        // Paginate using offset (costs reads, but only for skipped items)
        query = query.limit(limit).offset((page - 1) * limit);

        var snapshot = query.get().get();

        List<Map<String, Object>> formatted = snapshot.getDocuments().stream().map(d -> {
            Expense e = d.toObject(Expense.class);
            Map<String, Object> map = new HashMap<>();
            map.put("_id", d.getId());
            map.put("amount", e.getAmount());
            map.put("payee", e.getPayee());
            map.put("category", e.getCategory());
            map.put("upiApp", e.getUpiApp());
            map.put("upiRef", e.getUpiRef());
            map.put("note", e.getNote());
            map.put("type", e.getType());
            map.put("date", e.getDate() != null ? e.getDate().toDate().toInstant().toString() : null);
            return map;
        }).collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("expenses", formatted);
        Map<String, Object> pagination = new HashMap<>();
        pagination.put("total", total);
        pagination.put("page", page);
        pagination.put("pages", (int) Math.ceil((double) total / limit));
        result.put("pagination", pagination);
        return result;
    }

    public Map<String, Object> getMonthlySummary(String userId, Integer month, Integer year) throws Exception {
        Firestore db = getDb();
        var snapshot = db.collection("expenses").whereEqualTo("userId", userId).get().get();

        int targetMonth = month != null ? month : Calendar.getInstance().get(Calendar.MONTH) + 1;
        int targetYear = year != null ? year : Calendar.getInstance().get(Calendar.YEAR);

        List<Expense> debitsOnly = snapshot.getDocuments().stream().map(d -> d.toObject(Expense.class))
            .filter(e -> {
                if (e.getDate() == null || !"debit".equals(e.getType() != null ? e.getType() : "debit")) return false;
                Calendar cal = Calendar.getInstance();
                cal.setTime(e.getDate().toDate());
                return (cal.get(Calendar.MONTH) + 1) == targetMonth && cal.get(Calendar.YEAR) == targetYear;
            }).collect(Collectors.toList());

        Map<String, Map<String, Number>> catMap = new HashMap<>();
        Map<String, Map<String, Number>> dailyMap = new HashMap<>();
        double total = 0;

        for (Expense e : debitsOnly) {
            total += e.getAmount();
            
            // Category
            String cat = e.getCategory();
            catMap.putIfAbsent(cat, new HashMap<>(Map.of("total", 0.0, "count", 0)));
            catMap.get(cat).put("total", catMap.get(cat).get("total").doubleValue() + e.getAmount());
            catMap.get(cat).put("count", catMap.get(cat).get("count").intValue() + 1);

            // Daily trend
            String dateStr = e.getDate().toDate().toInstant().toString().split("T")[0];
            dailyMap.putIfAbsent(dateStr, new HashMap<>(Map.of("total", 0.0, "count", 0)));
            dailyMap.get(dateStr).put("total", dailyMap.get(dateStr).get("total").doubleValue() + e.getAmount());
            dailyMap.get(dateStr).put("count", dailyMap.get(dateStr).get("count").intValue() + 1);
        }

        var categoryBreakdown = catMap.entrySet().stream()
            .map(entry -> Map.of("_id", entry.getKey(), "total", entry.getValue().get("total"), "count", entry.getValue().get("count")))
            .sorted((a, b) -> Double.compare((Double) b.get("total"), (Double) a.get("total")))
            .collect(Collectors.toList());

        var dailyTrend = dailyMap.entrySet().stream()
            .map(entry -> Map.of("_id", entry.getKey(), "total", entry.getValue().get("total"), "count", entry.getValue().get("count")))
            .sorted((a, b) -> ((String) a.get("_id")).compareTo((String) b.get("_id")))
            .collect(Collectors.toList());

        return Map.of(
            "month", targetMonth, "year", targetYear,
            "total", total, "count", debitsOnly.size(),
            "categoryBreakdown", categoryBreakdown,
            "dailyTrend", dailyTrend
        );
    }

    public List<Expense> exportExpenses(String userId, Integer month, Integer year) throws Exception {
        Firestore db = getDb();
        var snapshot = db.collection("expenses").whereEqualTo("userId", userId).get().get();

        List<Expense> expenses = snapshot.getDocuments().stream().map(d -> d.toObject(Expense.class)).collect(Collectors.toList());
        if (month != null && year != null) {
            expenses = expenses.stream().filter(e -> {
                if (e.getDate() == null) return false;
                Calendar cal = Calendar.getInstance();
                cal.setTime(e.getDate().toDate());
                return (cal.get(Calendar.MONTH) + 1) == month && cal.get(Calendar.YEAR) == year;
            }).collect(Collectors.toList());
        }
        expenses.sort((a, b) -> b.getDate().compareTo(a.getDate()));
        return expenses;
    }

    public Map<String, Object> getYearlyStats(String userId, Integer year) throws Exception {
        Firestore db = getDb();
        var snapshot = db.collection("expenses").whereEqualTo("userId", userId).get().get();

        int targetYear = year != null ? year : Calendar.getInstance().get(Calendar.YEAR);
        List<Expense> filtered = snapshot.getDocuments().stream().map(d -> d.toObject(Expense.class))
            .filter(e -> {
                if (e.getDate() == null) return false;
                Calendar cal = Calendar.getInstance();
                cal.setTime(e.getDate().toDate());
                return cal.get(Calendar.YEAR) == targetYear;
            }).collect(Collectors.toList());

        Map<Integer, Map<String, Number>> monthMap = new HashMap<>();
        for (Expense e : filtered) {
            Calendar cal = Calendar.getInstance();
            cal.setTime(e.getDate().toDate());
            int m = cal.get(Calendar.MONTH) + 1;
            monthMap.putIfAbsent(m, new HashMap<>(Map.of("total", 0.0, "count", 0)));
            monthMap.get(m).put("total", monthMap.get(m).get("total").doubleValue() + e.getAmount());
            monthMap.get(m).put("count", monthMap.get(m).get("count").intValue() + 1);
        }

        List<Map<String, Object>> months = new ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            Map<String, Number> data = monthMap.getOrDefault(i, Map.of("total", 0.0, "count", 0));
            months.add(Map.of("month", i, "total", data.get("total"), "count", data.get("count")));
        }
        return Map.of("year", targetYear, "months", months);
    }

    public void deleteExpense(String userId, String id) throws Exception {
        Firestore db = getDb();
        var docRef = db.collection("expenses").document(id);
        var doc = docRef.get().get();
        if (!doc.exists() || !userId.equals(doc.getString("userId"))) {
            throw new IllegalArgumentException("Expense not found.");
        }
        docRef.delete().get();
    }

    public Object updateExpense(String userId, String id, Map<String, Object> reqBody) throws Exception {
        Firestore db = getDb();
        var docRef = db.collection("expenses").document(id);
        var doc = docRef.get().get();

        if (!doc.exists() || !userId.equals(doc.getString("userId"))) {
            throw new IllegalArgumentException("Expense not found.");
        }

        Map<String, Object> update = new HashMap<>();
        List<String> allowed = Arrays.asList("amount", "payee", "category", "upiApp", "note", "date", "upiRef", "type");
        
        for (String field : allowed) {
            if (reqBody.containsKey(field)) {
                if ("date".equals(field)) {
                    String d = (String) reqBody.get(field);
                    if (!d.endsWith("Z") && !d.contains("+") && d.length() >= 19) d += "Z";
                    Instant instant = Instant.parse(d);
                    update.put(field, Timestamp.ofTimeSecondsAndNanos(instant.getEpochSecond(), instant.getNano()));
                } else if ("amount".equals(field)) {
                    double amt = Double.parseDouble(reqBody.get(field).toString());
                    if (amt <= 0) throw new IllegalArgumentException("amount must be a positive number.");
                    update.put(field, amt);
                } else if ("payee".equals(field)) {
                    String p = ((String) reqBody.get(field)).trim();
                    if (p.isEmpty()) throw new IllegalArgumentException("payee is required.");
                    update.put(field, p);
                } else if ("upiRef".equals(field) && reqBody.get(field) != null) {
                    String ref = ((String) reqBody.get(field)).trim();
                    if (!ref.isEmpty()) {
                        var dupSnap = db.collection("expenses").whereEqualTo("userId", userId).whereEqualTo("upiRef", ref).limit(2).get().get();
                        if (dupSnap.getDocuments().stream().anyMatch(d -> !d.getId().equals(id))) {
                            throw new IllegalArgumentException("Transaction with this UPI reference already exists.");
                        }
                    }
                    update.put(field, ref);
                } else {
                    update.put(field, reqBody.get(field));
                }
            }
        }

        if (update.isEmpty()) throw new IllegalArgumentException("No valid fields provided for update.");
        docRef.update(update).get();
        
        var updated = docRef.get().get();
        Map<String, Object> result = new HashMap<>(updated.getData());
        result.put("_id", updated.getId());
        result.put("date", updated.getTimestamp("date").toDate().toInstant().toString());
        return result;
    }

    public List<Map<String, Integer>> getTrackedMonths(String userId) throws Exception {
        Firestore db = getDb();
        var snapshot = db.collection("expenses").whereEqualTo("userId", userId).get().get();

        Map<String, Map<String, Integer>> monthsMap = new HashMap<>();
        for (var doc : snapshot.getDocuments()) {
            Timestamp ts = doc.getTimestamp("date");
            if (ts != null) {
                Calendar cal = Calendar.getInstance();
                cal.setTime(ts.toDate());
                int m = cal.get(Calendar.MONTH) + 1;
                int y = cal.get(Calendar.YEAR);
                monthsMap.put(y + "-" + m, Map.of("month", m, "year", y));
            }
        }
        Calendar now = Calendar.getInstance();
        monthsMap.put(now.get(Calendar.YEAR) + "-" + (now.get(Calendar.MONTH) + 1), 
            Map.of("month", now.get(Calendar.MONTH) + 1, "year", now.get(Calendar.YEAR)));

        return monthsMap.values().stream().sorted((a, b) -> {
            if (!a.get("year").equals(b.get("year"))) return b.get("year") - a.get("year");
            return b.get("month") - a.get("month");
        }).collect(Collectors.toList());
    }
}
