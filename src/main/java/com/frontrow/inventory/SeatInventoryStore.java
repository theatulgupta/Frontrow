package com.frontrow.inventory;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SeatInventoryStore {

    private final JdbcTemplate jdbc;

    public SeatInventoryStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public int holdIfAvailable(UUID showId, UUID seatId, UUID bookingId, Instant holdExpiresAt) {
        return jdbc.update(
                """
                UPDATE seat_inventory
                SET status = 'HELD', booking_id = ?, hold_expires_at = ?, updated_at = now()
                WHERE show_id = ? AND seat_id = ? AND status = 'AVAILABLE'
                """,
                bookingId,
                Timestamp.from(holdExpiresAt),
                showId,
                seatId);
    }

    public int markSoldIfOwner(UUID showId, UUID seatId, UUID bookingId) {
        return jdbc.update(
                """
                UPDATE seat_inventory
                SET status = 'SOLD', hold_expires_at = NULL, updated_at = now()
                WHERE show_id = ? AND seat_id = ? AND status = 'HELD' AND booking_id = ?
                  AND hold_expires_at > now()
                """,
                showId,
                seatId,
                bookingId);
    }

    public int releaseIfOwner(UUID showId, UUID seatId, UUID bookingId) {
        return jdbc.update(
                """
                UPDATE seat_inventory
                SET status = 'AVAILABLE', booking_id = NULL, hold_expires_at = NULL, updated_at = now()
                WHERE show_id = ? AND seat_id = ? AND status = 'HELD' AND booking_id = ?
                """,
                showId,
                seatId,
                bookingId);
    }

    public int releaseIfExpiredOwner(UUID showId, UUID seatId, UUID bookingId) {
        return jdbc.update(
                """
                UPDATE seat_inventory
                SET status = 'AVAILABLE', booking_id = NULL, hold_expires_at = NULL, updated_at = now()
                WHERE show_id = ? AND seat_id = ? AND status = 'HELD' AND booking_id = ?
                  AND hold_expires_at <= now()
                """,
                showId,
                seatId,
                bookingId);
    }

    public List<HeldInventory> lockExpired(int limit) {
        return jdbc.query(
                """
                SELECT show_id, seat_id, booking_id
                FROM seat_inventory
                WHERE status = 'HELD' AND hold_expires_at <= now()
                ORDER BY hold_expires_at
                FOR UPDATE SKIP LOCKED
                LIMIT ?
                """,
                (rs, row) -> new HeldInventory(
                        rs.getObject("show_id", UUID.class),
                        rs.getObject("seat_id", UUID.class),
                        rs.getObject("booking_id", UUID.class)),
                limit);
    }
}
