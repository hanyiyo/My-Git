CREATE TABLE ai_consent (
    id VARCHAR(36) PRIMARY KEY,
    appointment_id VARCHAR(36) NOT NULL,
    patient_id VARCHAR(36) NOT NULL,
    provider VARCHAR(40) NOT NULL,
    purpose VARCHAR(60) NOT NULL,
    scope VARCHAR(120) NOT NULL,
    accepted BOOLEAN NOT NULL,
    created_at BIGINT NOT NULL,
    revoked_at BIGINT NULL,
    FOREIGN KEY (appointment_id) REFERENCES appointment(id),
    FOREIGN KEY (patient_id) REFERENCES account(id),
    INDEX ai_consent_appointment (appointment_id, created_at)
);

CREATE TABLE ai_task (
    id VARCHAR(36) PRIMARY KEY,
    appointment_id VARCHAR(36) NOT NULL,
    patient_id VARCHAR(36) NOT NULL,
    material_id VARCHAR(36) NOT NULL,
    consent_id VARCHAR(36) NOT NULL,
    idempotency_key VARCHAR(80) NOT NULL,
    provider VARCHAR(40) NOT NULL,
    purpose VARCHAR(60) NOT NULL,
    state VARCHAR(16) NOT NULL,
    attempts TINYINT NOT NULL DEFAULT 0,
    error_code VARCHAR(80) NULL,
    created_at BIGINT NOT NULL,
    finished_at BIGINT NULL,
    FOREIGN KEY (appointment_id) REFERENCES appointment(id),
    FOREIGN KEY (patient_id) REFERENCES account(id),
    FOREIGN KEY (material_id) REFERENCES material_revision(id),
    FOREIGN KEY (consent_id) REFERENCES ai_consent(id),
    UNIQUE (patient_id, idempotency_key),
    CHECK (state IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELLED')),
    CHECK (attempts BETWEEN 0 AND 3),
    INDEX ai_task_appointment (appointment_id, created_at)
);

CREATE TABLE ai_result (
    id VARCHAR(36) PRIMARY KEY,
    task_id VARCHAR(36) NOT NULL,
    appointment_id VARCHAR(36) NOT NULL,
    material_id VARCHAR(36) NOT NULL,
    version_no INT NOT NULL,
    provider VARCHAR(40) NOT NULL,
    body TEXT NOT NULL,
    created_at BIGINT NOT NULL,
    FOREIGN KEY (task_id) REFERENCES ai_task(id),
    FOREIGN KEY (appointment_id) REFERENCES appointment(id),
    FOREIGN KEY (material_id) REFERENCES material_revision(id),
    UNIQUE (appointment_id, version_no),
    INDEX ai_result_appointment (appointment_id, created_at)
);

CREATE TABLE ai_call_log (
    id VARCHAR(36) PRIMARY KEY,
    task_id VARCHAR(36) NOT NULL,
    provider VARCHAR(40) NOT NULL,
    purpose VARCHAR(60) NOT NULL,
    state VARCHAR(16) NOT NULL,
    attempt TINYINT NOT NULL,
    duration_ms BIGINT NOT NULL,
    error_code VARCHAR(80) NULL,
    created_at BIGINT NOT NULL,
    FOREIGN KEY (task_id) REFERENCES ai_task(id),
    INDEX ai_call_task (task_id, created_at)
);
