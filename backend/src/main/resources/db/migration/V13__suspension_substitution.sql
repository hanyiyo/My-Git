CREATE TABLE department_head (
    department_id VARCHAR(36) PRIMARY KEY,
    doctor_id VARCHAR(36) NOT NULL,
    FOREIGN KEY (department_id) REFERENCES department(id),
    FOREIGN KEY (doctor_id) REFERENCES doctor_profile(account_id)
);

CREATE TABLE suspension_request (
    id VARCHAR(36) PRIMARY KEY,
    schedule_id VARCHAR(36) NOT NULL,
    doctor_id VARCHAR(36) NOT NULL,
    department_id VARCHAR(36) NOT NULL,
    requested_by VARCHAR(36) NOT NULL,
    substitute_doctor_id VARCHAR(36) NULL,
    state VARCHAR(32) NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    FOREIGN KEY (schedule_id) REFERENCES schedule(id),
    FOREIGN KEY (doctor_id) REFERENCES doctor_profile(account_id),
    FOREIGN KEY (department_id) REFERENCES department(id),
    FOREIGN KEY (requested_by) REFERENCES account(id),
    FOREIGN KEY (substitute_doctor_id) REFERENCES doctor_profile(account_id),
    CHECK (state IN ('HEAD_REVIEW', 'ADMIN_REVIEW', 'SUBSTITUTE_REVIEW', 'HEAD_REJECTED', 'SUBSTITUTE_REJECTED', 'APPROVED_STOP', 'APPROVED_SUBSTITUTE', 'EMERGENCY_STOP')),
    INDEX suspension_schedule (schedule_id, state)
);

CREATE TABLE substitution_choice (
    id VARCHAR(36) PRIMARY KEY,
    request_id VARCHAR(36) NOT NULL,
    appointment_id VARCHAR(36) NOT NULL,
    patient_id VARCHAR(36) NOT NULL,
    original_doctor_id VARCHAR(36) NOT NULL,
    substitute_doctor_id VARCHAR(36) NOT NULL,
    state VARCHAR(16) NOT NULL,
    deadline_at BIGINT NOT NULL,
    responded_at BIGINT NULL,
    reason VARCHAR(500) NULL,
    FOREIGN KEY (request_id) REFERENCES suspension_request(id),
    FOREIGN KEY (appointment_id) REFERENCES appointment(id),
    FOREIGN KEY (patient_id) REFERENCES account(id),
    FOREIGN KEY (original_doctor_id) REFERENCES doctor_profile(account_id),
    FOREIGN KEY (substitute_doctor_id) REFERENCES doctor_profile(account_id),
    UNIQUE (appointment_id),
    CHECK (state IN ('PENDING', 'ACCEPTED', 'REJECTED', 'EXPIRED')),
    INDEX substitution_patient (patient_id, state, deadline_at)
);

CREATE TABLE audit_event (
    id VARCHAR(36) PRIMARY KEY,
    actor_id VARCHAR(36) NOT NULL,
    action VARCHAR(80) NOT NULL,
    object_type VARCHAR(40) NOT NULL,
    object_id VARCHAR(36) NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    created_at BIGINT NOT NULL,
    FOREIGN KEY (actor_id) REFERENCES account(id),
    INDEX audit_object (object_type, object_id, created_at)
);
