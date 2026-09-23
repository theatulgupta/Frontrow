package com.frontrow.payment;

import com.frontrow.config.FrontrowProperties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

@Component
public class SimulatedPaymentGateway implements PaymentGateway {

    private final FrontrowProperties properties;
    private final ConcurrentHashMap<UUID, PaymentResult> results = new ConcurrentHashMap<>();
    private final AtomicLong charges = new AtomicLong();

    public SimulatedPaymentGateway(FrontrowProperties properties) {
        this.properties = properties;
    }

    @Override
    public PaymentResult charge(UUID bookingId, int amountCents) {
        FrontrowProperties.Payment.Mode mode = properties.getPayment().getMode();
        if (mode == FrontrowProperties.Payment.Mode.TIMEOUT) {
            sleep(properties.getPayment().getLatencyMs());
            sleep(properties.getPayment().getTimeoutMs());
            throw new PaymentTimeoutException(bookingId);
        }
        return results.computeIfAbsent(bookingId, id -> {
            sleep(properties.getPayment().getLatencyMs());
            charges.incrementAndGet();
            return new PaymentResult(mode == FrontrowProperties.Payment.Mode.SUCCESS, "sim-" + id);
        });
    }

    public long chargeCount() {
        return charges.get();
    }

    private static void sleep(int millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Payment simulator interrupted", exception);
        }
    }
}
