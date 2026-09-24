package com.frontrow.catalog;

import java.util.UUID;

public record SeatResponse(
        UUID seatId,
        String section,
        String rowLabel,
        int seatNumber,
        boolean frontRow,
        String status,
        String holderName) {
}
