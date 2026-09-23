package com.frontrow.payment;

import java.util.UUID;

public class PaymentTimeoutException extends RuntimeException {

    public PaymentTimeoutException(UUID bookingId) {
        super("Payment gateway timed out for booking " + bookingId);
    }
}
