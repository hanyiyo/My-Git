CREATE TABLE account (
    id VARCHAR(36) PRIMARY KEY,
    identifier VARCHAR(190) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at BIGINT NOT NULL
);
CREATE TABLE account_role (
    account_id VARCHAR(36) NOT NULL,
    role VARCHAR(16) NOT NULL,
    PRIMARY KEY (account_id, role),
    FOREIGN KEY (account_id) REFERENCES account(id),
    CHECK (role IN ('PATIENT', 'DOCTOR', 'ADMIN'))
);
CREATE TABLE doctor_profile (
    account_id VARCHAR(36) PRIMARY KEY,
    credential_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    FOREIGN KEY (account_id) REFERENCES account(id)
);
CREATE TABLE refresh_session (
    id VARCHAR(36) PRIMARY KEY,
    account_id VARCHAR(36) NOT NULL,
    token_hash CHAR(64) NOT NULL UNIQUE,
    expires_at BIGINT NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    FOREIGN KEY (account_id) REFERENCES account(id),
    INDEX refresh_account (account_id)
);
