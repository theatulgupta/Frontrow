package com.frontrow.booking;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.frontrow.catalog.CatalogRepository;
import com.frontrow.catalog.ShowResponse;
import com.frontrow.config.ApiException;
import com.frontrow.config.ErrorCodes;
import com.frontrow.config.FrontrowMetrics;
import com.frontrow.config.FrontrowProperties;
import com.frontrow.config.MdcScope;
import com.frontrow.inventory.SeatInventoryStore;
import com.frontrow.inventory.SeatLockKey;
import com.frontrow.outbox.OutboxStore;
import com.frontrow.outbox.PaymentRequestedEvent;
import com.frontrow.risk.RiskAssessment;
import com.frontrow.risk.RiskDecision;
import com.frontrow.risk.RiskEvaluationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class CreateBookingService {

    private static final Logger log = LoggerFactory.getLogger(CreateBookingService.class);

    private final BookingRepository bookings;
    private final AttemptRepository attempts;
    private final CatalogRepository catalog;
    private final SeatInventoryStore inventory;
    private final OutboxStore outbox;
    private final RiskEvaluationService risk;
    private final RedissonClient redisson;
    private final TransactionTemplate transactions;
    private final FrontrowProperties properties;
    private final FrontrowMetrics metrics;
    private final ObjectMapper objectMapper;

    public CreateBookingService(
            BookingRepository bookings,
            AttemptRepository attempts,
            CatalogRepository catalog,
            SeatInventoryStore inventory,
            OutboxStore outbox,
            RiskEvaluationService risk,
            RedissonClient redisson,
            TransactionTemplate transactions,
            FrontrowProperties properties,
            FrontrowMetrics metrics,
            ObjectMapper objectMapper) {
        this.bookings = bookings;
        this.attempts = attempts;
        this.catalog = catalog;
        this.inventory = inventory;
        this.outbox = outbox;
        this.risk = risk;
        this.redisson = redisson;
        this.transactions = transactions;
        this.properties = properties;
        this.metrics = metrics;
        this.objectMapper = objectMapper;
    }

    public BookingRecord create(String userId, UUID showId, UUID seatId, String idempotencyKey) {
        try (MdcScope ignored = MdcScope.open(null, seatId, showId, userId)) {
            Optional<BookingRecord> existing = bookings.findByUserAndKey(userId, idempotencyKey);
            if (existing.isPresent()) {
                return replay(existing.get(), showId, seatId).booking();
            }
            rejectStoredAttempt(userId, showId, seatId, idempotencyKey);
            ShowResponse show = catalog.requireShow(showId);
            if (!catalog.seatExists(showId, seatId)) {
                throw ApiException.notFound("Seat not found");
            }
            RiskEvaluationService.Outcome outcome = risk.evaluate(userId, showId, seatId);
            if (outcome.assessment().decision() != RiskDecision.ALLOW) {
                block(userId, showId, seatId, idempotencyKey, outcome);
            }
            return hold(userId, show, seatId, idempotencyKey, outcome);
        }
    }

    public BookingRecord get(String userId, UUID bookingId) {
        BookingRecord booking = bookings.findById(bookingId)
                .filter(row -> row.userId().equals(userId))
                .orElseThrow(() -> ApiException.notFound("Booking not found"));
        if (booking.status() == BookingStatus.EXPIRED) {
            throw ApiException.holdExpired(booking.id());
        }
        return booking;
    }

    private void rejectStoredAttempt(String userId, UUID showId, UUID seatId, String idempotencyKey) {
        attempts.findByUserAndKey(userId, idempotencyKey).ifPresent(stored -> {
            if ("REJECT".equals(stored.decision()) || "CHALLENGE".equals(stored.decision())) {
                throw riskException(stored.decision(), stored.reason(), showId, seatId);
            }
            if ("ALLOW".equals(stored.decision())) {
                metrics.conflict("seat_unavailable");
                throw ApiException.seatUnavailable(showId, seatId);
            }
        });
    }

    private void block(String userId, UUID showId, UUID seatId, String idempotencyKey, RiskEvaluationService.Outcome outcome) {
        try {
            transactions.executeWithoutResult(status -> attempts.insert(
                    UUID.randomUUID(),
                    userId,
                    showId,
                    seatId,
                    idempotencyKey,
                    outcome.assessment(),
                    outcome.signals()));
        } catch (DataIntegrityViolationException exception) {
            attempts.findByUserAndKey(userId, idempotencyKey).ifPresent(stored -> {
                throw riskException(stored.decision(), stored.reason(), showId, seatId);
            });
        }
        throw riskException(outcome.assessment().decision().name(), outcome.assessment().reason(), showId, seatId);
    }

    private BookingRecord hold(
            String userId,
            ShowResponse show,
            UUID seatId,
            String idempotencyKey,
            RiskEvaluationService.Outcome outcome) {
        RLock lock = redisson.getLock(SeatLockKey.of(show.id(), seatId));
        long started = System.nanoTime();
        boolean acquired;
        try {
            acquired = lock.tryLock(
                    properties.getLock().getWait().toMillis(),
                    properties.getLock().getLease().toMillis(),
                    TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            metrics.lockWait("error", elapsed(started));
            throw ApiException.lockUnavailable(show.id(), seatId);
        } catch (RuntimeException exception) {
            metrics.lockWait("error", elapsed(started));
            log.warn("lock_unavailable showId={} seatId={}", show.id(), seatId, exception);
            throw ApiException.lockUnavailable(show.id(), seatId);
        }
        if (!acquired) {
            metrics.lockWait("timeout", elapsed(started));
            throw ApiException.lockTimeout(show.id(), seatId);
        }
        metrics.lockWait("acquired", elapsed(started));
        try {
            try {
                Held held = transactions.execute(status -> writeHold(userId, show, seatId, idempotencyKey, outcome));
                if (held == null) {
                    throw ApiException.seatUnavailable(show.id(), seatId);
                }
                if (held.created()) {
                    MDC.put("bookingId", held.booking().id().toString());
                    log.info("hold_created");
                }
                return held.booking();
            } catch (DataIntegrityViolationException exception) {
                try {
                    return resolveConflict(userId, show.id(), seatId, idempotencyKey);
                } catch (ApiException conflict) {
                    if (ErrorCodes.SEAT_UNAVAILABLE.equals(conflict.code())) {
                        recordLostAttempt(userId, show, seatId, idempotencyKey, outcome);
                    }
                    throw conflict;
                }
            } catch (ApiException exception) {
                if (ErrorCodes.SEAT_UNAVAILABLE.equals(exception.code())) {
                    recordLostAttempt(userId, show, seatId, idempotencyKey, outcome);
                }
                throw exception;
            }
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private Held writeHold(
            String userId,
            ShowResponse show,
            UUID seatId,
            String idempotencyKey,
            RiskEvaluationService.Outcome outcome) {
        Optional<BookingRecord> existing = bookings.findByUserAndKey(userId, idempotencyKey);
        if (existing.isPresent()) {
            return replay(existing.get(), show.id(), seatId);
        }
        UUID bookingId = UUID.randomUUID();
        Instant holdExpiresAt = Instant.now().plus(properties.getHoldTtl());
        int updated = inventory.holdIfAvailable(show.id(), seatId, bookingId, holdExpiresAt);
        if (updated == 0) {
            Optional<BookingRecord> raced = bookings.findByUserAndKey(userId, idempotencyKey);
            if (raced.isPresent()) {
                return replay(raced.get(), show.id(), seatId);
            }
            metrics.conflict("seat_unavailable");
            log.info("hold_rejected");
            throw ApiException.seatUnavailable(show.id(), seatId);
        }
        RiskAssessment assessment = outcome.assessment();
        bookings.insert(
                bookingId,
                show.id(),
                seatId,
                userId,
                idempotencyKey,
                show.priceCents(),
                holdExpiresAt,
                assessment.decision().name(),
                assessment.reason(),
                assessment.flagged());
        attempts.insert(UUID.randomUUID(), userId, show.id(), seatId, idempotencyKey, assessment, outcome.signals());
        UUID eventId = UUID.randomUUID();
        outbox.insert(eventId, bookingId, payload(eventId, bookingId, show, seatId, userId, holdExpiresAt));
        BookingRecord booking = bookings.findById(bookingId).orElseThrow();
        return new Held(booking, true);
    }

    private Held replay(BookingRecord existing, UUID showId, UUID seatId) {
        if (!existing.seatId().equals(seatId) || !existing.showId().equals(showId)) {
            metrics.conflict("idempotency_conflict");
            throw ApiException.idempotencyConflict(showId, seatId);
        }
        return new Held(existing, false);
    }

    private void recordLostAttempt(
            String userId,
            ShowResponse show,
            UUID seatId,
            String idempotencyKey,
            RiskEvaluationService.Outcome outcome) {
        transactions.executeWithoutResult(status -> attempts.insertIgnoringConflict(
                UUID.randomUUID(),
                userId,
                show.id(),
                seatId,
                idempotencyKey,
                outcome.assessment(),
                outcome.signals()));
    }

    private BookingRecord resolveConflict(String userId, UUID showId, UUID seatId, String idempotencyKey) {
        return bookings.findByUserAndKey(userId, idempotencyKey)
                .map(existing -> replay(existing, showId, seatId).booking())
                .orElseThrow(() -> {
                    metrics.conflict("seat_unavailable");
                    log.info("hold_rejected");
                    return ApiException.seatUnavailable(showId, seatId);
                });
    }

    private String payload(
            UUID eventId,
            UUID bookingId,
            ShowResponse show,
            UUID seatId,
            String userId,
            Instant holdExpiresAt) {
        try {
            return objectMapper.writeValueAsString(new PaymentRequestedEvent(
                    eventId,
                    PaymentRequestedEvent.EVENT_TYPE,
                    bookingId,
                    show.id(),
                    seatId,
                    userId,
                    show.priceCents(),
                    holdExpiresAt));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to write payment event", exception);
        }
    }

    private static ApiException riskException(String decision, String reason, UUID showId, UUID seatId) {
        String code = "CHALLENGE".equals(decision) ? ErrorCodes.RISK_CHALLENGE : ErrorCodes.RISK_REJECT;
        return ApiException.risk(code, reason, showId, seatId);
    }

    private static java.time.Duration elapsed(long startedNanos) {
        return java.time.Duration.ofNanos(System.nanoTime() - startedNanos);
    }

    private record Held(BookingRecord booking, boolean created) {
    }
}
