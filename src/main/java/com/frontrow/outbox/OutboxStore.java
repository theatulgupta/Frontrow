package com.frontrow.outbox;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OutboxStore {

    private final JdbcTemplate jdbc;

    public OutboxStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(UUID id, UUID aggregateId, String payload) {
        jdbc.update(
                """
                INSERT INTO outbox_messages (id, event_type, aggregate_id, payload, status)
                VALUES (?, ?, ?, ?::jsonb, 'PENDING')
                """,
                id,
                PaymentRequestedEvent.EVENT_TYPE,
                aggregateId,
                payload);
    }

    public List<OutboxMessage> claimPending(int limit) {
        return jdbc.query(
                """
                WITH claimed AS (
                    SELECT id
                    FROM outbox_messages
                    WHERE status = 'PENDING'
                    ORDER BY created_at
                    FOR UPDATE SKIP LOCKED
                    LIMIT ?
                )
                UPDATE outbox_messages AS message
                SET status = 'PUBLISHING', publishing_started_at = now()
                FROM claimed
                WHERE message.id = claimed.id
                RETURNING message.id, message.event_type, message.aggregate_id, message.payload::text
                """,
                (rs, row) -> new OutboxMessage(
                        rs.getObject("id", UUID.class),
                        rs.getString("event_type"),
                        rs.getObject("aggregate_id", UUID.class),
                        rs.getString("payload")),
                limit);
    }

    public void markPublished(UUID id) {
        jdbc.update(
                """
                UPDATE outbox_messages
                SET status = 'PUBLISHED', published_at = now()
                WHERE id = ? AND status = 'PUBLISHING'
                """,
                id);
    }

    public int countPending() {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM outbox_messages WHERE status = 'PENDING'",
                Integer.class);
        return count == null ? 0 : count;
    }

    public int purgePublished() {
        return jdbc.update(
                """
                DELETE FROM outbox_messages
                WHERE status = 'PUBLISHED'
                  AND published_at < now() - interval '7 days'
                """);
    }

    public int sweepStuck() {
        return jdbc.update(
                """
                UPDATE outbox_messages
                SET status = 'PENDING', publishing_started_at = NULL
                WHERE status = 'PUBLISHING'
                  AND publishing_started_at < now() - interval '30 seconds'
                """);
    }
}
