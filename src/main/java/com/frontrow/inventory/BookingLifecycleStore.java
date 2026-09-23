package com.frontrow.inventory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class BookingLifecycleStore {

    private final JdbcTemplate jdbc;

    public BookingLifecycleStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<ManagedBooking> find(UUID bookingId) {
        List<ManagedBooking> rows = jdbc.query(
                """
                SELECT id, show_id, seat_id, amount_cents
                FROM bookings
                WHERE id = ?
                """,
                (rs, row) -> new ManagedBooking(
                        rs.getObject("id", UUID.class),
                        rs.getObject("show_id", UUID.class),
                        rs.getObject("seat_id", UUID.class),
                        rs.getInt("amount_cents")),
                bookingId);
        return rows.stream().findFirst();
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

    public record ManagedBooking(UUID id, UUID showId, UUID seatId, int amountCents) {
    }
}
