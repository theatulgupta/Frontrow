-- READ COMMITTED is the session default and is set on the pool.
-- Double booking is prevented by the conditional status updates below
-- (the row lock plus a re-check of WHERE after the lock) together with
-- the primary key on seat_inventory and the partial unique index on
-- active bookings. SERIALIZABLE is intentionally not used.

CREATE TABLE venues (
    id uuid PRIMARY KEY,
    name text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE shows (
    id uuid PRIMARY KEY,
    venue_id uuid NOT NULL REFERENCES venues (id),
    name text NOT NULL,
    starts_at timestamptz NOT NULL,
    price_cents integer NOT NULL CHECK (price_cents > 0),
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE seats (
    id uuid PRIMARY KEY,
    venue_id uuid NOT NULL REFERENCES venues (id),
    section text NOT NULL,
    row_label text NOT NULL,
    seat_number integer NOT NULL CHECK (seat_number > 0),
    front_row boolean NOT NULL,
    CONSTRAINT seats_location UNIQUE (venue_id, section, row_label, seat_number)
);

CREATE TABLE seat_inventory (
    show_id uuid NOT NULL REFERENCES shows (id),
    seat_id uuid NOT NULL REFERENCES seats (id),
    status text NOT NULL CHECK (status IN ('AVAILABLE', 'HELD', 'SOLD')),
    booking_id uuid,
    hold_expires_at timestamptz,
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (show_id, seat_id),
    CONSTRAINT seat_inventory_status_owner CHECK (
        (status = 'AVAILABLE' AND booking_id IS NULL AND hold_expires_at IS NULL)
        OR (status = 'HELD' AND booking_id IS NOT NULL AND hold_expires_at IS NOT NULL)
        OR (status = 'SOLD' AND booking_id IS NOT NULL AND hold_expires_at IS NULL)
    )
);

CREATE INDEX seat_inventory_held_expiry
    ON seat_inventory (hold_expires_at)
    WHERE status = 'HELD';

CREATE TABLE bookings (
    id uuid PRIMARY KEY,
    show_id uuid NOT NULL REFERENCES shows (id),
    seat_id uuid NOT NULL REFERENCES seats (id),
    user_id text NOT NULL,
    idempotency_key text NOT NULL,
    status text NOT NULL CHECK (status IN ('PENDING_PAYMENT', 'CONFIRMED', 'CANCELLED', 'EXPIRED')),
    amount_cents integer NOT NULL CHECK (amount_cents > 0),
    hold_expires_at timestamptz NOT NULL,
    risk_decision text NOT NULL,
    risk_reason text NOT NULL,
    risk_flagged boolean NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT bookings_user_idempotency UNIQUE (user_id, idempotency_key)
);

-- At most one live booking for a seat, even if an inventory update is skipped.
CREATE UNIQUE INDEX bookings_one_active_per_seat
    ON bookings (show_id, seat_id)
    WHERE status IN ('PENDING_PAYMENT', 'CONFIRMED');

CREATE INDEX bookings_user_status ON bookings (user_id, status);

CREATE TABLE booking_attempts (
    id uuid PRIMARY KEY,
    user_id text NOT NULL,
    show_id uuid NOT NULL,
    seat_id uuid NOT NULL,
    idempotency_key text NOT NULL,
    decision text NOT NULL CHECK (decision IN ('ALLOW', 'CHALLENGE', 'REJECT')),
    reason text NOT NULL,
    flagged boolean NOT NULL,
    user_attempt_count integer NOT NULL,
    seat_attempt_count integer NOT NULL,
    distinct_seat_count integer NOT NULL,
    in_flight_holds integer NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT booking_attempts_user_key UNIQUE (user_id, idempotency_key)
);

CREATE INDEX booking_attempts_user_created ON booking_attempts (user_id, created_at);
CREATE INDEX booking_attempts_seat_created ON booking_attempts (show_id, seat_id, created_at);

CREATE TABLE payments (
    id uuid PRIMARY KEY,
    booking_id uuid NOT NULL UNIQUE REFERENCES bookings (id),
    status text NOT NULL CHECK (status IN ('PENDING', 'SUCCEEDED', 'DECLINED', 'TIMED_OUT', 'VOIDED')),
    attempt_count integer NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    gateway_reference text,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE outbox_messages (
    id uuid PRIMARY KEY,
    event_type text NOT NULL,
    aggregate_id uuid NOT NULL,
    payload jsonb NOT NULL,
    status text NOT NULL CHECK (status IN ('PENDING', 'PUBLISHING', 'PUBLISHED')),
    created_at timestamptz NOT NULL DEFAULT now(),
    publishing_started_at timestamptz,
    published_at timestamptz
);

CREATE INDEX outbox_pending
    ON outbox_messages (created_at)
    WHERE status = 'PENDING';
