package com.retry.api.controller;

import com.retry.api.service.AgentClient.AgentUnavailableException;
import java.time.Instant;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(AgentUnavailableException.class)
    public ResponseEntity<Map<String, Object>> handleAgentUnavailable(AgentUnavailableException exception) {
        return response(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleStatus(ResponseStatusException exception) {
        return response(HttpStatus.valueOf(exception.getStatusCode().value()), exception.getReason());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(error -> error.getField() + " 항목을 확인해 주세요.")
            .orElse("요청 값을 확인해 주세요.");
        return response(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler({
        HttpMessageNotReadableException.class,
        MissingRequestHeaderException.class,
        MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<Map<String, Object>> handleInvalidRequest(Exception exception) {
        return response(HttpStatus.BAD_REQUEST, "요청 값을 확인해 주세요.");
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, Object>> handleDataAccess(DataAccessException exception) {
        return response(HttpStatus.SERVICE_UNAVAILABLE, "데모 상태 저장소를 사용할 수 없습니다. 잠시 후 다시 시도해 주세요.");
    }

    private ResponseEntity<Map<String, Object>> response(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of(
            "status", status.value(),
            "error", status.getReasonPhrase(),
            "message", message == null ? "요청을 처리할 수 없습니다." : message,
            "timestamp", Instant.now().toString()
        ));
    }
}
