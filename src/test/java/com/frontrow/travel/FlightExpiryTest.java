package com.frontrow.travel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.frontrow.support.IntegrationTestBase;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
        "frontrow.hold-ttl=PT1S",
        "frontrow.payment.consumer-enabled=false"
})
class FlightExpiryTest extends IntegrationTestBase {

    static final UUID ONE_SEAT = UUID.fromString("66666666-6666-6666-6666-666666666601");

    @Test
    void anUnpaidFareReturnsToThePool() throws Exception {
        HttpResult held = postFlight("ada", ONE_SEAT, "flight-expiry-01");
        assertThat(held.status()).isEqualTo(202);

        await().atMost(Duration.ofSeconds(10)).until(() -> seatsHeld() == 0
                && seatsSold() == 0
                && count("EXPIRED") == 1);

        HttpResult expired = getFlightBooking("ada", held.bookingId());
        assertThat(expired.status()).isEqualTo(410);
        assertThat(expired.code()).isEqualTo("HOLD_EXPIRED");

        HttpResult replay = postFlight("ada", ONE_SEAT, "flight-expiry-01");
        assertThat(replay.status()).isEqualTo(410);

        HttpResult next = postFlight("grace", ONE_SEAT, "flight-expiry-02");
        assertThat(next.status()).isEqualTo(202);
        assertThat(seatsHeld()).isEqualTo(1);
        assertThat(count("EXPIRED")).isEqualTo(1);
        assertThat(count("PENDING_PAYMENT")).isEqualTo(1);
    }

    private int seatsHeld() {
        Integer value = jdbc.queryForObject(
                "SELECT seats_held FROM flight_offers WHERE id = ?", Integer.class, ONE_SEAT);
        return value == null ? -1 : value;
    }

    private int seatsSold() {
        Integer value = jdbc.queryForObject(
                "SELECT seats_sold FROM flight_offers WHERE id = ?", Integer.class, ONE_SEAT);
        return value == null ? -1 : value;
    }

    private int count(String status) {
        Integer value = jdbc.queryForObject(
                "SELECT count(*) FROM flight_bookings WHERE offer_id = ? AND status = ?",
                Integer.class,
                ONE_SEAT,
                status);
        return value == null ? 0 : value;
    }
}
