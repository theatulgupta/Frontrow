package com.frontrow.risk;

import static org.assertj.core.api.Assertions.assertThat;

import com.frontrow.inventory.SeatLockKey;
import com.frontrow.support.IntegrationTestBase;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "frontrow.payment.consumer-enabled=false")
class RiskChallengeTest extends IntegrationTestBase {

    @Test
    void sprayingSeatsChallengesWithoutAHold() throws Exception {
        List<UUID> seats = jdbc.query(
                "SELECT id FROM seats ORDER BY row_label, seat_number LIMIT 6",
                (rs, row) -> rs.getObject(1, UUID.class));
        assertThat(seats).hasSize(6);
        for (int i = 0; i < seats.size(); i++) {
            jdbc.update(
                    """
                    INSERT INTO booking_attempts (
                        id, user_id, show_id, seat_id, idempotency_key, decision, reason, flagged,
                        user_attempt_count, seat_attempt_count, distinct_seat_count, in_flight_holds
                    ) VALUES (?, ?, ?, ?, ?, 'ALLOW', 'OK', false, 0, 0, 0, 0)
                    """,
                    UUID.randomUUID(),
                    "spray-user",
                    SHOW_ID,
                    seats.get(i),
                    "spray-seed-" + i + "-key");
        }

        HttpResult result = postBooking("spray-user", SEAT_A1, "challenge-key-1");
        assertThat(result.status()).isEqualTo(403);
        assertThat(result.code()).isEqualTo("RISK_CHALLENGE");
        assertThat(result.body()).contains("SEAT_SPRAY");
        assertThat(inventoryStatus(SEAT_A1)).isEqualTo("AVAILABLE");
        assertThat(countBookings(SEAT_A1, "PENDING_PAYMENT")).isZero();
        assertThat(redisson.getLock(SeatLockKey.of(SHOW_ID, SEAT_A1)).isLocked()).isFalse();
    }
}
