package com.frontrow.booking;

import java.time.Instant;
import java.util.UUID;

public record BookingRecord(
        UUID id,
        UUID showId,
        UUID seatId,
        String userId,
        String idempotencyKey,
        BookingStatus status,
        int amountCents,
        Instant holdExpiresAt,
        String riskDecision,
        String riskReason,
        boolean riskFlagged,
        String paymentStatus) {
}
