package com.frontrow.travel;

import static org.assertj.core.api.Assertions.assertThat;

import com.frontrow.support.IntegrationTestBase;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FlightSearchTest extends IntegrationTestBase {

    @Test
    void delhiToMumbaiReturnsOffersAndATrainSearchWritesNothing() throws Exception {
        String flightDate = jdbc.queryForObject(
                """
                SELECT to_char(depart_at, 'YYYY-MM-DD')
                FROM flight_offers
                WHERE id = ?
                """,
                String.class,
                UUID.fromString("66666666-6666-6666-6666-666666666601"));
        HttpResult flights = get("/api/flights?from=del&to=bom&date=" + flightDate);
        assertThat(flights.status()).isEqualTo(200);
        assertThat(flights.body()).contains("6E901").contains("IndiGo");

        Integer flightBookings = jdbc.queryForObject("SELECT count(*) FROM flight_bookings", Integer.class);
        Integer showBookings = jdbc.queryForObject("SELECT count(*) FROM bookings", Integer.class);

        String trainDate = jdbc.queryForObject(
                """
                SELECT to_char(min(depart_at), 'YYYY-MM-DD')
                FROM train_trips
                WHERE origin_code = 'DEL' AND destination_code = 'BOM'
                """,
                String.class);
        HttpResult trains = get("/api/trains?from=DEL&to=BOM&date=" + trainDate);
        assertThat(trains.status()).isEqualTo(200);
        assertThat(trains.body()).contains("Rajdhani Express");

        String busDate = jdbc.queryForObject(
                """
                SELECT to_char(min(depart_at), 'YYYY-MM-DD')
                FROM bus_trips
                WHERE origin_code = 'DEL' AND destination_code = 'JAI'
                """,
                String.class);
        HttpResult buses = get("/api/buses?from=DEL&to=JAI&date=" + busDate);
        assertThat(buses.status()).isEqualTo(200);
        assertThat(buses.body()).contains("Zingbus");

        HttpResult hotels = get("/api/hotels?city=Mumbai&checkIn=" + flightDate + "&checkOut=" + flightDate);
        assertThat(hotels.status()).isEqualTo(400);

        HttpResult stay = get("/api/hotels?city=mumbai&checkIn=2026-09-24&checkOut=2026-09-26");
        assertThat(stay.status()).isEqualTo(200);
        assertThat(stay.body()).contains("Marine View").contains("\"nights\":2");

        assertThat(jdbc.queryForObject("SELECT count(*) FROM flight_bookings", Integer.class)).isEqualTo(flightBookings);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bookings", Integer.class)).isEqualTo(showBookings);
    }
}
