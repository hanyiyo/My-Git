CREATE TABLE clinical_draft (
    appointment_id VARCHAR(36) PRIMARY KEY,
    doctor_id VARCHAR(36) NOT NULL,
    version_no INT NOT NULL,
    chief_complaint TEXT NOT NULL,
    present_illness TEXT NOT NULL,
    history_allergy TEXT NOT NULL,
    diagnosis TEXT NOT NULL,
    treatment_plan TEXT NOT NULL,
    prescriptions_json TEXT NOT NULL,
    updated_at BIGINT NOT NULL,
    FOREIGN KEY (appointment_id) REFERENCES appointment(id),
    FOREIGN KEY (doctor_id) REFERENCES account(id),
    CHECK (version_no > 0),
    UNIQUE (appointment_id, doctor_id)
);
