package com.upitracker.backend.controller;

import com.upitracker.backend.dto.ExpenseRequest;
import com.upitracker.backend.service.ExpenseService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/expenses")
public class ExpenseController {

    private final ExpenseService expenseService;

    public ExpenseController(ExpenseService expenseService) {
        this.expenseService = expenseService;
    }

    @PostMapping
    public ResponseEntity<?> createExpense(Authentication authentication, @Valid @RequestBody ExpenseRequest req) throws Exception {
        Object response = expenseService.createExpense(authentication.getName(), req);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<?> getExpenses(
            Authentication authentication,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String category) throws Exception {
        var response = expenseService.getExpenses(authentication.getName(), page, limit, startDate, endDate, category);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/summary")
    public ResponseEntity<?> getMonthlySummary(
            Authentication authentication,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year) throws Exception {
        var response = expenseService.getMonthlySummary(authentication.getName(), month, year);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/export")
    public ResponseEntity<?> exportExpenses(
            Authentication authentication,
            @RequestParam(defaultValue = "json") String format,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year) throws Exception {
        var expenses = expenseService.exportExpenses(authentication.getName(), month, year);
        if ("csv".equalsIgnoreCase(format)) {
            String csv = com.upitracker.backend.util.CsvExporter.export(expenses);
            return ResponseEntity.ok()
                    .header("Content-Type", "text/csv")
                    .header("Content-Disposition", "attachment; filename=expenses.csv")
                    .body(csv);
        }
        return ResponseEntity.ok(expenses);
    }

    @GetMapping("/stats/yearly")
    public ResponseEntity<?> getYearlyStats(
            Authentication authentication,
            @RequestParam(required = false) Integer year) throws Exception {
        var response = expenseService.getYearlyStats(authentication.getName(), year);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/months")
    public ResponseEntity<?> getTrackedMonths(Authentication authentication) throws Exception {
        var response = expenseService.getTrackedMonths(authentication.getName());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteExpense(Authentication authentication, @PathVariable String id) throws Exception {
        expenseService.deleteExpense(authentication.getName(), id);
        return ResponseEntity.ok(java.util.Map.of("success", true));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<?> updateExpense(
            Authentication authentication, 
            @PathVariable String id, 
            @RequestBody java.util.Map<String, Object> body) throws Exception {
        var response = expenseService.updateExpense(authentication.getName(), id, body);
        return ResponseEntity.ok(response);
    }
}
