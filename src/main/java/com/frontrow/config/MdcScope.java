package com.frontrow.config;

import java.util.UUID;
import org.slf4j.MDC;

public final class MdcScope implements AutoCloseable {

    private MdcScope() {
    }

    public static MdcScope open(UUID bookingId, UUID seatId, UUID showId, String userId) {
        put("bookingId", bookingId);
        put("seatId", seatId);
        put("showId", showId);
        if (userId != null) {
            MDC.put("userId", userId);
        }
        return new MdcScope();
    }

    private static void put(String key, UUID value) {
        if (value != null) {
            MDC.put(key, value.toString());
        }
    }

    @Override
    public void close() {
        MDC.remove("bookingId");
        MDC.remove("seatId");
        MDC.remove("showId");
        MDC.remove("userId");
    }
}
