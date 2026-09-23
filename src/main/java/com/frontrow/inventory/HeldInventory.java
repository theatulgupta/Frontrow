package com.frontrow.inventory;

import java.util.UUID;

public record HeldInventory(UUID showId, UUID seatId, UUID bookingId) {
}
