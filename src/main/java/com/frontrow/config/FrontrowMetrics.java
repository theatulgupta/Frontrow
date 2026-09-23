package com.frontrow.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class FrontrowMetrics {

    private final MeterRegistry registry;

    public FrontrowMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void lockWait(String result, Duration elapsed) {
        Timer.builder("frontrow.lock.wait")
                .tag("result", result)
                .register(registry)
                .record(elapsed);
    }

    public void conflict(String reason) {
        Counter.builder("frontrow.booking.conflicts")
                .tag("reason", reason)
                .register(registry)
                .increment();
    }

    public void holdExpired() {
        Counter.builder("frontrow.hold.expirations").register(registry).increment();
    }

    public void payment(String outcome) {
        Counter.builder("frontrow.payment.outcomes")
                .tag("outcome", outcome)
                .register(registry)
                .increment();
    }

    public void risk(String decision) {
        Counter.builder("frontrow.risk.decisions")
                .tag("decision", decision)
                .register(registry)
                .increment();
    }
}
