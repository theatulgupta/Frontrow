CREATE TABLE patrons (
    user_id text PRIMARY KEY,
    display_name text NOT NULL,
    city text NOT NULL
);

INSERT INTO patrons (user_id, display_name, city) VALUES
    ('mira-chen', 'Mira Chen', 'Brooklyn'),
    ('jonas-hale', 'Jonas Hale', 'Queens'),
    ('leila-okonkwo', 'Leila Okonkwo', 'Harlem'),
    ('samir-patel', 'Samir Patel', 'Jersey City'),
    ('nora-berg', 'Nora Berg', 'Hoboken'),
    ('elio-vargas', 'Elio Vargas', 'Bronx'),
    ('hana-ito', 'Hana Ito', 'Manhattan'),
    ('ruth-adeyemi', 'Ruth Adeyemi', 'Brooklyn'),
    ('caleb-nguyen', 'Caleb Nguyen', 'Astoria'),
    ('ines-moreau', 'Inès Moreau', 'Paris'),
    ('omar-farouk', 'Omar Farouk', 'Cairo'),
    ('priya-nair', 'Priya Nair', 'Jersey City'),
    ('theo-march', 'Theo March', 'London'),
    ('aya-sato', 'Aya Sato', 'Osaka'),
    ('felix-duran', 'Felix Durán', 'Madrid'),
    ('noor-hassan', 'Noor Hassan', 'Amman'),
    ('lila-brooks', 'Lila Brooks', 'Boston'),
    ('ivan-petrov', 'Ivan Petrov', 'Sofia'),
    ('maya-singh', 'Maya Singh', 'Queens'),
    ('oscar-lind', 'Oscar Lind', 'Stockholm'),
    ('chloe-martin', 'Chloé Martin', 'Lyon'),
    ('kenji-watanabe', 'Kenji Watanabe', 'Tokyo'),
    ('amira-said', 'Amira Said', 'Alexandria'),
    ('diego-alvarez', 'Diego Alvarez', 'Mexico City'),
    ('freya-nilsen', 'Freya Nilsen', 'Oslo'),
    ('hassan-ali', 'Hassan Ali', 'Dubai'),
    ('julia-kowal', 'Julia Kowal', 'Krakow'),
    ('leo-rossi', 'Leo Rossi', 'Rome'),
    ('nadia-karim', 'Nadia Karim', 'Beirut'),
    ('paulo-mendes', 'Paulo Mendes', 'Lisbon'),
    ('quinn-harper', 'Quinn Harper', 'Chicago'),
    ('rosa-delgado', 'Rosa Delgado', 'Barcelona'),
    ('soren-ahl', 'Soren Ahl', 'Copenhagen'),
    ('tara-mehta', 'Tara Mehta', 'Mumbai'),
    ('umar-diallo', 'Umar Diallo', 'Dakar'),
    ('vera-novak', 'Vera Novak', 'Prague'),
    ('wesley-park', 'Wesley Park', 'Seoul'),
    ('yasmin-haddad', 'Yasmin Haddad', 'Beirut'),
    ('zara-okoye', 'Zara Okoye', 'Lagos'),
    ('adrian-cole', 'Adrian Cole', 'Austin');

INSERT INTO seats (id, venue_id, section, row_label, seat_number, front_row)
SELECT
    md5('frontrow-seat-' || row_label || '-' || seat_number::text)::uuid,
    '11111111-1111-1111-1111-111111111111',
    'ORCHESTRA',
    row_label,
    seat_number,
    row_label = 'A'
FROM (
    SELECT chr(row_code) AS row_label, seat_number
    FROM generate_series(65, 70) AS row_code
    CROSS JOIN generate_series(1, 12) AS seat_number
) grid
ON CONFLICT ON CONSTRAINT seats_location DO NOTHING;

INSERT INTO shows (id, venue_id, name, starts_at, price_cents) VALUES
    ('55555555-5555-5555-5555-555555555501', '11111111-1111-1111-1111-111111111111', 'Late Set', now() + interval '2 days', 9500),
    ('55555555-5555-5555-5555-555555555502', '11111111-1111-1111-1111-111111111111', 'Sunday Matinee', now() + interval '9 days', 7500)
ON CONFLICT (id) DO NOTHING;

