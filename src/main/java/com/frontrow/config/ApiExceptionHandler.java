package com.frontrow.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handle(ApiException exception) {
        return ResponseEntity.status(exception.status()).body(ApiError.from(exception));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> typeMismatch(MethodArgumentTypeMismatchException exception) {
        return validation("Request is invalid");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception exception) {
        log.error("request_failed", exception);
        return ResponseEntity.internalServerError()
                .body(new ApiError("INTERNAL", "Unexpected error", null, null, null));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        return validationEntity("Request body is invalid");
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        return validationEntity("Request body is invalid");
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception exception,
            Object body,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        if (body instanceof ApiError) {
            return new ResponseEntity<>(body, headers, status);
        }
        String code = status.value() == 404 ? ErrorCodes.NOT_FOUND : ErrorCodes.VALIDATION;
        String message = status.value() == 404 ? "Not found" : "Request is invalid";
        if (status.is5xxServerError()) {
            log.error("request_failed", exception);
            code = "INTERNAL";
            message = "Unexpected error";
        }
        return new ResponseEntity<>(new ApiError(code, message, null, null, null), headers, status);
    }

    private static ResponseEntity<ApiError> validation(String message) {
        return ResponseEntity.badRequest().body(new ApiError(ErrorCodes.VALIDATION, message, null, null, null));
    }

    private static ResponseEntity<Object> validationEntity(String message) {
        return ResponseEntity.badRequest().body(new ApiError(ErrorCodes.VALIDATION, message, null, null, null));
    }
}
