ALTER TABLE appointment ADD credential VARCHAR(50) NULL UNIQUE;

CREATE TABLE payment_order (
    id VARCHAR(36) PRIMARY KEY,
    appointment_id VARCHAR(36) NOT NULL UNIQUE,
    amount_minor BIGINT NOT NULL,
    state VARCHAR(16) NOT NULL,
    mode VARCHAR(16) NOT NULL,
    paid_at BIGINT NOT NULL,
    FOREIGN KEY (appointment_id) REFERENCES appointment(id),
    CHECK (amount_minor >= 0),
    CHECK (mode = 'SIMULATED')
);
