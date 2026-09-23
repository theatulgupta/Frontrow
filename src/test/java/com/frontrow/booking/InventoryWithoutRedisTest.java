package com.frontrow.booking;

import static org.assertj.core.api.Assertions.assertThat;

import com.frontrow.inventory.SeatInventoryStore;
import com.frontrow.support.IntegrationTestBase;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

class InventoryWithoutRedisTest extends IntegrationTestBase {

    @Autowired
    private SeatInventoryStore inventory;

    @Autowired
    private TransactionTemplate transactions;

    @Test
    void conditionalUpdateAllowsOneHoldWhenTheLockIsSkipped() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        Future<Integer> left = pool.submit(() -> hold(barrier, first));
        Future<Integer> right = pool.submit(() -> hold(barrier, second));
        int wins = left.get() + right.get();
        pool.shutdownNow();

        assertThat(wins).isEqualTo(1);
        assertThat(inventoryStatus(SEAT_A1)).isEqualTo("HELD");
        assertThat(countInventory(SEAT_A1, "HELD")).isEqualTo(1);
    }

    private int hold(CyclicBarrier barrier, UUID bookingId) throws Exception {
        barrier.await();
        Integer updated = transactions.execute(status -> inventory.holdIfAvailable(
                SHOW_ID,
                SEAT_A1,
                bookingId,
                Instant.now().plusSeconds(30)));
        return updated == null ? 0 : updated;
    }
}
