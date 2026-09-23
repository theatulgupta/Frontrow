package com.frontrow.payment;

import java.util.UUID;

public record PaymentRecord(UUID id, UUID bookingId, PaymentStatus status, int attemptCount, String gatewayReference) {
}
