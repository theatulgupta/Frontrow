package com.frontrow.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.frontrow.config.KafkaConfig;
import com.frontrow.support.IntegrationTestBase;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
        "frontrow.payment.mode=SUCCESS",
        "frontrow.payment.latency-ms=0",
        "frontrow.hold-ttl=PT30S"
})
class DuplicateKafkaDeliveryTest extends IntegrationTestBase {

    @Autowired
    private KafkaTemplate<String, String> kafka;

    @Test
    void aSecondDeliveryDoesNotSellOrChargeAgain() throws Exception {
        HttpResult held = postBooking("ada", SEAT_A1, "duplicate-key-1");
        assertThat(held.status()).isEqualTo(202);
        await().atMost(Duration.ofSeconds(15)).until(() -> "SOLD".equals(inventoryStatus(SEAT_A1)));

        String payload = jdbc.queryForObject(
                "SELECT payload::text FROM outbox_messages WHERE aggregate_id = ?",
                String.class,
                java.util.UUID.fromString(held.bookingId()));
        kafka.send(KafkaConfig.PAYMENT_REQUESTED, held.bookingId(), payload).get(5, TimeUnit.SECONDS);

        await().pollDelay(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(countInventory(SEAT_A1, "SOLD")).isEqualTo(1);
            assertThat(countBookings(SEAT_A1, "CONFIRMED")).isEqualTo(1);
            assertThat(countPayments("SUCCEEDED")).isEqualTo(1);
            assertThat(gateway.chargeCount()).isEqualTo(1);
        });
    }
}
