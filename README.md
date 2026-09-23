# Frontrow

Frontrow is a single Spring Boot service for seated ticket sales. A buyer asks for one seat. Redis locks that seat only. PostgreSQL's conditional update is what makes a double booking impossible. Kafka carries the payment after the hold commits.

## Tests

Tests start PostgreSQL, Redis, and Kafka with Testcontainers. Docker must be running. No API key is required.

```bash
./gradlew test
./gradlew bootJar
```

`./gradlew test` skips the 200-thread load test. Run it with:

```bash
./gradlew test -PincludeLoadTests
```

Two hundred threads booking seat A1 must still produce exactly one `SOLD` ticket.

## Run locally

Start Postgres, Redis, and Kafka, then the app:

```bash
docker compose up -d --wait
./gradlew bootRun --args='--spring.profiles.active=local'
```

`bootRun` expects Compose. The local profile turns on `POST /api/dev/tokens`.

```bash
curl -s -X POST http://localhost:8080/api/dev/tokens \
  -H 'Content-Type: application/json' \
  -d '{"userId":"ada"}'
```

Book a seat with that bearer token and an `Idempotency-Key` header. `GET /api/shows` lists the seeded Opening Night show, including front-row seats `A1`–`A8`.

## Environment

| Variable | Default |
| --- | --- |
| `FRONTROW_TOKEN_SECRET` | `dev-only-change-me` |
| `FRONTROW_HOLD_TTL` | `PT2M` |
| `FRONTROW_LOCK_WAIT` | `PT0.2S` |
| `FRONTROW_LOCK_LEASE` | `PT10S` |
| `FRONTROW_PAYMENT_MODE` | `SUCCESS` (`DECLINE` or `TIMEOUT`) |
| `FRONTROW_PAYMENT_LATENCY_MS` | `50` |
| `FRONTROW_PAYMENT_TIMEOUT_MS` | `200` |
| `FRONTROW_PAYMENT_MAX_ATTEMPTS` | `3` |
| `FRONTROW_RISK_PROVIDER` | `local` (`openai` optional) |
| `FRONTROW_RISK_TIMEOUT_MS` | `150` |
| `FRONTROW_RISK_WINDOW` | `PT60S` |
| `FRONTROW_AI_API_KEY` | unset |
| `FRONTROW_AI_MODEL` | `gpt-4o-mini` |

Leave `FRONTROW_AI_API_KEY` unset to use the in-process scorer. Ticket sales still proceed when the scorer times out: the attempt is allowed and flagged.

## What is guaranteed

A seat is `AVAILABLE`, `HELD`, or `SOLD`. The inventory primary key is `(show_id, seat_id)`. The hold, the booking, and the outbox row commit in one transaction. Payment and hold expiry both update that row only when `booking_id` still matches, so a retry cannot sell or charge the seat twice.
