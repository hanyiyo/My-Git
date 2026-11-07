CREATE TABLE medical_reviewer_permission (
    account_id VARCHAR(36) PRIMARY KEY,
    granted_by VARCHAR(36) NOT NULL,
    enabled BOOLEAN NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    updated_at BIGINT NOT NULL,
    FOREIGN KEY (account_id) REFERENCES account(id),
    FOREIGN KEY (granted_by) REFERENCES account(id)
);
-- Legacy self-granted cases do not acquire the new independent capability.
UPDATE medical_review_case SET state='REVOKED' WHERE state='GRANTED';
ALTER TABLE medical_content_flag ADD COLUMN sequence_no BIGINT NOT NULL AUTO_INCREMENT UNIQUE;
CREATE TABLE audit_chain_head (
    id INT PRIMARY KEY,
    sequence_no BIGINT NOT NULL,
    signature CHAR(128) NOT NULL,
    legacy_unsigned_events BIGINT NOT NULL
);
INSERT INTO audit_chain_head SELECT 1, 0, REPEAT('0',128), COUNT(*) FROM audit_event;
CREATE TABLE audit_signature (
    sequence_no BIGINT PRIMARY KEY,
    event_id VARCHAR(36) NOT NULL UNIQUE,
    previous_signature CHAR(128) NOT NULL,
    signature CHAR(128) NOT NULL,
    FOREIGN KEY (event_id) REFERENCES audit_event(id)
);
