package com.frontrow.booking;

import static org.assertj.core.api.Assertions.assertThat;

import com.frontrow.support.IntegrationTestBase;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
        "frontrow.payment.consumer-enabled=false",
        "frontrow.lock.wait=PT5S",
        "frontrow.hold-ttl=PT30S"
})
class SeatHoldConcurrencyTest extends IntegrationTestBase {

    @Test
    void oneFrontRowSeatIsHeldAndEveryLoserIsRejected() throws Exception {
        List<HttpResult> results = contend(SEAT_A1, 64, 60);

        assertThat(results).hasSize(64);
        assertThat(results.stream().filter(result -> result.status() == 202)).hasSize(1);
        assertThat(results.stream().filter(result -> result.status() == 409)).hasSize(63);
        assertThat(results).allMatch(result -> result.status() == 202
                || (result.status() == 409 && "SEAT_UNAVAILABLE".equals(result.code())));
        assertThat(inventoryStatus(SEAT_A1)).isEqualTo("HELD");
        assertThat(countInventory(SEAT_A1, "HELD")).isEqualTo(1);
        assertThat(countBookings(SEAT_A1, "PENDING_PAYMENT")).isEqualTo(1);
        assertThat(countBookings(SEAT_A1, "CONFIRMED")).isZero();
    }
}
