-- V1: payments schema (MySQL)
-- UNIQUE on idempotency_key is the foundation of the idempotency pattern

CREATE TABLE IF NOT EXISTS payments (
    id                VARCHAR(36)     NOT NULL PRIMARY KEY,
    idempotency_key   VARCHAR(36)     NOT NULL UNIQUE COMMENT 'orderId — prevents double-charge on retry',
    order_id          VARCHAR(36)     NOT NULL,
    user_id           VARCHAR(36)     NOT NULL,
    user_email        VARCHAR(255)    NOT NULL,
    amount            DECIMAL(12,2)   NOT NULL,
    currency          VARCHAR(3)      NOT NULL DEFAULT 'USD',
    status            VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    payment_method    VARCHAR(30)     NOT NULL DEFAULT 'CARD',
    transaction_id    VARCHAR(100),
    gateway_response  TEXT,
    failure_reason    VARCHAR(500),
    retry_count       INT             NOT NULL DEFAULT 0,
    created_at        DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at        DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    processed_at      DATETIME(6),

    CONSTRAINT chk_payment_status CHECK (status IN ('PENDING','SUCCESS','FAILED','REFUNDED')),
    CONSTRAINT chk_payment_method CHECK (payment_method IN ('CARD','UPI','NET_BANKING','WALLET'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_payments_order_id   ON payments (order_id);
CREATE INDEX idx_payments_user_id    ON payments (user_id);
CREATE INDEX idx_payments_status     ON payments (status);
CREATE INDEX idx_payments_created_at ON payments (created_at DESC);
