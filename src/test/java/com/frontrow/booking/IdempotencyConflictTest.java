package com.frontrow.booking;

import static org.assertj.core.api.Assertions.assertThat;

import com.frontrow.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "frontrow.payment.consumer-enabled=false")
class IdempotencyConflictTest extends IntegrationTestBase {

    @Test
    void theSameKeyCannotHoldASecondSeat() throws Exception {
        HttpResult first = postBooking("ada", SEAT_A1, "conflict-key-1");
        HttpResult second = postBooking("ada", SEAT_A2, "conflict-key-1");

        assertThat(first.status()).isEqualTo(202);
        assertThat(second.status()).isEqualTo(409);
        assertThat(second.code()).isEqualTo("IDEMPOTENCY_CONFLICT");
        assertThat(inventoryStatus(SEAT_A1)).isEqualTo("HELD");
        assertThat(inventoryStatus(SEAT_A2)).isEqualTo("AVAILABLE");
        assertThat(countBookings(SEAT_A1, "PENDING_PAYMENT")).isEqualTo(1);
        assertThat(countBookings(SEAT_A2, "PENDING_PAYMENT")).isZero();
    }
}
