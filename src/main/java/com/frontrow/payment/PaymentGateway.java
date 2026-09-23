package com.frontrow.payment;

import java.util.UUID;

public interface PaymentGateway {

    PaymentResult charge(UUID bookingId, int amountCents);
}
