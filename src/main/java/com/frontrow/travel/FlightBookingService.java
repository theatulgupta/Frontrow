package com.frontrow.travel;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.frontrow.config.ApiException;
import com.frontrow.config.FrontrowProperties;
import com.frontrow.outbox.OutboxStore;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class FlightBookingService {

    static final String EVENT_TYPE = "flight.payment-requested";

    private final FlightInventoryStore inventory;
    private final OutboxStore outbox;
    private final RedissonClient redisson;
    private final TransactionTemplate transactions;
    private final FrontrowProperties properties;
    private final ObjectMapper objectMapper;

    public FlightBookingService(
            FlightInventoryStore inventory,
            OutboxStore outbox,
            RedissonClient redisson,
            TransactionTemplate transactions,
            FrontrowProperties properties,
            ObjectMapper objectMapper) {
        this.inventory = inventory;
        this.outbox = outbox;
        this.redisson = redisson;
        this.transactions = transactions;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public FlightBooking create(String userId, UUID offerId, String idempotencyKey) {
        Optional<FlightBooking> existing = inventory.findByUserAndKey(userId, idempotencyKey);
        if (existing.isPresent()) {
            return replay(existing.get(), offerId);
        }
        FlightOffer offer = inventory.findOffer(offerId).orElseThrow(() -> ApiException.notFound("Flight not found"));
        RLock lock = redisson.getLock("frontrow:flight:" + offerId);
        boolean acquired;
        try {
            acquired = lock.tryLock(
                    properties.getLock().getWait().toMillis(),
                    properties.getLock().getLease().toMillis(),
                    TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw ApiException.lockUnavailable(null, null);
        } catch (RuntimeException exception) {
            throw ApiException.lockUnavailable(null, null);
        }
        if (!acquired) {
            throw ApiException.lockTimeout(null, null);
        }
        try {
            try {
                return transactions.execute(status -> write(userId, offer, idempotencyKey));
            } catch (DataIntegrityViolationException exception) {
                return inventory.findByUserAndKey(userId, idempotencyKey).orElseThrow(() -> ApiException.fareFull());
            }
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    public FlightBooking get(String userId, UUID bookingId) {
        FlightBooking booking = inventory.findBooking(bookingId)
                .filter(row -> row.userId().equals(userId))
                .orElseThrow(() -> ApiException.notFound("Booking not found"));
        if ("EXPIRED".equals(booking.status())) {
            throw ApiException.holdExpired(booking.id());
        }
        return booking;
    }

    private FlightBooking write(String userId, FlightOffer offer, String idempotencyKey) {
        Optional<FlightBooking> existing = inventory.findByUserAndKey(userId, idempotencyKey);
        if (existing.isPresent()) {
            return replay(existing.get(), offer.id());
        }
        int held = inventory.holdOneSeat(offer.id());
        if (held == 0) {
            throw ApiException.fareFull();
        }
        UUID bookingId = UUID.randomUUID();
        Instant expiresAt = Instant.now().plus(properties.getHoldTtl());
        inventory.insertBooking(bookingId, offer.id(), userId, idempotencyKey, offer.priceCents(), expiresAt);
        UUID eventId = UUID.randomUUID();
        outbox.insert(eventId, bookingId, EVENT_TYPE, payload(eventId, bookingId, offer, userId, expiresAt));
        return inventory.findBooking(bookingId).orElseThrow();
    }

    private FlightBooking replay(FlightBooking existing, UUID offerId) {
        if (!existing.offerId().equals(offerId)) {
            throw ApiException.idempotencyConflict(null, null);
        }
        if ("EXPIRED".equals(existing.status())) {
            throw ApiException.holdExpired(existing.id());
        }
        return existing;
    }

    private String payload(UUID eventId, UUID bookingId, FlightOffer offer, String userId, Instant expiresAt) {
        try {
            return objectMapper.writeValueAsString(new FlightPaymentEvent(
                    eventId, EVENT_TYPE, bookingId, offer.id(), userId, offer.priceCents(), expiresAt));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to write flight payment event", exception);
        }
    }

    public record FlightPaymentEvent(
            UUID eventId,
            String eventType,
            UUID bookingId,
            UUID offerId,
            String userId,
            int amountCents,
            Instant holdExpiresAt) {
    }

}
