package com.frontrow.booking;

import static org.assertj.core.api.Assertions.assertThat;

import com.frontrow.support.IntegrationTestBase;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import static org.awaitility.Awaitility.await;

@TestPropertySource(properties = {
        "frontrow.payment.mode=SUCCESS",
        "frontrow.payment.latency-ms=10",
        "frontrow.hold-ttl=PT30S",
        "frontrow.lock.wait=PT5S",
        "frontrow.payment.consumer-enabled=true"
})
class FrontRowSeatConcurrencyTest extends IntegrationTestBase {

    @Test
    void oneBuyerIsSoldAndEveryLoserIsRejected() throws Exception {
        List<HttpResult> results = contend(SEAT_A1, 64, 60);

        assertThat(results).hasSize(64);
        assertThat(results.stream().filter(result -> result.status() == 202)).hasSize(1);
        assertThat(results).allMatch(result -> result.status() == 202
                || (result.status() == 409 && "SEAT_UNAVAILABLE".equals(result.code())));

        await().atMost(Duration.ofSeconds(20)).until(() -> "SOLD".equals(inventoryStatus(SEAT_A1)));

        assertThat(countInventory(SEAT_A1, "SOLD")).isEqualTo(1);
        assertThat(countBookings(SEAT_A1, "CONFIRMED")).isEqualTo(1);
        assertThat(countBookings(SEAT_A1, "PENDING_PAYMENT")).isZero();
        assertThat(countPayments("SUCCEEDED")).isEqualTo(1);
        assertThat(gateway.chargeCount()).isEqualTo(1);
    }
}
