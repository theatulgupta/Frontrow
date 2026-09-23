package com.frontrow.booking;

import static org.assertj.core.api.Assertions.assertThat;

import com.frontrow.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "frontrow.payment.consumer-enabled=false")
class RedisDownTest extends IntegrationTestBase {

    @DynamicPropertySource
    static void redisDown(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", () -> "127.0.0.1");
        registry.add("spring.data.redis.port", () -> 1);
    }

    @Test
    void redisDownDoesNotBookTheSeat() throws Exception {
        HttpResult result = postBooking("ada", SEAT_A1, "redis-down-key");
        assertThat(result.status()).isEqualTo(503);
        assertThat(result.code()).isEqualTo("LOCK_UNAVAILABLE");
        assertThat(inventoryStatus(SEAT_A1)).isEqualTo("AVAILABLE");
        assertThat(countBookings(SEAT_A1, "PENDING_PAYMENT")).isZero();
    }
}
