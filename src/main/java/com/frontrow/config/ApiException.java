package com.frontrow.config;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final UUID showId;
    private final UUID seatId;
    private final UUID bookingId;

    public ApiException(HttpStatus status, String code, String message, UUID showId, UUID seatId, UUID bookingId) {
        super(message);
        this.status = status;
        this.code = code;
        this.showId = showId;
        this.seatId = seatId;
        this.bookingId = bookingId;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public UUID showId() {
        return showId;
    }

    public UUID seatId() {
        return seatId;
    }

    public UUID bookingId() {
        return bookingId;
    }

    public static ApiException validation(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION, message, null, null, null);
    }

    public static ApiException unauthorized() {
        return new ApiException(HttpStatus.UNAUTHORIZED, ErrorCodes.UNAUTHORIZED, "Unauthorized", null, null, null);
    }

    public static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, ErrorCodes.NOT_FOUND, message, null, null, null);
    }

    public static ApiException fareFull() {
        return new ApiException(HttpStatus.CONFLICT, "FARE_UNAVAILABLE", "This fare is full", null, null, null);
    }

    public static ApiException seatUnavailable(UUID showId, UUID seatId) {
        return new ApiException(HttpStatus.CONFLICT, ErrorCodes.SEAT_UNAVAILABLE, "Seat is not available", showId, seatId, null);
    }

    public static ApiException idempotencyConflict(UUID showId, UUID seatId) {
        return new ApiException(
                HttpStatus.CONFLICT,
                ErrorCodes.IDEMPOTENCY_CONFLICT,
                "Idempotency key was already used for a different seat",
                showId,
                seatId,
                null);
    }

    public static ApiException lockTimeout(UUID showId, UUID seatId) {
        return new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                ErrorCodes.LOCK_TIMEOUT,
                "Timed out waiting for the seat lock",
                showId,
                seatId,
                null);
    }

    public static ApiException lockUnavailable(UUID showId, UUID seatId) {
        return new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                ErrorCodes.LOCK_UNAVAILABLE,
                "Seat lock is unavailable",
                showId,
                seatId,
                null);
    }

    public static ApiException risk(String code, String reason, UUID showId, UUID seatId) {
        return new ApiException(HttpStatus.FORBIDDEN, code, reason, showId, seatId, null);
    }

    public static ApiException holdExpired(UUID bookingId) {
        return new ApiException(HttpStatus.GONE, ErrorCodes.HOLD_EXPIRED, "Hold expired", null, null, bookingId);
    }
}
