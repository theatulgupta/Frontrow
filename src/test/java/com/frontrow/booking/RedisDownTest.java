package com.frontrow.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.frontrow.support.IntegrationTestBase;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisConnectionException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class RedisDownTest extends IntegrationTestBase {

    @MockitoBean
    private RedissonClient redissonClient;

    @BeforeEach
    void redisRejectsLocks() throws InterruptedException {
        RLock lock = org.mockito.Mockito.mock(RLock.class);
        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class)))
                .thenThrow(new RedisConnectionException("Redis is down"));
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
