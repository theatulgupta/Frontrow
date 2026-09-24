package com.frontrow.travel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.frontrow.support.IntegrationTestBase;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
        "frontrow.lock.wait=PT5S",
        "frontrow.payment.latency-ms=0",
        "frontrow.hold-ttl=PT2M"
})
class FlightHoldConcurrencyTest extends IntegrationTestBase {

    static final UUID ONE_SEAT = UUID.fromString("66666666-6666-6666-6666-666666666601");

    @Test
    void oneSeatFareSellsOnce() throws Exception {
        List<HttpResult> results = contend(32);

        assertThat(results).hasSize(32);
        assertThat(results.stream().filter(result -> result.status() == 202)).hasSize(1);
        assertThat(results.stream().filter(result -> result.status() == 409 && "FARE_UNAVAILABLE".equals(result.code())))
                .hasSize(31);

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            Integer sold = jdbc.queryForObject(
                    "SELECT seats_sold FROM flight_offers WHERE id = ?", Integer.class, ONE_SEAT);
            Integer held = jdbc.queryForObject(
                    "SELECT seats_held FROM flight_offers WHERE id = ?", Integer.class, ONE_SEAT);
            assertThat(sold).isEqualTo(1);
            assertThat(held).isZero();
        });
    }

    private List<HttpResult> contend(int threads) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CyclicBarrier barrier = new CyclicBarrier(threads);
        CountDownLatch done = new CountDownLatch(threads);
        List<HttpResult> results = new CopyOnWriteArrayList<>();
        for (int i = 0; i < threads; i++) {
            int id = i;
            pool.submit(() -> {
                try {
                    barrier.await(30, TimeUnit.SECONDS);
                    results.add(postFlight("flyer-" + id, ONE_SEAT, "flight-key-" + id));
                } catch (Exception exception) {
                    results.add(new HttpResult(500, exception.getClass().getSimpleName(), null, exception.getMessage()));
                } finally {
                    done.countDown();
                }
            });
        }
        if (!done.await(40, TimeUnit.SECONDS)) {
            pool.shutdownNow();
            throw new AssertionError("Flight booking threads did not finish");
        }
        pool.shutdownNow();
        return List.copyOf(results);
    }
}
