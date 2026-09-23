package com.frontrow.booking;

import static org.assertj.core.api.Assertions.assertThat;

import com.frontrow.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "frontrow.payment.consumer-enabled=false")
class IdempotentBookingReplayTest extends IntegrationTestBase {

    @Test
    void theSameKeyReturnsTheSameHoldWithoutASecondAttempt() throws Exception {
        HttpResult first = postBooking("ada", SEAT_A1, "replay-key-1");
        HttpResult second = postBooking("ada", SEAT_A1, "replay-key-1");

        assertThat(first.status()).isEqualTo(202);
        assertThat(second.status()).isEqualTo(202);
        assertThat(second.bookingId()).isEqualTo(first.bookingId());
        assertThat(countBookings(SEAT_A1, "PENDING_PAYMENT")).isEqualTo(1);
        assertThat(countAttempts("ada")).isEqualTo(1);
        assertThat(countInventory(SEAT_A1, "HELD")).isEqualTo(1);
    }
}
