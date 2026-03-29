-- ─────────────────────────────────────────────────────────────────────────────
-- V1: Create orders schema
--
-- LEARNING POINT — Database-per-service:
--   This is a SEPARATE database (orderdb) from userdb.
--   order-service never queries userdb directly.
--   User data needed in orders (email) is denormalized as a snapshot.
-- ─────────────────────────────────────────────────────────────────────────────

CREATE EXTENSION IF NOT EXISTS "pgcrypto";  -- for gen_random_uuid()

-- ── orders ───────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS orders (
    id                 UUID         NOT NULL DEFAULT gen_random_uuid()  PRIMARY KEY,
    order_number       VARCHAR(30)  NOT NULL UNIQUE,
    user_id            UUID         NOT NULL,
    user_email         VARCHAR(255) NOT NULL,

    -- Embedded shipping address
    street_address     VARCHAR(255),
    city               VARCHAR(100),
    state              VARCHAR(100),
    postal_code        VARCHAR(20),
    country            VARCHAR(100),

    -- Financial snapshot (calculated at order time, never changes)
    subtotal           NUMERIC(12,2) NOT NULL,
    shipping_cost      NUMERIC(10,2) NOT NULL DEFAULT 0.00,
    tax_amount         NUMERIC(10,2) NOT NULL DEFAULT 0.00,
    total_amount       NUMERIC(12,2) NOT NULL,
    currency           VARCHAR(3)    NOT NULL DEFAULT 'USD',

    -- State
    status             VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    payment_id         VARCHAR(100),
    cancellation_reason VARCHAR(500),
    notes              VARCHAR(500),

    -- Timestamps
    created_at         TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMP    NOT NULL DEFAULT NOW(),
    confirmed_at       TIMESTAMP,
    shipped_at         TIMESTAMP,
    delivered_at       TIMESTAMP,

    CONSTRAINT chk_orders_status CHECK (
        status IN ('PENDING','CONFIRMED','SHIPPED','DELIVERED','CANCELLED','REFUNDED'))
);

-- ── order_items ───────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS order_items (
    id           UUID          NOT NULL DEFAULT gen_random_uuid()  PRIMARY KEY,
    order_id     UUID          NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id   VARCHAR(50)   NOT NULL,

    -- Price + name snapshot (immutable — records what customer actually paid)
    product_name VARCHAR(200)  NOT NULL,
    product_sku  VARCHAR(100),
    quantity     INTEGER       NOT NULL CHECK (quantity > 0),
    unit_price   NUMERIC(12,2) NOT NULL CHECK (unit_price >= 0),
    total_price  NUMERIC(12,2) NOT NULL CHECK (total_price >= 0)
);

-- ── Indexes ───────────────────────────────────────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_orders_user_id  ON orders (user_id);
CREATE INDEX IF NOT EXISTS idx_orders_status   ON orders (status);
CREATE INDEX IF NOT EXISTS idx_orders_created  ON orders (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_order_items_order ON order_items (order_id);

-- ── Auto-update updated_at trigger ───────────────────────────────────────────
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ language 'plpgsql';

DROP TRIGGER IF EXISTS update_orders_updated_at ON orders;
CREATE TRIGGER update_orders_updated_at
    BEFORE UPDATE ON orders
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
