package com.frontrow.expiry;

import com.frontrow.config.FrontrowMetrics;
import com.frontrow.config.MdcScope;
import com.frontrow.inventory.BookingLifecycleStore;
import com.frontrow.inventory.HeldInventory;
import com.frontrow.inventory.SeatInventoryStore;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class HoldExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(HoldExpiryJob.class);

    private final SeatInventoryStore inventory;
    private final BookingLifecycleStore bookings;
    private final TransactionTemplate transactions;
    private final FrontrowMetrics metrics;

    public HoldExpiryJob(
            SeatInventoryStore inventory,
            BookingLifecycleStore bookings,
            TransactionTemplate transactions,
            FrontrowMetrics metrics) {
        this.inventory = inventory;
        this.bookings = bookings;
        this.transactions = transactions;
        this.metrics = metrics;
    }

    @Scheduled(fixedDelayString = "${frontrow.expiry.scan-interval}")
    public void scan() {
        transactions.executeWithoutResult(status -> {
            List<HeldInventory> expired = inventory.lockExpired(100);
            for (HeldInventory row : expired) {
                int released = inventory.releaseIfExpiredOwner(row.showId(), row.seatId(), row.bookingId());
                if (released != 1) {
                    continue;
                }
                bookings.markExpired(row.bookingId());
                metrics.holdExpired();
                try (MdcScope ignored = MdcScope.open(row.bookingId(), row.seatId(), row.showId(), null)) {
                    log.info("hold_expired");
                }
            }
        });
    }
}
