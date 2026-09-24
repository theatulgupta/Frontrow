CREATE TABLE places (
    code text NOT NULL,
    city text NOT NULL,
    kind text NOT NULL CHECK (kind IN ('AIRPORT', 'STATION', 'BUS_STOP', 'CITY')),
    PRIMARY KEY (code, kind)
);

INSERT INTO places (code, city, kind) VALUES
    ('DEL', 'Delhi', 'AIRPORT'), ('BOM', 'Mumbai', 'AIRPORT'), ('BLR', 'Bengaluru', 'AIRPORT'), ('JAI', 'Jaipur', 'AIRPORT'),
    ('MAA', 'Chennai', 'AIRPORT'), ('CCU', 'Kolkata', 'AIRPORT'), ('HYD', 'Hyderabad', 'AIRPORT'), ('GOI', 'Goa', 'AIRPORT'),
    ('DEL', 'Delhi', 'STATION'), ('BOM', 'Mumbai', 'STATION'), ('BLR', 'Bengaluru', 'STATION'), ('JAI', 'Jaipur', 'STATION'),
    ('DEL', 'Delhi', 'BUS_STOP'), ('BOM', 'Mumbai', 'BUS_STOP'), ('BLR', 'Bengaluru', 'BUS_STOP'), ('JAI', 'Jaipur', 'BUS_STOP'),
    ('DEL', 'Delhi', 'CITY'), ('BOM', 'Mumbai', 'CITY'), ('BLR', 'Bengaluru', 'CITY'), ('JAI', 'Jaipur', 'CITY'),
    ('MAA', 'Chennai', 'CITY'), ('CCU', 'Kolkata', 'CITY'), ('HYD', 'Hyderabad', 'CITY'), ('GOI', 'Goa', 'CITY'),
    ('AMD', 'Ahmedabad', 'AIRPORT'), ('AMD', 'Ahmedabad', 'CITY'),
    ('COK', 'Kochi', 'AIRPORT'), ('COK', 'Kochi', 'CITY'),
    ('PNQ', 'Pune', 'AIRPORT'), ('PNQ', 'Pune', 'CITY'),
    ('LKO', 'Lucknow', 'AIRPORT'), ('LKO', 'Lucknow', 'CITY');

CREATE TABLE flight_offers (
    id uuid PRIMARY KEY,
    airline text NOT NULL,
    flight_number text NOT NULL,
    origin_code text NOT NULL,
    destination_code text NOT NULL,
    depart_at timestamptz NOT NULL,
    arrive_at timestamptz NOT NULL,
    stops integer NOT NULL CHECK (stops >= 0),
    cabin text NOT NULL,
    price_cents integer NOT NULL CHECK (price_cents > 0),
    capacity integer NOT NULL CHECK (capacity > 0),
    seats_held integer NOT NULL DEFAULT 0 CHECK (seats_held >= 0),
    seats_sold integer NOT NULL DEFAULT 0 CHECK (seats_sold >= 0),
    CONSTRAINT flight_offers_capacity CHECK (seats_held + seats_sold <= capacity),
    CONSTRAINT flight_offers_route CHECK (origin_code <> destination_code)
);

CREATE TABLE flight_bookings (
    id uuid PRIMARY KEY,
    offer_id uuid NOT NULL REFERENCES flight_offers (id),
    user_id text NOT NULL,
    idempotency_key text NOT NULL,
    status text NOT NULL CHECK (status IN ('PENDING_PAYMENT', 'CONFIRMED', 'CANCELLED', 'EXPIRED')),
    amount_cents integer NOT NULL CHECK (amount_cents > 0),
    hold_expires_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT flight_bookings_user_key UNIQUE (user_id, idempotency_key)
);

CREATE INDEX flight_bookings_offer_status ON flight_bookings (offer_id, status);

