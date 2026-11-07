CREATE TABLE medical_review_case (
    id VARCHAR(36) PRIMARY KEY,
    appointment_id VARCHAR(36) NOT NULL,
    requester_id VARCHAR(36) NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    scope VARCHAR(40) NOT NULL,
    state VARCHAR(16) NOT NULL,
    created_at BIGINT NOT NULL,
    expires_at BIGINT NOT NULL,
    FOREIGN KEY (appointment_id) REFERENCES appointment(id),
    FOREIGN KEY (requester_id) REFERENCES account(id),
    CHECK (state IN ('GRANTED', 'REVOKED', 'EXPIRED')),
    INDEX review_requester (requester_id, state, expires_at)
);

CREATE TABLE medical_content_flag (
    id VARCHAR(36) PRIMARY KEY,
    appointment_id VARCHAR(36) NOT NULL,
    target_type VARCHAR(24) NOT NULL,
    target_id VARCHAR(36) NOT NULL,
    marked_by VARCHAR(36) NOT NULL,
    state VARCHAR(16) NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    created_at BIGINT NOT NULL,
    FOREIGN KEY (appointment_id) REFERENCES appointment(id),
    FOREIGN KEY (marked_by) REFERENCES account(id),
    CHECK (state IN ('LIMITED', 'CLEARED')),
    INDEX content_flag_target (target_type, target_id, created_at)
);
