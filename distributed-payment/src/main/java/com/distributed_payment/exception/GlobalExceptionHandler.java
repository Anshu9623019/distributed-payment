//package com.distributed_payment.exception;
//
//import org.springframework.http.HttpStatus;
//import org.springframework.http.ResponseEntity;
//import org.springframework.security.authentication.BadCredentialsException;
//import org.springframework.web.bind.MethodArgumentNotValidException;
//import org.springframework.web.bind.annotation.ExceptionHandler;
//import org.springframework.web.bind.annotation.RestControllerAdvice;
//
//import java.time.Instant;
//import java.util.LinkedHashMap;
//import java.util.Map;
//import java.util.stream.Collectors;
//
//@RestControllerAdvice
//public class GlobalExceptionHandler {
//
//    @ExceptionHandler(ResourceNotFoundException.class)
//    public ResponseEntity<Map<String, Object>> handleNotFound(ResourceNotFoundException ex) {
//        return build(HttpStatus.NOT_FOUND, ex.getMessage());
//    }
//
//    @ExceptionHandler(DuplicateResourceException.class)
//    public ResponseEntity<Map<String, Object>> handleDuplicate(DuplicateResourceException ex) {
//        return build(HttpStatus.CONFLICT, ex.getMessage());
//    }
//
//    @ExceptionHandler(InsufficientBalanceException.class)
//    public ResponseEntity<Map<String, Object>> handleInsufficientBalance(InsufficientBalanceException ex) {
//        return build(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
//    }
//
//    @ExceptionHandler(RequestInProgressException.class)
//    public ResponseEntity<Map<String, Object>> handleInProgress(RequestInProgressException ex) {
//        return build(HttpStatus.CONFLICT, ex.getMessage());
//    }
//
//    @ExceptionHandler(BadCredentialsException.class)
//    public ResponseEntity<Map<String, Object>> handleBadCredentials(BadCredentialsException ex) {
//        return build(HttpStatus.UNAUTHORIZED, "Invalid email or password");
//    }
//
//    @ExceptionHandler(MethodArgumentNotValidException.class)
//    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
//        String message = ex.getBindingResult().getFieldErrors().stream()
//                .map(err -> err.getField() + ": " + err.getDefaultMessage())
//                .collect(Collectors.joining(", "));
//        return build(HttpStatus.BAD_REQUEST, message);
//    }
//
//    private ResponseEntity<Map<String, Object>> build(HttpStatus status, String message) {
//        Map<String, Object> body = new LinkedHashMap<>();
//        body.put("timestamp", Instant.now().toString());
//        body.put("status", status.value());
//        body.put("error", status.getReasonPhrase());
//        body.put("message", message);
//        return ResponseEntity.status(status).body(body);
//    }
//
//
//
//}



package com.distributed_payment.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(ResourceNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicate(DuplicateResourceException ex) {
        return build(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(InsufficientBalanceException.class)
    public ResponseEntity<Map<String, Object>> handleInsufficientBalance(InsufficientBalanceException ex) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }

    @ExceptionHandler(RequestInProgressException.class)
    public ResponseEntity<Map<String, Object>> handleInProgress(RequestInProgressException ex) {
        return build(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(PaymentBlockedException.class)
    public ResponseEntity<Map<String, Object>> handleBlocked(PaymentBlockedException ex) {
        return build(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleBadCredentials(BadCredentialsException ex) {
        return build(HttpStatus.UNAUTHORIZED, "Invalid email or password");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return build(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseEntity<Map<String, Object>> build(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}
