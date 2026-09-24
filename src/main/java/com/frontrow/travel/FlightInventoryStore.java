package com.frontrow.travel;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class FlightInventoryStore {

    private final JdbcTemplate jdbc;

    public FlightInventoryStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<FlightOffer> search(String from, String to, LocalDate date) {
        return jdbc.query(
                """
                SELECT * FROM flight_offers
                WHERE origin_code = ? AND destination_code = ? AND depart_at::date = ?
                ORDER BY depart_at, price_cents
                """,
                (rs, row) -> mapOffer(rs),
                from,
                to,
                date);
    }

    public Optional<FlightOffer> findOffer(UUID id) {
        List<FlightOffer> rows = jdbc.query("SELECT * FROM flight_offers WHERE id = ?", (rs, row) -> mapOffer(rs), id);
        return rows.stream().findFirst();
    }

    public Optional<FlightBooking> findBooking(UUID id) {
        List<FlightBooking> rows = jdbc.query("SELECT * FROM flight_bookings WHERE id = ?", (rs, row) -> mapBooking(rs), id);
        return rows.stream().findFirst();
    }

    public Optional<FlightBooking> findByUserAndKey(String userId, String idempotencyKey) {
        List<FlightBooking> rows = jdbc.query(
                "SELECT * FROM flight_bookings WHERE user_id = ? AND idempotency_key = ?",
                (rs, row) -> mapBooking(rs),
                userId,
                idempotencyKey);
        return rows.stream().findFirst();
    }

    public int holdOneSeat(UUID offerId) {
        return jdbc.update(
                """
                UPDATE flight_offers
                SET seats_held = seats_held + 1
                WHERE id = ? AND (capacity - seats_held - seats_sold) >= 1
                """,
                offerId);
    }

    public void insertBooking(UUID id, UUID offerId, String userId, String idempotencyKey, int amountCents, Instant holdExpiresAt) {
        jdbc.update(
                """
                INSERT INTO flight_bookings (
                    id, offer_id, user_id, idempotency_key, status, amount_cents, hold_expires_at
                ) VALUES (?, ?, ?, ?, 'PENDING_PAYMENT', ?, ?)
                """,
                id,
                offerId,
                userId,
                idempotencyKey,
                amountCents,
                Timestamp.from(holdExpiresAt));
    }

    public boolean isPayable(UUID bookingId) {
        Boolean payable = jdbc.queryForObject(
                """
                SELECT status = 'PENDING_PAYMENT' AND hold_expires_at > now()
                FROM flight_bookings WHERE id = ?
                """,
                Boolean.class,
                bookingId);
        return Boolean.TRUE.equals(payable);
    }

    public int confirmAndSell(UUID bookingId) {
        return jdbc.update(
                """
                WITH owned AS (
                    SELECT id, offer_id FROM flight_bookings
                    WHERE id = ? AND status = 'PENDING_PAYMENT' AND hold_expires_at > now()
                    FOR UPDATE
                ),
                sold AS (
                    UPDATE flight_offers AS offer
                    SET seats_held = offer.seats_held - 1, seats_sold = offer.seats_sold + 1
                    FROM owned
                    WHERE offer.id = owned.offer_id AND offer.seats_held > 0
                    RETURNING owned.id
                )
                UPDATE flight_bookings AS booking
                SET status = 'CONFIRMED', updated_at = now()
                FROM sold
                WHERE booking.id = sold.id
                """,
                bookingId);
    }

    public int cancelAndRelease(UUID bookingId) {
        return release(bookingId, "CANCELLED", false);
    }

    public int expireDueHolds() {
        List<UUID> ids = jdbc.query(
                """
                SELECT id FROM flight_bookings
                WHERE status = 'PENDING_PAYMENT' AND hold_expires_at <= now()
                ORDER BY hold_expires_at
                FOR UPDATE SKIP LOCKED
                LIMIT 50
                """,
                (rs, row) -> rs.getObject("id", UUID.class));
        int released = 0;
        for (UUID id : ids) {
            released += release(id, "EXPIRED", true);
        }
        return released;
    }

    private int release(UUID bookingId, String status, boolean onlyIfExpired) {
        String expiryClause = onlyIfExpired ? " AND hold_expires_at <= now()" : "";
        return jdbc.update(
                """
                WITH owned AS (
                    SELECT id, offer_id FROM flight_bookings
                    WHERE id = ? AND status = 'PENDING_PAYMENT'
                """
                        + expiryClause
                        + """
                    FOR UPDATE
                ),
                released AS (
                    UPDATE flight_offers AS offer
                    SET seats_held = offer.seats_held - 1
                    FROM owned
                    WHERE offer.id = owned.offer_id AND offer.seats_held > 0
                    RETURNING owned.id
                )
                UPDATE flight_bookings AS booking
                SET status = ?, updated_at = now()
                FROM released
                WHERE booking.id = released.id
                """,
                bookingId,
                status);
    }

    private FlightOffer mapOffer(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new FlightOffer(
                rs.getObject("id", UUID.class),
                rs.getString("airline"),
                rs.getString("flight_number"),
                rs.getString("origin_code"),
                rs.getString("destination_code"),
                rs.getTimestamp("depart_at").toInstant(),
                rs.getTimestamp("arrive_at").toInstant(),
                rs.getInt("stops"),
                rs.getString("cabin"),
                rs.getInt("price_cents"),
                rs.getInt("capacity"),
                rs.getInt("seats_held"),
                rs.getInt("seats_sold"));
    }

    private FlightBooking mapBooking(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new FlightBooking(
                rs.getObject("id", UUID.class),
                rs.getObject("offer_id", UUID.class),
                rs.getString("user_id"),
                rs.getString("idempotency_key"),
                rs.getString("status"),
                rs.getInt("amount_cents"),
                rs.getTimestamp("hold_expires_at").toInstant());
    }
}
