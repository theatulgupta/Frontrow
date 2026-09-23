package com.frontrow.booking;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class BookingRepository {

    private final JdbcTemplate jdbc;

    public BookingRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<BookingRecord> findByUserAndKey(String userId, String idempotencyKey) {
        return one(
                """
                SELECT b.*, p.status AS payment_status
                FROM bookings b
                LEFT JOIN payments p ON p.booking_id = b.id
                WHERE b.user_id = ? AND b.idempotency_key = ?
                """,
                userId,
                idempotencyKey);
    }

    public Optional<BookingRecord> findById(UUID bookingId) {
        return one(
                """
                SELECT b.*, p.status AS payment_status
                FROM bookings b
                LEFT JOIN payments p ON p.booking_id = b.id
                WHERE b.id = ?
                """,
                bookingId);
    }

    public boolean isPayable(UUID bookingId) {
        Boolean payable = jdbc.queryForObject(
                """
                SELECT status = 'PENDING_PAYMENT' AND hold_expires_at > now()
                FROM bookings
                WHERE id = ?
                """,
                Boolean.class,
                bookingId);
        return Boolean.TRUE.equals(payable);
    }

    public void insert(
            UUID id,
            UUID showId,
            UUID seatId,
            String userId,
            String idempotencyKey,
            int amountCents,
            Instant holdExpiresAt,
            String riskDecision,
            String riskReason,
            boolean riskFlagged) {
        jdbc.update(
                """
                INSERT INTO bookings (
                    id, show_id, seat_id, user_id, idempotency_key, status, amount_cents,
                    hold_expires_at, risk_decision, risk_reason, risk_flagged
                ) VALUES (?, ?, ?, ?, ?, 'PENDING_PAYMENT', ?, ?, ?, ?, ?)
                """,
                id,
                showId,
                seatId,
                userId,
                idempotencyKey,
                amountCents,
                Timestamp.from(holdExpiresAt),
                riskDecision,
                riskReason,
                riskFlagged);
    }

    public int markConfirmed(UUID bookingId) {
        return transition(bookingId, "CONFIRMED");
    }

    public int markCancelled(UUID bookingId) {
        return transition(bookingId, "CANCELLED");
    }

    public int markExpired(UUID bookingId) {
        return transition(bookingId, "EXPIRED");
    }

    private int transition(UUID bookingId, String status) {
        return jdbc.update(
                """
                UPDATE bookings
                SET status = ?, updated_at = now()
                WHERE id = ? AND status = 'PENDING_PAYMENT'
                """,
                status,
                bookingId);
    }

    private Optional<BookingRecord> one(String sql, Object... args) {
        List<BookingRecord> rows = jdbc.query(sql, (rs, row) -> map(rs), args);
        return rows.stream().findFirst();
    }

    private BookingRecord map(ResultSet rs) throws SQLException {
        return new BookingRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("show_id", UUID.class),
                rs.getObject("seat_id", UUID.class),
                rs.getString("user_id"),
                rs.getString("idempotency_key"),
                BookingStatus.valueOf(rs.getString("status")),
                rs.getInt("amount_cents"),
                rs.getTimestamp("hold_expires_at").toInstant(),
                rs.getString("risk_decision"),
                rs.getString("risk_reason"),
                rs.getBoolean("risk_flagged"),
                rs.getString("payment_status"));
    }
}
