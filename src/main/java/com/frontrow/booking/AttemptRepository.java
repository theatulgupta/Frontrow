package com.frontrow.booking;

import com.frontrow.risk.RiskAssessment;
import com.frontrow.risk.RiskSignals;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AttemptRepository {

    private final JdbcTemplate jdbc;

    public AttemptRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<StoredAttempt> findByUserAndKey(String userId, String idempotencyKey) {
        List<StoredAttempt> rows = jdbc.query(
                """
                SELECT decision, reason, show_id, seat_id
                FROM booking_attempts
                WHERE user_id = ? AND idempotency_key = ?
                """,
                (rs, row) -> new StoredAttempt(
                        rs.getString("decision"),
                        rs.getString("reason"),
                        rs.getObject("show_id", UUID.class),
                        rs.getObject("seat_id", UUID.class)),
                userId,
                idempotencyKey);
        return rows.stream().findFirst();
    }

    public void insert(
            UUID id,
            String userId,
            UUID showId,
            UUID seatId,
            String idempotencyKey,
            RiskAssessment assessment,
            RiskSignals signals) {
        jdbc.update(
                """
                INSERT INTO booking_attempts (
                    id, user_id, show_id, seat_id, idempotency_key, decision, reason, flagged,
                    user_attempt_count, seat_attempt_count, distinct_seat_count, in_flight_holds
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id,
                userId,
                showId,
                seatId,
                idempotencyKey,
                assessment.decision().name(),
                assessment.reason(),
                assessment.flagged(),
                signals.userAttempts(),
                signals.seatAttempts(),
                signals.distinctSeats(),
                signals.inFlightHolds());
    }

    public void insertIgnoringConflict(
            UUID id,
            String userId,
            UUID showId,
            UUID seatId,
            String idempotencyKey,
            RiskAssessment assessment,
            RiskSignals signals) {
        jdbc.update(
                """
                INSERT INTO booking_attempts (
                    id, user_id, show_id, seat_id, idempotency_key, decision, reason, flagged,
                    user_attempt_count, seat_attempt_count, distinct_seat_count, in_flight_holds
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (user_id, idempotency_key) DO NOTHING
                """,
                id,
                userId,
                showId,
                seatId,
                idempotencyKey,
                assessment.decision().name(),
                assessment.reason(),
                assessment.flagged(),
                signals.userAttempts(),
                signals.seatAttempts(),
                signals.distinctSeats(),
                signals.inFlightHolds());
    }

    public record StoredAttempt(String decision, String reason, UUID showId, UUID seatId) {
    }
}
