package com.frontrow.catalog;

import java.time.Instant;
import java.util.UUID;

public record ShowResponse(UUID id, String name, Instant startsAt, int priceCents, String venueName) {
}
