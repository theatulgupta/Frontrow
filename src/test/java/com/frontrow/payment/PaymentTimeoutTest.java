package com.frontrow.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.frontrow.support.IntegrationTestBase;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
        "frontrow.payment.mode=TIMEOUT",
        "frontrow.payment.latency-ms=0",
        "frontrow.payment.timeout-ms=30",
        "frontrow.payment.max-attempts=3",
        "frontrow.hold-ttl=PT2M"
})
class PaymentTimeoutTest extends IntegrationTestBase {

    @Test
    void repeatedTimeoutsReleaseTheHold() throws Exception {
        HttpResult held = postBooking("ada", SEAT_A1, "timeout-key-1");
        assertThat(held.status()).isEqualTo(202);

        await().atMost(Duration.ofSeconds(30)).until(() -> "AVAILABLE".equals(inventoryStatus(SEAT_A1))
                && countBookings(SEAT_A1, "CANCELLED") == 1
                && countPayments("TIMED_OUT") == 1);
        assertThat(gateway.chargeCount()).isZero();
    }
}
