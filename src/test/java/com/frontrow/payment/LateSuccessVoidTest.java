package com.frontrow.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.frontrow.support.IntegrationTestBase;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
        "frontrow.hold-ttl=PT1S",
        "frontrow.payment.mode=SUCCESS",
        "frontrow.payment.latency-ms=3000"
})
class LateSuccessVoidTest extends IntegrationTestBase {

    @Test
    void successAfterTheHoldExpiresVoidsThePayment() throws Exception {
        HttpResult held = postBooking("ada", SEAT_A1, "late-success-key");
        assertThat(held.status()).isEqualTo(202);

        await().atMost(Duration.ofSeconds(20)).until(() ->
                "AVAILABLE".equals(inventoryStatus(SEAT_A1))
                        && countBookings(SEAT_A1, "EXPIRED") == 1
                        && countPayments("VOIDED") == 1);
    }
}
