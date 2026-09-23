package com.frontrow.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handle(ApiException exception) {
        return ResponseEntity.status(exception.status()).body(ApiError.from(exception));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> invalid(MethodArgumentNotValidException exception) {
        return ResponseEntity.badRequest().body(new ApiError(ErrorCodes.VALIDATION, "Request body is invalid", null, null, null));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> unreadable(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest().body(new ApiError(ErrorCodes.VALIDATION, "Request body is invalid", null, null, null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception exception) {
        log.error("request_failed", exception);
        return ResponseEntity.internalServerError()
                .body(new ApiError("INTERNAL", "Unexpected error", null, null, null));
    }
}
