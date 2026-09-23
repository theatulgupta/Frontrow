package com.frontrow.expiry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.frontrow.support.IntegrationTestBase;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
        "frontrow.hold-ttl=PT1S",
        "frontrow.payment.consumer-enabled=false"
})
class HoldExpiryTest extends IntegrationTestBase {

    @Test
    void anUnpaidHoldReturnsToAvailableExactlyOnce() throws Exception {
        HttpResult held = postBooking("ada", SEAT_A1, "expiry-key-01");
        assertThat(held.status()).isEqualTo(202);

        await().atMost(Duration.ofSeconds(10)).until(() -> "AVAILABLE".equals(inventoryStatus(SEAT_A1))
                && countBookings(SEAT_A1, "EXPIRED") == 1);

        HttpResult expired = getBooking("ada", held.bookingId());
        assertThat(expired.status()).isEqualTo(410);
        assertThat(expired.code()).isEqualTo("HOLD_EXPIRED");

        HttpResult replay = postBooking("ada", SEAT_A1, "expiry-key-01");
        assertThat(replay.status()).isEqualTo(410);
        assertThat(replay.code()).isEqualTo("HOLD_EXPIRED");
        assertThat(inventoryStatus(SEAT_A1)).isEqualTo("AVAILABLE");

        HttpResult next = postBooking("grace", SEAT_A1, "expiry-key-02");
        assertThat(next.status()).isEqualTo(202);
        assertThat(inventoryStatus(SEAT_A1)).isEqualTo("HELD");
        assertThat(countBookings(SEAT_A1, "EXPIRED")).isEqualTo(1);
        assertThat(countBookings(SEAT_A1, "PENDING_PAYMENT")).isEqualTo(1);
    }
}
