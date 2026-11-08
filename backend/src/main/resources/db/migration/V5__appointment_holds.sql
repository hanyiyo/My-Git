ALTER TABLE catalog_slot ADD reserved_by VARCHAR(36) NULL;

CREATE TABLE appointment (
    id VARCHAR(36) PRIMARY KEY,
    patient_id VARCHAR(36) NOT NULL,
    schedule_id VARCHAR(36) NOT NULL,
    slot_id VARCHAR(36) NOT NULL,
    doctor_id VARCHAR(36) NOT NULL,
    department_id VARCHAR(36) NOT NULL,
    clinic_date DATE NOT NULL,
    clinic_session CHAR(2) NOT NULL,
    service_mode VARCHAR(8) NOT NULL,
    source VARCHAR(8) NOT NULL,
    starts_at BIGINT NOT NULL,
    ends_at BIGINT NOT NULL,
    fee_minor BIGINT NOT NULL,
    created_at BIGINT NOT NULL,
    hold_expires_at BIGINT NOT NULL,
    state VARCHAR(20) NOT NULL,
    idempotency_key VARCHAR(80) NOT NULL,
    FOREIGN KEY (patient_id) REFERENCES account(id),
    FOREIGN KEY (schedule_id) REFERENCES schedule(id),
    FOREIGN KEY (slot_id) REFERENCES catalog_slot(id),
    UNIQUE (patient_id, idempotency_key),
    INDEX appointment_patient (patient_id, created_at),
    INDEX appointment_expiry (state, hold_expires_at),
    CHECK (fee_minor >= 0),
    CHECK (hold_expires_at > created_at)
);

CREATE TABLE patient_session_guard (
    patient_id VARCHAR(36) NOT NULL,
    clinic_date DATE NOT NULL,
    clinic_session CHAR(2) NOT NULL,
    appointment_id VARCHAR(36) NOT NULL,
    PRIMARY KEY (patient_id, clinic_date, clinic_session),
    FOREIGN KEY (patient_id) REFERENCES account(id),
    FOREIGN KEY (appointment_id) REFERENCES appointment(id)
);
