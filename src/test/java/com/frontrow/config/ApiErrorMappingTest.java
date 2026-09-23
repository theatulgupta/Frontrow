package com.frontrow.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.frontrow.support.IntegrationTestBase;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ApiErrorMappingTest extends IntegrationTestBase {

    @Test
    void unknownRouteIsNotFound() throws Exception {
        assertThat(get("/api/does-not-exist").status()).isEqualTo(404);
    }

    @Test
    void malformedShowIdIsValidation() throws Exception {
        HttpResult result = get("/api/shows/not-a-uuid");
        assertThat(result.status()).isEqualTo(400);
        assertThat(result.code()).isEqualTo("VALIDATION");
    }

    @Test
    void missingIdempotencyKeyIsValidation() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/shows/" + SHOW_ID + "/bookings"))
                .header("Authorization", "Bearer " + tokens.issue("ada", Instant.now().plus(Duration.ofHours(1))))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"seatId\":\"" + SEAT_A1 + "\"}"))
                .build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("VALIDATION");
    }
}
