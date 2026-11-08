ALTER TABLE refund
    ADD attempts INT NOT NULL DEFAULT 0,
    ADD next_attempt_at BIGINT NULL,
    ADD last_error VARCHAR(500) NULL,
    ADD provider_reference VARCHAR(200) NULL,
    ADD attempt_token VARCHAR(36) NULL,
    ADD lease_expires_at BIGINT NULL,
    ADD INDEX refund_due (state, next_attempt_at),
    ADD INDEX refund_lease (state, lease_expires_at),
    ADD CHECK (attempts BETWEEN 0 AND 3);
UPDATE refund SET next_attempt_at=created_at WHERE state='REFUND_PENDING';

CREATE TABLE refund_attempt (
    token VARCHAR(36) PRIMARY KEY,
    refund_id VARCHAR(36) NOT NULL,
    attempt_number INT NOT NULL,
    started_at BIGINT NOT NULL,
    finished_at BIGINT NULL,
    state VARCHAR(16) NOT NULL,
    error VARCHAR(500) NULL,
    provider_reference VARCHAR(200) NULL,
    UNIQUE (refund_id, attempt_number),
    FOREIGN KEY (refund_id) REFERENCES refund(id),
    CHECK (attempt_number BETWEEN 1 AND 3)
);
