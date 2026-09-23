package com.frontrow.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.frontrow.support.IntegrationTestBase;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
        "frontrow.payment.mode=DECLINE",
        "frontrow.payment.latency-ms=0",
        "frontrow.hold-ttl=PT2M"
})
class PaymentDeclineTest extends IntegrationTestBase {

    @Test
    void aDeclineReleasesTheSeatOnce() throws Exception {
        HttpResult held = postBooking("ada", SEAT_A1, "decline-key-1");
        assertThat(held.status()).isEqualTo(202);

        await().atMost(Duration.ofSeconds(15)).until(() -> "AVAILABLE".equals(inventoryStatus(SEAT_A1))
                && countBookings(SEAT_A1, "CANCELLED") == 1
                && countPayments("DECLINED") == 1);

        long charges = gateway.chargeCount();
        HttpResult replay = postBooking("ada", SEAT_A1, "decline-key-1");
        assertThat(replay.status()).isEqualTo(200);
        assertThat(replay.bookingId()).isEqualTo(held.bookingId());
        assertThat(replay.body()).contains("CANCELLED").contains("DECLINED");
        assertThat(countPayments("DECLINED")).isEqualTo(1);
        assertThat(gateway.chargeCount()).isEqualTo(charges);
    }
}
