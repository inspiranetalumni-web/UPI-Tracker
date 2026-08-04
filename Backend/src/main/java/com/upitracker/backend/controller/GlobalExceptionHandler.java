package com.upitracker.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidationExceptions(MethodArgumentNotValidException ex) {
        String errorMessage = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ResponseEntity.badRequest().body(java.util.Map.of("error", errorMessage));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleGeneralExceptions(Exception ex) {
        // In production, log the full exception stack trace to your internal logging system here
        System.err.println("Internal Server Error: " + ex.getMessage());
        ex.printStackTrace();
        
        // Return a sanitized message to the client
        return ResponseEntity.internalServerError().body(java.util.Map.of("error", "An internal server error occurred."));
    }
}
