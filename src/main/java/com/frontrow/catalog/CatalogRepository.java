package com.frontrow.catalog;

import com.frontrow.config.ApiException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CatalogRepository {

    private final JdbcTemplate jdbc;

    public CatalogRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<ShowResponse> listShows() {
        return jdbc.query(
                """
                SELECT s.id, s.name, s.starts_at, s.price_cents, v.name AS venue_name
                FROM shows s
                JOIN venues v ON v.id = s.venue_id
                ORDER BY s.starts_at
                """,
                (rs, row) -> new ShowResponse(
                        rs.getObject("id", UUID.class),
                        rs.getString("name"),
                        rs.getTimestamp("starts_at").toInstant(),
                        rs.getInt("price_cents"),
                        rs.getString("venue_name")));
    }

    public Optional<ShowResponse> findShow(UUID showId) {
        List<ShowResponse> rows = jdbc.query(
                """
                SELECT s.id, s.name, s.starts_at, s.price_cents, v.name AS venue_name
                FROM shows s
                JOIN venues v ON v.id = s.venue_id
                WHERE s.id = ?
                """,
                (rs, row) -> new ShowResponse(
                        rs.getObject("id", UUID.class),
                        rs.getString("name"),
                        rs.getTimestamp("starts_at").toInstant(),
                        rs.getInt("price_cents"),
                        rs.getString("venue_name")),
                showId);
        return rows.stream().findFirst();
    }

    public ShowResponse requireShow(UUID showId) {
        return findShow(showId).orElseThrow(() -> ApiException.notFound("Show not found"));
    }

    public boolean seatExists(UUID showId, UUID seatId) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM seat_inventory WHERE show_id = ? AND seat_id = ?",
                Integer.class,
                showId,
                seatId);
        return count != null && count == 1;
    }
}