CREATE TABLE flight_payments (
    id uuid PRIMARY KEY,
    booking_id uuid NOT NULL UNIQUE REFERENCES flight_bookings (id),
    status text NOT NULL CHECK (status IN ('PENDING', 'SUCCEEDED', 'DECLINED', 'TIMED_OUT', 'VOIDED')),
    attempt_count integer NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    gateway_reference text,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE train_trips (
    id uuid PRIMARY KEY,
    train_number text NOT NULL,
    name text NOT NULL,
    origin_code text NOT NULL,
    destination_code text NOT NULL,
    depart_at timestamptz NOT NULL,
    arrive_at timestamptz NOT NULL,
    class_name text NOT NULL,
    price_cents integer NOT NULL CHECK (price_cents > 0)
);

CREATE TABLE bus_trips (
    id uuid PRIMARY KEY,
    operator_name text NOT NULL,
    origin_code text NOT NULL,
    destination_code text NOT NULL,
    depart_at timestamptz NOT NULL,
    arrive_at timestamptz NOT NULL,
    coach text NOT NULL,
    price_cents integer NOT NULL CHECK (price_cents > 0)
);

CREATE TABLE hotels (
    id uuid PRIMARY KEY,
    name text NOT NULL,
    city text NOT NULL,
    nightly_rate_cents integer NOT NULL CHECK (nightly_rate_cents > 0),
    stars integer NOT NULL CHECK (stars BETWEEN 1 AND 5)
);

INSERT INTO flight_offers (
    id, airline, flight_number, origin_code, destination_code, depart_at, arrive_at,
    stops, cabin, price_cents, capacity, seats_held, seats_sold
)
SELECT
    md5('flight-offer-' || i)::uuid,
    (ARRAY['IndiGo', 'Air India', 'Vistara', 'Akasa Air'])[1 + (i % 4)],
    (ARRAY['6E', 'AI', 'UK', 'QP'])[1 + (i % 4)] || (200 + i),
    (ARRAY['DEL', 'BOM', 'BLR', 'JAI'])[1 + (i % 4)],
    (ARRAY['BOM', 'BLR', 'JAI', 'DEL'])[1 + (i % 4)],
    (CURRENT_DATE + (i % 10))::timestamp + ((6 + (i % 12)) || ' hours')::interval,
    (CURRENT_DATE + (i % 10))::timestamp + ((8 + (i % 12)) || ' hours')::interval,
    i % 2,
    (ARRAY['ECONOMY', 'ECONOMY', 'PREMIUM'])[1 + (i % 3)],
    420000 + (i * 12500),
    24 + (i % 40),
    0,
    0
FROM generate_series(1, 36) AS i;

INSERT INTO flight_offers (
    id, airline, flight_number, origin_code, destination_code, depart_at, arrive_at,
    stops, cabin, price_cents, capacity, seats_held, seats_sold
) VALUES (
    '66666666-6666-6666-6666-666666666601',
    'IndiGo',
    '6E901',
    'DEL',
    'BOM',
    (CURRENT_DATE + 1)::timestamp + interval '9 hours',
    (CURRENT_DATE + 1)::timestamp + interval '11 hours 15 minutes',
    0,
    'ECONOMY',
    399900,
    1,
    0,
    0
);

INSERT INTO train_trips (id, train_number, name, origin_code, destination_code, depart_at, arrive_at, class_name, price_cents)
SELECT
    md5('train-' || i)::uuid,
    '12' || lpad(i::text, 3, '0'),
    (ARRAY['Rajdhani Express', 'Shatabdi Express', 'Duronto Express', 'Garib Rath'])[1 + (i % 4)],
    (ARRAY['DEL', 'BOM', 'BLR', 'JAI'])[1 + (i % 4)],
    (ARRAY['BOM', 'BLR', 'JAI', 'DEL'])[1 + (i % 4)],
    (CURRENT_DATE + (i % 8))::timestamp + ((7 + i % 6) || ' hours')::interval,
    (CURRENT_DATE + (i % 8) + 1)::timestamp + ((6 + i % 5) || ' hours')::interval,
    (ARRAY['3A', '2A', 'SL', 'CC'])[1 + (i % 4)],
    180000 + (i * 9000)
FROM generate_series(1, 15) AS i;

INSERT INTO bus_trips (id, operator_name, origin_code, destination_code, depart_at, arrive_at, coach, price_cents)
SELECT
    md5('bus-' || i)::uuid,
    (ARRAY['Zingbus', 'IntrCity', 'NueGo', 'VRL'])[1 + (i % 4)],
    (ARRAY['DEL', 'BOM', 'BLR', 'JAI'])[1 + (i % 4)],
    (ARRAY['JAI', 'DEL', 'BOM', 'BLR'])[1 + (i % 4)],
    (CURRENT_DATE + (i % 6))::timestamp + ((18 + i % 4) || ' hours')::interval,
    (CURRENT_DATE + (i % 6) + 1)::timestamp + ((6 + i % 3) || ' hours')::interval,
    (ARRAY['Seater', 'Sleeper', 'AC Sleeper'])[1 + (i % 3)],
    90000 + (i * 4500)
FROM generate_series(1, 15) AS i;

INSERT INTO hotels (id, name, city, nightly_rate_cents, stars) VALUES
    (md5('hotel-1')::uuid, 'Haveli House', 'Jaipur', 640000, 4),
    (md5('hotel-2')::uuid, 'Amber Courtyard', 'Jaipur', 410000, 3),
    (md5('hotel-3')::uuid, 'Marine View', 'Mumbai', 980000, 5),
    (md5('hotel-4')::uuid, 'Colaba Rooms', 'Mumbai', 520000, 3),
    (md5('hotel-5')::uuid, 'Bandra Stay', 'Mumbai', 730000, 4),
    (md5('hotel-6')::uuid, 'Indiranagar Lodge', 'Bengaluru', 480000, 3),
    (md5('hotel-7')::uuid, 'Cubbon Suites', 'Bengaluru', 860000, 5),
    (md5('hotel-8')::uuid, 'Koramangala House', 'Bengaluru', 390000, 3),
    (md5('hotel-9')::uuid, 'Connaught Residency', 'Delhi', 910000, 5),
    (md5('hotel-10')::uuid, 'Hauz Khas House', 'Delhi', 560000, 4),
    (md5('hotel-11')::uuid, 'Karol Bagh Inn', 'Delhi', 280000, 2),
    (md5('hotel-12')::uuid, 'Pink City Palace', 'Jaipur', 1200000, 5);
