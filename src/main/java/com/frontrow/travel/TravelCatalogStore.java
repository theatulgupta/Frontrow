package com.frontrow.travel;

import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TravelCatalogStore {

    private final JdbcTemplate jdbc;

    public TravelCatalogStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<TripResult> trains(String from, String to, LocalDate date) {
        return jdbc.query(
                """
                SELECT id, train_number, name, origin_code, destination_code, depart_at, arrive_at, class_name, price_cents
                FROM train_trips
                WHERE origin_code = ? AND destination_code = ? AND depart_at::date = ?
                ORDER BY depart_at
                """,
                (rs, row) -> new TripResult(
                        rs.getObject("id", java.util.UUID.class).toString(),
                        rs.getString("name"),
                        rs.getString("train_number"),
                        rs.getString("origin_code"),
                        rs.getString("destination_code"),
                        rs.getTimestamp("depart_at").toInstant(),
                        rs.getTimestamp("arrive_at").toInstant(),
                        rs.getString("class_name"),
                        rs.getInt("price_cents")),
                from,
                to,
                date);
    }

    public List<TripResult> buses(String from, String to, LocalDate date) {
        return jdbc.query(
                """
                SELECT id, operator_name, coach, origin_code, destination_code, depart_at, arrive_at, price_cents
                FROM bus_trips
                WHERE origin_code = ? AND destination_code = ? AND depart_at::date = ?
                ORDER BY depart_at
                """,
                (rs, row) -> new TripResult(
                        rs.getObject("id", java.util.UUID.class).toString(),
                        rs.getString("operator_name"),
                        rs.getString("coach"),
                        rs.getString("origin_code"),
                        rs.getString("destination_code"),
                        rs.getTimestamp("depart_at").toInstant(),
                        rs.getTimestamp("arrive_at").toInstant(),
                        rs.getString("coach"),
                        rs.getInt("price_cents")),
                from,
                to,
                date);
    }

    public List<HotelResult> hotels(String city, int nights) {
        return jdbc.query(
                """
                SELECT id, name, city, nightly_rate_cents, stars
                FROM hotels
                WHERE lower(city) = lower(?)
                ORDER BY stars DESC, nightly_rate_cents
                """,
                (rs, row) -> new HotelResult(
                        rs.getObject("id", java.util.UUID.class).toString(),
                        rs.getString("name"),
                        rs.getString("city"),
                        rs.getInt("stars"),
                        rs.getInt("nightly_rate_cents"),
                        rs.getInt("nightly_rate_cents") * nights,
                        nights),
                city);
    }

    public record TripResult(
            String id,
            String title,
            String code,
            String originCode,
            String destinationCode,
            java.time.Instant departAt,
            java.time.Instant arriveAt,
            String detail,
            int priceCents) {
    }

    public record HotelResult(
            String id,
            String name,
            String city,
            int stars,
            int nightlyRateCents,
            int totalCents,
            int nights) {
    }
}
