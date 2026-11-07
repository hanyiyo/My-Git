ALTER TABLE appointment ADD cancellation_reason VARCHAR(40) NULL, ADD cancelled_at BIGINT NULL;

CREATE TABLE refund (
    id VARCHAR(36) PRIMARY KEY,
    payment_id VARCHAR(36) NOT NULL UNIQUE,
    amount_minor BIGINT NOT NULL,
    reason VARCHAR(40) NOT NULL,
    state VARCHAR(20) NOT NULL,
    created_at BIGINT NOT NULL,
    FOREIGN KEY (payment_id) REFERENCES payment_order(id),
    CHECK (amount_minor >= 0)
);
