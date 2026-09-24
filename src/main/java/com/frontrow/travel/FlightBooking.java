package com.frontrow.travel;

import java.time.Instant;
import java.util.UUID;

public record FlightBooking(
        UUID id,
        UUID offerId,
        String userId,
        String idempotencyKey,
        String status,
        int amountCents,
        Instant holdExpiresAt) {
}
