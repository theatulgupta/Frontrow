package com.frontrow.config;

import java.util.UUID;

public record ApiError(String code, String message, UUID showId, UUID seatId, UUID bookingId) {

    public static ApiError from(ApiException exception) {
        return new ApiError(
                exception.code(),
                exception.getMessage(),
                exception.showId(),
                exception.seatId(),
                exception.bookingId());
    }
}
