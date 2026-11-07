CREATE TABLE notification (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    recipient_id VARCHAR(36) NOT NULL,
    event_type VARCHAR(60) NOT NULL,
    object_id VARCHAR(36) NOT NULL,
    payload TEXT NOT NULL,
    dedupe_key VARCHAR(160) NOT NULL UNIQUE,
    state VARCHAR(16) NOT NULL,
    attempts TINYINT NOT NULL DEFAULT 0,
    last_error VARCHAR(120) NULL,
    next_attempt_at BIGINT NOT NULL,
    created_at BIGINT NOT NULL,
    read_at BIGINT NULL,
    FOREIGN KEY (recipient_id) REFERENCES account(id),
    CHECK (state IN ('PENDING', 'SENT', 'FAILED')),
    CHECK (attempts BETWEEN 0 AND 3),
    INDEX notification_recipient (recipient_id, id),
    INDEX notification_retry (state, next_attempt_at)
);
