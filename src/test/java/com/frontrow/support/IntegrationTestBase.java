package com.frontrow.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.frontrow.identity.TokenSigner;
import com.frontrow.payment.SimulatedPaymentGateway;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    public static final UUID SHOW_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    public static final UUID SEAT_A1 = UUID.fromString("33333333-3333-3333-3333-333333333301");
    public static final UUID SEAT_A2 = UUID.fromString("33333333-3333-3333-3333-333333333302");

    private final HttpClient http = HttpClient.newHttpClient();

    static {
        Containers.start();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        String group = "frontrow-payment-" + UUID.randomUUID();
        registry.add("spring.datasource.url", Containers.POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", Containers.POSTGRES::getUsername);
        registry.add("spring.datasource.password", Containers.POSTGRES::getPassword);
        registry.add("spring.data.redis.host", Containers.REDIS::getHost);
        registry.add("spring.data.redis.port", () -> Containers.REDIS.getMappedPort(6379));
        registry.add("spring.kafka.bootstrap-servers", Containers.KAFKA::getBootstrapServers);
        registry.add("spring.kafka.consumer.group-id", () -> group);
    }

    @LocalServerPort
    protected int port;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected TokenSigner tokens;

    @Autowired
    protected ObjectMapper json;

    @Autowired
    protected RedissonClient redisson;

    @Autowired
    protected SimulatedPaymentGateway gateway;

    @BeforeEach
    void resetState() {
        jdbc.update("DELETE FROM payments");
        jdbc.update("DELETE FROM outbox_messages");
        jdbc.update("DELETE FROM booking_attempts");
        jdbc.update("DELETE FROM bookings");
        jdbc.update("""
                UPDATE seat_inventory
                SET status = 'AVAILABLE', booking_id = NULL, hold_expires_at = NULL, updated_at = now()
                """);
        try {
            redisson.getKeys().flushdb();
        } catch (RuntimeException ignored) {
            // Redis-down tests have no server to flush.
        }
    }

    protected HttpResult postBooking(String userId, UUID seatId, String idempotencyKey) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/api/shows/" + SHOW_ID + "/bookings"))
                .header("Authorization", "Bearer " + token(userId))
                .header("Idempotency-Key", idempotencyKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"seatId\":\"" + seatId + "\"}"))
                .build();
        return send(request);
    }

    protected HttpResult getBooking(String userId, String bookingId) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/api/bookings/" + bookingId))
                .header("Authorization", "Bearer " + token(userId))
                .GET()
                .build();
        return send(request);
    }

    protected HttpResult get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(baseUrl() + path)).GET().build();
        return send(request);
    }

    protected List<HttpResult> contend(UUID seatId, int threads, long awaitSeconds) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CyclicBarrier barrier = new CyclicBarrier(threads);
        CountDownLatch done = new CountDownLatch(threads);
        List<HttpResult> results = new CopyOnWriteArrayList<>();
        for (int i = 0; i < threads; i++) {
            int id = i;
            pool.submit(() -> {
                try {
                    barrier.await(awaitSeconds, TimeUnit.SECONDS);
                    results.add(postBooking("buyer-" + id, seatId, "idem-key-" + id));
                } catch (Exception exception) {
                    results.add(new HttpResult(500, exception.getClass().getSimpleName(), null, exception.getMessage()));
                } finally {
                    done.countDown();
                }
            });
        }
        if (!done.await(awaitSeconds, TimeUnit.SECONDS)) {
            pool.shutdownNow();
            throw new AssertionError("Booking threads did not finish");
        }
        pool.shutdownNow();
        return List.copyOf(results);
    }

    protected String inventoryStatus(UUID seatId) {
        return jdbc.queryForObject(
                "SELECT status FROM seat_inventory WHERE show_id = ? AND seat_id = ?",
                String.class,
                SHOW_ID,
                seatId);
    }

    protected int countInventory(UUID seatId, String status) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM seat_inventory WHERE show_id = ? AND seat_id = ? AND status = ?",
                Integer.class,
                SHOW_ID,
                seatId,
                status);
        return count == null ? 0 : count;
    }

    protected int countBookings(UUID seatId, String status) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM bookings WHERE show_id = ? AND seat_id = ? AND status = ?",
                Integer.class,
                SHOW_ID,
                seatId,
                status);
        return count == null ? 0 : count;
    }

    protected int countPayments(String status) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM payments WHERE status = ?",
                Integer.class,
                status);
        return count == null ? 0 : count;
    }

    protected int countAttempts(String userId) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM booking_attempts WHERE user_id = ?",
                Integer.class,
                userId);
        return count == null ? 0 : count;
    }

    private HttpResult send(HttpRequest request) throws Exception {
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        String code = null;
        String bookingId = null;
        String body = response.body() == null ? "" : response.body();
        if (!body.isBlank()) {
            JsonNode node = json.readTree(body);
            if (node.hasNonNull("code")) {
                code = node.get("code").asText();
            }
            if (node.hasNonNull("bookingId")) {
                bookingId = node.get("bookingId").asText();
            }
            if (node.hasNonNull("reason") && code == null) {
                code = node.path("code").asText(null);
            }
        }
        return new HttpResult(response.statusCode(), code, bookingId, body);
    }

    private String token(String userId) {
        return tokens.issue(userId, Instant.now().plus(Duration.ofHours(1)));
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    public record HttpResult(int status, String code, String bookingId, String body) {
    }
}
