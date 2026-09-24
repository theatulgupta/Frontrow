package com.frontrow.catalog;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/patrons")
public class PatronController {

    private final JdbcTemplate jdbc;

    public PatronController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    public List<PatronResponse> list() {
        return jdbc.query(
                """
                SELECT user_id, display_name, city
                FROM patrons
                ORDER BY display_name
                """,
                (rs, row) -> new PatronResponse(
                        rs.getString("user_id"),
                        rs.getString("display_name"),
                        rs.getString("city")));
    }
}
