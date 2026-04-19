package com.spry.shared.exception;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.net.URI;
import java.util.List;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ProblemDetail handleApiException(ApiException ex) {
        var problem = ProblemDetail.forStatusAndDetail(ex.status(), ex.getMessage());
        problem.setTitle(titleFor(ex.status()));
        enrichWithTraceId(problem);
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        List<Map<String, String>> errors = ex.getBindingResult().getAllErrors().stream()
                .map(error -> {
                    String field = error instanceof FieldError fe ? fe.getField() : error.getObjectName();
                    return Map.of("field", field, "message", error.getDefaultMessage());
                })
                .toList();

        var problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Validation Failed");
        problem.setType(URI.create("https://api.spry.io/errors/validation"));
        problem.setProperty("errors", errors);
        enrichWithTraceId(problem);
        return problem;
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Access denied");
        problem.setTitle("Forbidden");
        enrichWithTraceId(problem);
        return problem;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
        List<Map<String, String>> errors = ex.getConstraintViolations().stream()
                .map(v -> Map.of("field", v.getPropertyPath().toString(), "message", v.getMessage()))
                .toList();

        var problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Validation Failed");
        problem.setType(URI.create("https://api.spry.io/errors/validation"));
        problem.setProperty("errors", errors);
        enrichWithTraceId(problem);
        return problem;
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ProblemDetail handleMethodValidation(HandlerMethodValidationException ex) {
        List<Map<String, String>> errors = ex.getAllErrors().stream()
                .map(e -> Map.of("message", e.getDefaultMessage() != null ? e.getDefaultMessage() : "Invalid value"))
                .toList();

        var problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Validation Failed");
        problem.setType(URI.create("https://api.spry.io/errors/validation"));
        problem.setProperty("errors", errors);
        enrichWithTraceId(problem);
        return problem;
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "Request conflicts with existing data");
        problem.setTitle("Conflict");
        enrichWithTraceId(problem);
        return problem;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred");
        problem.setTitle("Internal Server Error");
        enrichWithTraceId(problem);
        return problem;
    }

    private void enrichWithTraceId(ProblemDetail problem) {
        String traceId = MDC.get("traceId");
        if (traceId != null) {
            problem.setProperty("traceId", traceId);
        }
    }

    private String titleFor(HttpStatus status) {
        return switch (status) {
            case NOT_FOUND -> "Resource Not Found";
            case CONFLICT -> "Conflict";
            case FORBIDDEN -> "Forbidden";
            case UNAUTHORIZED -> "Unauthorized";
            case BAD_REQUEST -> "Bad Request";
            default -> status.getReasonPhrase();
        };
    }
}
