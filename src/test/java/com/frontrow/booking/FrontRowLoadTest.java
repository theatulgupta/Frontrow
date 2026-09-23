package com.frontrow.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.frontrow.support.IntegrationTestBase;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

@Tag("load")
@TestPropertySource(properties = {
        "frontrow.payment.mode=SUCCESS",
        "frontrow.payment.latency-ms=0",
        "frontrow.hold-ttl=PT60S",
        "frontrow.lock.wait=PT60S",
        "server.tomcat.threads.max=300"
})
class FrontRowLoadTest extends IntegrationTestBase {

    @Test
    void twoHundredThreadsOnOneFrontRowSeatStillSellOnce() throws Exception {
        List<HttpResult> results = contend(SEAT_A1, 200, 120);
        assertThat(results).hasSize(200);
        assertThat(results.stream().filter(result -> result.status() == 202)).hasSize(1);
        assertThat(results).allMatch(result -> result.status() == 202
                || (result.status() == 409 && "SEAT_UNAVAILABLE".equals(result.code())));

        await().atMost(Duration.ofSeconds(30)).until(() -> "SOLD".equals(inventoryStatus(SEAT_A1)));
        assertThat(countInventory(SEAT_A1, "SOLD")).isEqualTo(1);
        assertThat(countBookings(SEAT_A1, "CONFIRMED")).isEqualTo(1);
        assertThat(countPayments("SUCCEEDED")).isEqualTo(1);
    }
}
