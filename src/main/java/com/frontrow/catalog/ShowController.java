package com.frontrow.catalog;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/shows")
public class ShowController {

    private final CatalogRepository catalog;
    private final JdbcTemplate jdbc;

    public ShowController(CatalogRepository catalog, JdbcTemplate jdbc) {
        this.catalog = catalog;
        this.jdbc = jdbc;
    }

    @GetMapping
    public List<ShowResponse> list() {
        return catalog.listShows();
    }

    @GetMapping("/{showId}")
    public ShowResponse get(@PathVariable UUID showId) {
        return catalog.requireShow(showId);
    }

    @GetMapping("/{showId}/seats")
    public List<SeatResponse> seats(@PathVariable UUID showId) {
        catalog.requireShow(showId);
        return jdbc.query(
                """
                SELECT st.id, st.section, st.row_label, st.seat_number, st.front_row, i.status
                FROM seats st
                JOIN seat_inventory i ON i.seat_id = st.id
                WHERE i.show_id = ?
                ORDER BY st.row_label, st.seat_number
                """,
                (rs, row) -> new SeatResponse(
                        rs.getObject("id", UUID.class),
                        rs.getString("section"),
                        rs.getString("row_label"),
                        rs.getInt("seat_number"),
                        rs.getBoolean("front_row"),
                        rs.getString("status")),
                showId);
    }
}
