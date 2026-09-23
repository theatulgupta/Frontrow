package com.frontrow.booking;

import com.frontrow.config.ApiException;
import com.frontrow.identity.TokenAuthenticationFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class BookingController {

    private final CreateBookingService bookings;

    public BookingController(CreateBookingService bookings) {
        this.bookings = bookings;
    }

    @PostMapping("/shows/{showId}/bookings")
    public ResponseEntity<BookingResponse> create(
            @PathVariable UUID showId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateBookingRequest request,
            HttpServletRequest http) {
        requireKey(idempotencyKey);
        BookingRecord booking = bookings.create(userId(http), showId, request.seatId(), idempotencyKey);
        if (booking.status() == BookingStatus.EXPIRED) {
            throw ApiException.holdExpired(booking.id());
        }
        BookingResponse body = BookingResponse.from(booking);
        if (booking.status() == BookingStatus.PENDING_PAYMENT) {
            return ResponseEntity.accepted().body(body);
        }
        return ResponseEntity.ok(body);
    }

    @GetMapping("/bookings/{bookingId}")
    public BookingResponse get(@PathVariable UUID bookingId, HttpServletRequest http) {
        return BookingResponse.from(bookings.get(userId(http), bookingId));
    }

    private static String userId(HttpServletRequest request) {
        Object userId = request.getAttribute(TokenAuthenticationFilter.USER_ATTRIBUTE);
        if (userId instanceof String value && !value.isBlank()) {
            return value;
        }
        throw ApiException.unauthorized();
    }

    private static void requireKey(String key) {
        if (key == null || key.length() < 8 || key.length() > 128 || key.chars().anyMatch(ch -> ch < 32 || ch > 126)) {
            throw ApiException.validation("Idempotency-Key must be 8-128 printable ASCII characters");
        }
    }

    public record CreateBookingRequest(@NotNull UUID seatId) {
    }

    public record BookingResponse(
            UUID bookingId,
            UUID showId,
            UUID seatId,
            String userId,
            String status,
            Instant holdExpiresAt,
            int priceCents,
            String paymentStatus) {

        static BookingResponse from(BookingRecord booking) {
            return new BookingResponse(
                    booking.id(),
                    booking.showId(),
                    booking.seatId(),
                    booking.userId(),
                    booking.status().name(),
                    booking.holdExpiresAt(),
                    booking.amountCents(),
                    booking.paymentStatus());
        }
    }
}
