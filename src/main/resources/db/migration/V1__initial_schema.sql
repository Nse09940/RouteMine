CREATE TABLE couriers (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    max_weight_grams BIGINT NOT NULL CHECK (max_weight_grams > 0),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE users (
    id UUID PRIMARY KEY,
    username VARCHAR(80) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL CHECK (role IN ('DISPATCHER', 'COURIER')),
    courier_id UUID UNIQUE REFERENCES couriers(id),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE shifts (
    id UUID PRIMARY KEY,
    courier_id UUID NOT NULL REFERENCES couriers(id),
    started_at TIMESTAMPTZ NOT NULL,
    ended_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX ux_shifts_open_courier
    ON shifts(courier_id)
    WHERE ended_at IS NULL;

CREATE TABLE route_plans (
    id UUID PRIMARY KEY,
    courier_id UUID NOT NULL REFERENCES couriers(id),
    status VARCHAR(20) NOT NULL CHECK (status IN ('DRAFT', 'CONFIRMED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    start_latitude DOUBLE PRECISION NOT NULL,
    start_longitude DOUBLE PRECISION NOT NULL,
    total_distance_meters BIGINT NOT NULL CHECK (total_distance_meters >= 0),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    confirmed_at TIMESTAMPTZ,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX ux_route_plans_active_courier
    ON route_plans(courier_id)
    WHERE status IN ('CONFIRMED', 'IN_PROGRESS');

CREATE TABLE deliveries (
    id UUID PRIMARY KEY,
    description VARCHAR(500) NOT NULL,
    weight_grams BIGINT NOT NULL CHECK (weight_grams > 0),
    recipient_name VARCHAR(120) NOT NULL,
    recipient_phone VARCHAR(40) NOT NULL,
    confirmation_code_hash VARCHAR(100) NOT NULL,
    pickup_latitude DOUBLE PRECISION NOT NULL,
    pickup_longitude DOUBLE PRECISION NOT NULL,
    dropoff_latitude DOUBLE PRECISION NOT NULL,
    dropoff_longitude DOUBLE PRECISION NOT NULL,
    status VARCHAR(30) NOT NULL CHECK (status IN (
        'CREATED', 'ASSIGNED', 'PICKED_UP', 'IN_TRANSIT', 'DELIVERED',
        'CANCELLED', 'PICKUP_FAILED', 'DELIVERY_FAILED'
    )),
    assigned_courier_id UUID REFERENCES couriers(id),
    route_plan_id UUID REFERENCES route_plans(id),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_deliveries_status ON deliveries(status);
CREATE INDEX ix_deliveries_courier ON deliveries(assigned_courier_id);

CREATE TABLE route_stops (
    id UUID PRIMARY KEY,
    route_plan_id UUID NOT NULL REFERENCES route_plans(id) ON DELETE CASCADE,
    delivery_id UUID NOT NULL REFERENCES deliveries(id),
    stop_type VARCHAR(20) NOT NULL CHECK (stop_type IN ('PICKUP', 'DROPOFF')),
    position INTEGER NOT NULL CHECK (position > 0),
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    distance_from_previous_meters BIGINT NOT NULL CHECK (distance_from_previous_meters >= 0),
    UNIQUE (route_plan_id, position),
    UNIQUE (route_plan_id, delivery_id, stop_type)
);

CREATE TABLE delivery_events (
    id BIGSERIAL PRIMARY KEY,
    delivery_id UUID NOT NULL REFERENCES deliveries(id) ON DELETE CASCADE,
    from_status VARCHAR(30),
    to_status VARCHAR(30) NOT NULL,
    actor_user_id UUID REFERENCES users(id),
    occurred_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_delivery_events_delivery ON delivery_events(delivery_id, occurred_at);

