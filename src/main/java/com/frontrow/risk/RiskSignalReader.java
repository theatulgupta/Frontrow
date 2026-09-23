package com.frontrow.risk;

import com.frontrow.config.FrontrowProperties;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
public class RiskSignalReader {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final FrontrowProperties properties;

    public RiskSignalReader(JdbcTemplate jdbc, TransactionTemplate transactions, FrontrowProperties properties) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.properties = properties;
    }

    public RiskSignals read(String userId, UUID showId, UUID seatId) {
        long windowMs = properties.getRisk().getWindow().toMillis();
        return transactions.execute(status -> {
            jdbc.execute("SET LOCAL statement_timeout = '100ms'");
            int userAttempts = count(
                    """
                    SELECT count(*) FROM booking_attempts
                    WHERE user_id = ? AND created_at >= now() - (? * interval '1 millisecond')
                    """,
                    userId,
                    windowMs);
            int seatAttempts = count(
                    """
                    SELECT count(*) FROM booking_attempts
                    WHERE show_id = ? AND seat_id = ? AND created_at >= now() - (? * interval '1 millisecond')
                    """,
                    showId,
                    seatId,
                    windowMs);
            int distinctSeats = count(
                    """
                    SELECT count(DISTINCT seat_id) FROM booking_attempts
                    WHERE user_id = ? AND created_at >= now() - (? * interval '1 millisecond')
                    """,
                    userId,
                    windowMs);
            int inFlight = count(
                    """
                    SELECT count(*) FROM bookings
                    WHERE user_id = ? AND status = 'PENDING_PAYMENT'
                    """,
                    userId);
            return new RiskSignals(userAttempts, seatAttempts, distinctSeats, inFlight);
        });
    }

    private int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }
}
