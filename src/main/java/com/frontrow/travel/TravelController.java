package com.frontrow.travel;

import com.frontrow.config.ApiException;
import com.frontrow.identity.TokenAuthenticationFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class TravelController {

    private final TravelCatalogStore catalog;
    private final FlightInventoryStore flights;
    private final FlightBookingService bookings;

    public TravelController(TravelCatalogStore catalog, FlightInventoryStore flights, FlightBookingService bookings) {
        this.catalog = catalog;
        this.flights = flights;
        this.bookings = bookings;
    }

    @GetMapping("/flights")
    public List<FlightOffer> flights(@RequestParam String from, @RequestParam String to, @RequestParam LocalDate date) {
        return flights.search(from.toUpperCase(), to.toUpperCase(), date);
    }

    @GetMapping("/flights/{offerId}")
    public FlightOffer flight(@PathVariable UUID offerId) {
        return flights.findOffer(offerId).orElseThrow(() -> ApiException.notFound("Flight not found"));
    }

    @GetMapping("/trains")
    public List<TravelCatalogStore.TripResult> trains(
            @RequestParam String from, @RequestParam String to, @RequestParam LocalDate date) {
        return catalog.trains(from.toUpperCase(), to.toUpperCase(), date);
    }

    @GetMapping("/buses")
    public List<TravelCatalogStore.TripResult> buses(
            @RequestParam String from, @RequestParam String to, @RequestParam LocalDate date) {
        return catalog.buses(from.toUpperCase(), to.toUpperCase(), date);
    }

    @GetMapping("/hotels")
    public List<TravelCatalogStore.HotelResult> hotels(
            @RequestParam String city, @RequestParam LocalDate checkIn, @RequestParam LocalDate checkOut) {
        long nights = ChronoUnit.DAYS.between(checkIn, checkOut);
        if (nights < 1) {
            throw ApiException.validation("Check-out must be after check-in");
        }
        return catalog.hotels(city, (int) nights);
    }

    @PostMapping("/flights/{offerId}/bookings")
    public ResponseEntity<FlightBookingResponse> book(
            @PathVariable UUID offerId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request) {
        requireKey(idempotencyKey);
        FlightBooking booking = bookings.create(userId(request), offerId, idempotencyKey);
        FlightBookingResponse body = FlightBookingResponse.from(booking);
        if ("PENDING_PAYMENT".equals(booking.status())) {
            return ResponseEntity.accepted().body(body);
        }
        return ResponseEntity.ok(body);
    }

    @GetMapping("/flight-bookings/{bookingId}")
    public FlightBookingResponse booking(@PathVariable UUID bookingId, HttpServletRequest request) {
        return FlightBookingResponse.from(bookings.get(userId(request), bookingId));
    }

    private static String userId(HttpServletRequest request) {
        Object value = request.getAttribute(TokenAuthenticationFilter.USER_ATTRIBUTE);
        if (value instanceof String userId && !userId.isBlank()) {
            return userId;
        }
        throw ApiException.unauthorized();
    }

    private static void requireKey(String key) {
        if (key == null || key.length() < 8 || key.length() > 128 || key.chars().anyMatch(ch -> ch < 32 || ch > 126)) {
            throw ApiException.validation("Idempotency-Key must be 8-128 printable ASCII characters");
        }
    }

    public record FlightBookingResponse(
            UUID bookingId,
            UUID offerId,
            String userId,
            String status,
            Instant holdExpiresAt,
            int priceCents) {

        static FlightBookingResponse from(FlightBooking booking) {
            return new FlightBookingResponse(
                    booking.id(),
                    booking.offerId(),
                    booking.userId(),
                    booking.status(),
                    booking.holdExpiresAt(),
                    booking.amountCents());
        }
    }
}
