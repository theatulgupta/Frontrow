package com.frontrow.payment;

import java.util.UUID;

public record PaymentResult(boolean success, String gatewayReference) {
}
