package com.frontrow.config;

public final class ErrorCodes {

    public static final String SEAT_UNAVAILABLE = "SEAT_UNAVAILABLE";
    public static final String IDEMPOTENCY_CONFLICT = "IDEMPOTENCY_CONFLICT";
    public static final String LOCK_TIMEOUT = "LOCK_TIMEOUT";
    public static final String LOCK_UNAVAILABLE = "LOCK_UNAVAILABLE";
    public static final String RISK_REJECT = "RISK_REJECT";
    public static final String RISK_CHALLENGE = "RISK_CHALLENGE";
    public static final String HOLD_EXPIRED = "HOLD_EXPIRED";
    public static final String VALIDATION = "VALIDATION";
    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String UNAUTHORIZED = "UNAUTHORIZED";

    private ErrorCodes() {
    }
}
