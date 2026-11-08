CREATE TABLE schedule (
    id VARCHAR(36) PRIMARY KEY,
    doctor_id VARCHAR(36) NOT NULL,
    department_id VARCHAR(36) NOT NULL,
    clinic_date DATE NOT NULL,
    clinic_session CHAR(2) NOT NULL,
    service_mode VARCHAR(8) NOT NULL,
    starts_at BIGINT NOT NULL,
    ends_at BIGINT NOT NULL,
    zone_id VARCHAR(40) NOT NULL,
    capacity INT NOT NULL,
    fee_minor BIGINT NOT NULL,
    state VARCHAR(16) NOT NULL,
    FOREIGN KEY (doctor_id) REFERENCES doctor_profile(account_id),
    FOREIGN KEY (department_id) REFERENCES department(id),
    UNIQUE (doctor_id, clinic_date, clinic_session),
    CHECK (clinic_session IN ('AM', 'PM')),
    CHECK (service_mode IN ('ONLINE', 'OFFLINE')),
    CHECK (capacity BETWEEN 1 AND 500),
    CHECK (fee_minor BETWEEN 0 AND 100000000),
    CHECK (ends_at > starts_at),
    INDEX schedule_start (starts_at)
);
CREATE TABLE catalog_slot (
    id VARCHAR(36) PRIMARY KEY,
    schedule_id VARCHAR(36) NOT NULL,
    position INT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    FOREIGN KEY (schedule_id) REFERENCES schedule(id),
    UNIQUE (schedule_id, position),
    CHECK (position > 0)
);
