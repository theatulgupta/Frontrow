package com.frontrow.travel;

import java.time.Instant;
import java.util.UUID;

public record FlightOffer(
        UUID id,
        String airline,
        String flightNumber,
        String originCode,
        String destinationCode,
        Instant departAt,
        Instant arriveAt,
        int stops,
        String cabin,
        int priceCents,
        int capacity,
        int seatsHeld,
        int seatsSold) {

    public int seatsLeft() {
        return capacity - seatsHeld - seatsSold;
    }
}
