package com.frontrow.inventory;

import java.util.UUID;

public final class SeatLockKey {

    private SeatLockKey() {
    }

    public static String of(UUID showId, UUID seatId) {
        return "frontrow:seat:" + showId + ":" + seatId;
    }
}
