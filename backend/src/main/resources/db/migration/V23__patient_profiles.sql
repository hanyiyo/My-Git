CREATE TABLE patient_profile (
    account_id VARCHAR(36) PRIMARY KEY,
    display_name VARCHAR(120) NOT NULL,
    phone_number VARCHAR(32) NOT NULL,
    FOREIGN KEY (account_id) REFERENCES account(id)
);
