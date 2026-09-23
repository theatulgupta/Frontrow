package com.frontrow.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.frontrow.booking.BookingRecord;
import com.frontrow.booking.BookingRepository;
import com.frontrow.config.FrontrowMetrics;
import com.frontrow.config.FrontrowProperties;
import com.frontrow.config.KafkaConfig;
import com.frontrow.config.MdcScope;
import com.frontrow.inventory.SeatInventoryStore;
import com.frontrow.outbox.PaymentRequestedEvent;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@ConditionalOnProperty(name = "frontrow.payment.consumer-enabled", havingValue = "true", matchIfMissing = true)
public class PaymentRequestedConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentRequestedConsumer.class);

    private final ObjectMapper objectMapper;
    private final BookingRepository bookings;
    private final PaymentRepository payments;
    private final SeatInventoryStore inventory;
    private final PaymentGateway gateway;
    private final TransactionTemplate transactions;
    private final FrontrowProperties properties;
    private final FrontrowMetrics metrics;

    public PaymentRequestedConsumer(
            ObjectMapper objectMapper,
            BookingRepository bookings,
            PaymentRepository payments,
            SeatInventoryStore inventory,
            PaymentGateway gateway,
            TransactionTemplate transactions,
            FrontrowProperties properties,
            FrontrowMetrics metrics) {
        this.objectMapper = objectMapper;
        this.bookings = bookings;
        this.payments = payments;
        this.inventory = inventory;
        this.gateway = gateway;
        this.transactions = transactions;
        this.properties = properties;
        this.metrics = metrics;
    }

    @KafkaListener(topics = KafkaConfig.PAYMENT_REQUESTED)
    public void onPaymentRequested(String payload, Acknowledgment acknowledgment) {
        PaymentRequestedEvent event;
        try {
            event = objectMapper.readValue(payload, PaymentRequestedEvent.class);
        } catch (Exception exception) {
            log.error("payment_payload_invalid", exception);
            acknowledgment.acknowledge();
            return;
        }
        try (MdcScope ignored = MdcScope.open(event.bookingId(), event.seatId(), event.showId(), event.userId())) {
            handle(event, acknowledgment);
        }
    }

    private void handle(PaymentRequestedEvent event, Acknowledgment acknowledgment) {
        BookingRecord booking = bookings.findById(event.bookingId()).orElse(null);
        if (booking == null) {
            acknowledgment.acknowledge();
            return;
        }
        PaymentRecord existing = payments.findByBookingId(booking.id()).orElse(null);
        if (!bookings.isPayable(booking.id())) {
            if (existing != null && existing.status() == PaymentStatus.PENDING) {
                if (payments.markVoided(booking.id()) == 1) {
                    metrics.payment("voided");
                    log.info("payment_voided");
                }
            }
            acknowledgment.acknowledge();
            return;
        }
        if (existing != null && existing.status() != PaymentStatus.PENDING) {
            reapply(booking, existing);
            acknowledgment.acknowledge();
            return;
        }
        PaymentRecord payment = existing == null ? payments.insertPending(booking.id()) : existing;
        if (payment.status() == PaymentStatus.PENDING && payment.attemptCount() >= properties.getPayment().getMaxAttempts()) {
            finalizeTimeout(booking);
            acknowledgment.acknowledge();
            return;
        }
        PaymentResult result;
        try {
            result = gateway.charge(booking.id(), booking.amountCents());
        } catch (PaymentTimeoutException exception) {
            int attempts = payments.incrementAttempt(booking.id());
            if (attempts >= properties.getPayment().getMaxAttempts()) {
                finalizeTimeout(booking);
                acknowledgment.acknowledge();
            } else {
                acknowledgment.nack(Duration.ofSeconds(1));
            }
            return;
        }
        if (!result.success()) {
            applyDecline(booking, result.gatewayReference());
            acknowledgment.acknowledge();
            return;
        }
        applySuccess(booking, result.gatewayReference());
        acknowledgment.acknowledge();
    }

    private void reapply(BookingRecord booking, PaymentRecord payment) {
        switch (payment.status()) {
            case SUCCEEDED -> transactions.executeWithoutResult(status -> {
                inventory.markSoldIfOwner(booking.showId(), booking.seatId(), booking.id());
                bookings.markConfirmed(booking.id());
            });
            case DECLINED, TIMED_OUT -> transactions.executeWithoutResult(status -> {
                inventory.releaseIfOwner(booking.showId(), booking.seatId(), booking.id());
                bookings.markCancelled(booking.id());
            });
            case PENDING, VOIDED -> {
            }
        }
    }

    private void applySuccess(BookingRecord booking, String reference) {
        transactions.executeWithoutResult(status -> {
            int sold = inventory.markSoldIfOwner(booking.showId(), booking.seatId(), booking.id());
            if (sold == 1) {
                bookings.markConfirmed(booking.id());
                if (payments.markSucceeded(booking.id(), reference) == 1) {
                    metrics.payment("succeeded");
                    log.info("payment_succeeded");
                }
                return;
            }
            if (payments.markVoided(booking.id()) == 1) {
                metrics.payment("voided");
                log.info("payment_voided");
            }
        });
    }

    private void applyDecline(BookingRecord booking, String reference) {
        transactions.executeWithoutResult(status -> {
            inventory.releaseIfOwner(booking.showId(), booking.seatId(), booking.id());
            bookings.markCancelled(booking.id());
            if (payments.markDeclined(booking.id(), reference) == 1) {
                metrics.payment("declined");
                log.info("payment_declined");
            }
        });
    }

    private void finalizeTimeout(BookingRecord booking) {
        transactions.executeWithoutResult(status -> {
            inventory.releaseIfOwner(booking.showId(), booking.seatId(), booking.id());
            bookings.markCancelled(booking.id());
            if (payments.markTimedOut(booking.id()) == 1) {
                metrics.payment("timed_out");
                log.info("payment_timed_out");
            }
        });
    }
}
