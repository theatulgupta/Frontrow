package com.frontrow.payment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PaymentRepository {

    private final JdbcTemplate jdbc;

    public PaymentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<PaymentRecord> findByBookingId(UUID bookingId) {
        List<PaymentRecord> rows = jdbc.query(
                """
                SELECT id, booking_id, status, attempt_count, gateway_reference
                FROM payments
                WHERE booking_id = ?
                """,
                (rs, row) -> new PaymentRecord(
                        rs.getObject("id", UUID.class),
                        rs.getObject("booking_id", UUID.class),
                        PaymentStatus.valueOf(rs.getString("status")),
                        rs.getInt("attempt_count"),
                        rs.getString("gateway_reference")),
                bookingId);
        return rows.stream().findFirst();
    }

    public PaymentRecord insertPending(UUID bookingId) {
        try {
            jdbc.update(
                    """
                    INSERT INTO payments (id, booking_id, status, attempt_count)
                    VALUES (?, ?, 'PENDING', 0)
                    """,
                    UUID.randomUUID(),
                    bookingId);
        } catch (DataIntegrityViolationException exception) {
            return findByBookingId(bookingId).orElseThrow(() -> exception);
        }
        return findByBookingId(bookingId).orElseThrow();
    }

    public int markSucceeded(UUID bookingId, String gatewayReference) {
        return updateStatus(bookingId, "SUCCEEDED", gatewayReference);
    }

    public int markDeclined(UUID bookingId, String gatewayReference) {
        return updateStatus(bookingId, "DECLINED", gatewayReference);
    }

    public int markTimedOut(UUID bookingId) {
        return updateStatus(bookingId, "TIMED_OUT", null);
    }

    public int markVoided(UUID bookingId) {
        return updateStatus(bookingId, "VOIDED", null);
    }

    public int incrementAttempt(UUID bookingId) {
        List<Integer> counts = jdbc.query(
                """
                UPDATE payments
                SET attempt_count = attempt_count + 1, updated_at = now()
                WHERE booking_id = ? AND status = 'PENDING'
                RETURNING attempt_count
                """,
                (rs, row) -> rs.getInt(1),
                bookingId);
        if (!counts.isEmpty()) {
            return counts.get(0);
        }
        return findByBookingId(bookingId).map(PaymentRecord::attemptCount).orElse(0);
    }

    private int updateStatus(UUID bookingId, String status, String gatewayReference) {
        return jdbc.update(
                """
                UPDATE payments
                SET status = ?, gateway_reference = COALESCE(?, gateway_reference), updated_at = now()
                WHERE booking_id = ? AND status = 'PENDING'
                """,
                status,
                gatewayReference,
                bookingId);
    }
}
