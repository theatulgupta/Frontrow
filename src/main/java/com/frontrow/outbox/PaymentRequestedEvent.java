package com.frontrow.outbox;

import java.time.Instant;
import java.util.UUID;

public record PaymentRequestedEvent(
        UUID eventId,
        String eventType,
        UUID bookingId,
        UUID showId,
        UUID seatId,
        String userId,
        int amountCents,
        Instant holdExpiresAt) {

    public static final String EVENT_TYPE = "booking.payment-requested";
}
