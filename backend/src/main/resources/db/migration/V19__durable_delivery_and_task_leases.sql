ALTER TABLE notification ADD COLUMN cursor_no BIGINT NULL UNIQUE,
    ADD COLUMN lease_token VARCHAR(36) NULL,
    ADD COLUMN lease_expires_at BIGINT NULL;
UPDATE notification SET cursor_no=id;
CREATE TABLE notification_cursor (id INT PRIMARY KEY, last_cursor BIGINT NOT NULL);
INSERT INTO notification_cursor SELECT 1, COALESCE(MAX(id),0) FROM notification;

ALTER TABLE ai_task ADD COLUMN lease_token VARCHAR(36) NULL,
    ADD COLUMN lease_expires_at BIGINT NULL,
    ADD COLUMN next_attempt_at BIGINT NOT NULL DEFAULT 0;
CREATE UNIQUE INDEX ai_result_one_per_task ON ai_result(task_id);

CREATE TABLE runtime_task (
    id VARCHAR(80) PRIMARY KEY,
    task_type VARCHAR(32) NOT NULL,
    object_id VARCHAR(36) NOT NULL,
    state VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at BIGINT NOT NULL,
    lease_token VARCHAR(36) NULL,
    lease_expires_at BIGINT NULL,
    last_error VARCHAR(120) NULL,
    CHECK (state IN ('PENDING','RUNNING','SUCCEEDED','FAILED')),
    CHECK (attempts BETWEEN 0 AND 3),
    INDEX runtime_task_due(state,next_attempt_at,lease_expires_at)
);
INSERT INTO runtime_task (id,task_type,object_id,next_attempt_at)
SELECT CONCAT('HOLD_EXPIRY:',id),'HOLD_EXPIRY',id,hold_expires_at FROM appointment WHERE state='PENDING_PAYMENT';
INSERT INTO runtime_task (id,task_type,object_id,next_attempt_at)
SELECT CONCAT('SUBSTITUTION_EXPIRY:',id),'SUBSTITUTION_EXPIRY',id,deadline_at FROM substitution_choice WHERE state='PENDING';
INSERT INTO runtime_task (id,task_type,object_id,next_attempt_at)
SELECT CONCAT('CHECK_IN_REMINDER:',id),'CHECK_IN_REMINDER',id,starts_at-1800000 FROM appointment WHERE state='CONFIRMED';
