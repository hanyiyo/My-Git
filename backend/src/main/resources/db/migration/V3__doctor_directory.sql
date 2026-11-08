ALTER TABLE doctor_profile ADD COLUMN display_name VARCHAR(120) NOT NULL DEFAULT '';
ALTER TABLE doctor_profile ADD COLUMN title VARCHAR(120) NOT NULL DEFAULT '';
ALTER TABLE doctor_profile ADD COLUMN biography VARCHAR(2000) NOT NULL DEFAULT '';
ALTER TABLE doctor_profile ADD CONSTRAINT doctor_credential_status CHECK (credential_status IN ('PENDING', 'CERTIFIED', 'REJECTED'));
CREATE TABLE doctor_assignment (
    doctor_id VARCHAR(36) PRIMARY KEY,
    department_id VARCHAR(36) NOT NULL,
    FOREIGN KEY (doctor_id) REFERENCES doctor_profile(account_id),
    FOREIGN KEY (department_id) REFERENCES department(id)
);
