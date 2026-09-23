package com.frontrow.booking;

import static org.assertj.core.api.Assertions.assertThat;

import com.frontrow.inventory.SeatLockKey;
import com.frontrow.support.IntegrationTestBase;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
        "frontrow.lock.wait=PT0.05S",
        "frontrow.payment.consumer-enabled=false"
})
class LockTimeoutTest extends IntegrationTestBase {

    @Test
    void aHeldSeatLockReturns503WithoutWriting() throws Exception {
        RLock lock = redisson.getLock(SeatLockKey.of(SHOW_ID, SEAT_A1));
        assertThat(lock.tryLock(1, 30, TimeUnit.SECONDS)).isTrue();
        try {
            HttpResult result = postBooking("ada", SEAT_A1, "lock-timeout-key");
            assertThat(result.status()).isEqualTo(503);
            assertThat(result.code()).isEqualTo("LOCK_TIMEOUT");
            assertThat(inventoryStatus(SEAT_A1)).isEqualTo("AVAILABLE");
            assertThat(countBookings(SEAT_A1, "PENDING_PAYMENT")).isZero();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
