CREATE TABLE consultation (
    appointment_id VARCHAR(36) PRIMARY KEY,
    state VARCHAR(16) NOT NULL,
    created_at BIGINT NOT NULL,
    closed_at BIGINT NULL,
    FOREIGN KEY (appointment_id) REFERENCES appointment(id),
    CHECK (state IN ('OPEN', 'REPLIED', 'CLOSED'))
);

CREATE TABLE material_revision (
    id VARCHAR(36) PRIMARY KEY,
    appointment_id VARCHAR(36) NOT NULL,
    version_no INT NOT NULL,
    symptom_text TEXT NOT NULL,
    shared BOOLEAN NOT NULL,
    created_at BIGINT NOT NULL,
    FOREIGN KEY (appointment_id) REFERENCES appointment(id),
    UNIQUE (appointment_id, version_no),
    INDEX material_appointment (appointment_id, version_no)
);

CREATE TABLE medical_attachment (
    id VARCHAR(36) PRIMARY KEY,
    material_id VARCHAR(36) NOT NULL,
    appointment_id VARCHAR(36) NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    media_type VARCHAR(80) NOT NULL,
    size_bytes BIGINT NOT NULL,
    storage_key VARCHAR(500) NOT NULL UNIQUE,
    created_at BIGINT NOT NULL,
    FOREIGN KEY (material_id) REFERENCES material_revision(id),
    FOREIGN KEY (appointment_id) REFERENCES appointment(id),
    CHECK (size_bytes > 0 AND size_bytes <= 10485760)
);

CREATE TABLE consultation_message (
    id VARCHAR(36) PRIMARY KEY,
    appointment_id VARCHAR(36) NOT NULL,
    author_id VARCHAR(36) NOT NULL,
    author_role VARCHAR(16) NOT NULL,
    body TEXT NOT NULL,
    created_at BIGINT NOT NULL,
    FOREIGN KEY (appointment_id) REFERENCES appointment(id),
    FOREIGN KEY (author_id) REFERENCES account(id),
    CHECK (author_role IN ('PATIENT', 'DOCTOR')),
    INDEX message_appointment (appointment_id, created_at, id)
);

CREATE TABLE clinical_result (
    id VARCHAR(36) PRIMARY KEY,
    appointment_id VARCHAR(36) NOT NULL,
    version_no INT NOT NULL,
    author_id VARCHAR(36) NOT NULL,
    encounter_type VARCHAR(24) NOT NULL,
    body TEXT NOT NULL,
    created_at BIGINT NOT NULL,
    FOREIGN KEY (appointment_id) REFERENCES appointment(id),
    FOREIGN KEY (author_id) REFERENCES account(id),
    UNIQUE (appointment_id, version_no),
    CHECK (encounter_type IN ('ONLINE_CHAT', 'OFFLINE_RESULT'))
);

CREATE TABLE clinical_correction (
    id VARCHAR(36) PRIMARY KEY,
    result_id VARCHAR(36) NOT NULL,
    appointment_id VARCHAR(36) NOT NULL,
    author_id VARCHAR(36) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    body TEXT NOT NULL,
    created_at BIGINT NOT NULL,
    FOREIGN KEY (result_id) REFERENCES clinical_result(id),
    FOREIGN KEY (appointment_id) REFERENCES appointment(id),
    FOREIGN KEY (author_id) REFERENCES account(id),
    INDEX correction_result (result_id, created_at, id)
);