INSERT INTO seat_inventory (show_id, seat_id, status, booking_id, hold_expires_at)
SELECT shows.id, seats.id, 'AVAILABLE', NULL, NULL
FROM shows
CROSS JOIN seats
ON CONFLICT (show_id, seat_id) DO NOTHING;

WITH open_seats AS (
    SELECT
        seats.id AS seat_id,
        row_number() OVER (ORDER BY seats.row_label, seats.seat_number) AS n
    FROM seats
    WHERE seats.row_label IN ('A', 'B', 'C', 'D')
      AND NOT (seats.row_label = 'A' AND seats.seat_number >= 10)
      AND NOT EXISTS (
          SELECT 1
          FROM bookings
          WHERE bookings.show_id = '22222222-2222-2222-2222-222222222222'
            AND bookings.seat_id = seats.id
            AND bookings.status IN ('PENDING_PAYMENT', 'CONFIRMED')
      )
),
numbered_patrons AS (
    SELECT user_id, row_number() OVER (ORDER BY display_name) AS n, count(*) OVER () AS total
    FROM patrons
),
created AS (
    INSERT INTO bookings (
        id, show_id, seat_id, user_id, idempotency_key, status, amount_cents,
        hold_expires_at, risk_decision, risk_reason, risk_flagged, created_at
    )
    SELECT
        md5('opening-booking-' || open_seats.seat_id::text)::uuid,
        '22222222-2222-2222-2222-222222222222',
        open_seats.seat_id,
        numbered_patrons.user_id,
        'seed-opening-' || open_seats.seat_id::text,
        'CONFIRMED',
        15000,
        now() + interval '30 days',
        'ALLOW',
        'OK',
        false,
        now() - (open_seats.n || ' minutes')::interval
    FROM open_seats
    JOIN numbered_patrons
      ON numbered_patrons.n = ((open_seats.n - 1) % numbered_patrons.total) + 1
    RETURNING id, seat_id
)
UPDATE seat_inventory
SET status = 'SOLD',
    booking_id = created.id,
    hold_expires_at = NULL,
    updated_at = now()
FROM created
WHERE seat_inventory.show_id = '22222222-2222-2222-2222-222222222222'
  AND seat_inventory.seat_id = created.seat_id
  AND seat_inventory.status = 'AVAILABLE';

INSERT INTO payments (id, booking_id, status, attempt_count, gateway_reference)
SELECT md5('opening-payment-' || bookings.id::text)::uuid, bookings.id, 'SUCCEEDED', 1, 'sim-' || bookings.id::text
FROM bookings
WHERE bookings.idempotency_key LIKE 'seed-opening-%'
ON CONFLICT (booking_id) DO NOTHING;

WITH hold_seats AS (
    SELECT seats.id AS seat_id, row_number() OVER (ORDER BY seats.seat_number) AS n
    FROM seats
    WHERE seats.row_label = 'E' AND seats.seat_number IN (1, 2)
      AND NOT EXISTS (
          SELECT 1 FROM bookings
          WHERE bookings.show_id = '22222222-2222-2222-2222-222222222222'
            AND bookings.seat_id = seats.id
            AND bookings.status IN ('PENDING_PAYMENT', 'CONFIRMED')
      )
),
held AS (
    INSERT INTO bookings (
        id, show_id, seat_id, user_id, idempotency_key, status, amount_cents,
        hold_expires_at, risk_decision, risk_reason, risk_flagged
    )
    SELECT
        md5('opening-hold-' || hold_seats.seat_id::text)::uuid,
        '22222222-2222-2222-2222-222222222222',
        hold_seats.seat_id,
        CASE WHEN hold_seats.n = 1 THEN 'wesley-park' ELSE 'yasmin-haddad' END,
        'seed-hold-' || hold_seats.seat_id::text,
        'PENDING_PAYMENT',
        15000,
        now() + interval '45 minutes',
        'ALLOW',
        'OK',
        false
    FROM hold_seats
    RETURNING id, seat_id, hold_expires_at
)
UPDATE seat_inventory
SET status = 'HELD',
    booking_id = held.id,
    hold_expires_at = held.hold_expires_at,
    updated_at = now()
FROM held
WHERE seat_inventory.show_id = '22222222-2222-2222-2222-222222222222'
  AND seat_inventory.seat_id = held.seat_id
  AND seat_inventory.status = 'AVAILABLE';
