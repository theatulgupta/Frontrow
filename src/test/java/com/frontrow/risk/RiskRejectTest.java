package com.frontrow.risk;

import static org.assertj.core.api.Assertions.assertThat;

import com.frontrow.inventory.SeatLockKey;
import com.frontrow.support.IntegrationTestBase;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "frontrow.payment.consumer-enabled=false")
class RiskRejectTest extends IntegrationTestBase {

    @Test
    void tooManyAttemptsRejectWithoutTakingTheSeat() throws Exception {
        for (int i = 0; i < 20; i++) {
            insertAttempt("risk-user", SEAT_A1, "seed-user-" + i + "-key");
        }

        HttpResult result = postBooking("risk-user", SEAT_A1, "reject-attempt-key");
        assertThat(result.status()).isEqualTo(403);
        assertThat(result.code()).isEqualTo("RISK_REJECT");
        assertThat(result.body()).contains("USER_ATTEMPT_RATE");
        assertThat(inventoryStatus(SEAT_A1)).isEqualTo("AVAILABLE");
        assertThat(countBookings(SEAT_A1, "PENDING_PAYMENT")).isZero();
        assertThat(redisson.getLock(SeatLockKey.of(SHOW_ID, SEAT_A1)).isLocked()).isFalse();
    }

    private void insertAttempt(String userId, UUID seatId, String key) {
        jdbc.update(
                """
                INSERT INTO booking_attempts (
                    id, user_id, show_id, seat_id, idempotency_key, decision, reason, flagged,
                    user_attempt_count, seat_attempt_count, distinct_seat_count, in_flight_holds
                ) VALUES (?, ?, ?, ?, ?, 'ALLOW', 'OK', false, 0, 0, 0, 0)
                """,
                UUID.randomUUID(),
                userId,
                SHOW_ID,
                seatId,
                key);
    }
}
