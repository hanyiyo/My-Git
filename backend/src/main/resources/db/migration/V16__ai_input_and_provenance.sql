ALTER TABLE ai_consent ADD text_hash VARCHAR(64) NULL;
ALTER TABLE ai_task
    ADD model_version VARCHAR(80) NOT NULL DEFAULT 'legacy',
    ADD prompt_version VARCHAR(80) NOT NULL DEFAULT 'legacy',
    ADD INDEX ai_task_consent (consent_id);

ALTER TABLE ai_consent MODIFY appointment_id VARCHAR(36) NULL;
ALTER TABLE ai_task
    MODIFY appointment_id VARCHAR(36) NULL,
    MODIFY material_id VARCHAR(36) NULL,
    ADD symptom_text TEXT NULL,
    ADD material_version INT NOT NULL DEFAULT 1,
    ADD draft BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE ai_task t JOIN material_revision m ON m.id=t.material_id
    SET t.material_version=m.version_no, t.symptom_text=m.symptom_text;
ALTER TABLE ai_result
    MODIFY appointment_id VARCHAR(36) NULL,
    MODIFY material_id VARCHAR(36) NULL;
CREATE TABLE ai_patient_guard (
    patient_id VARCHAR(36) PRIMARY KEY,
    FOREIGN KEY (patient_id) REFERENCES account(id)
);
CREATE TABLE ai_draft_share (
    task_id VARCHAR(36) NOT NULL,
    appointment_id VARCHAR(36) NOT NULL,
    material_id VARCHAR(36) NOT NULL,
    PRIMARY KEY (task_id, appointment_id),
    FOREIGN KEY (task_id) REFERENCES ai_task(id),
    FOREIGN KEY (appointment_id) REFERENCES appointment(id),
    FOREIGN KEY (material_id) REFERENCES material_revision(id)
);
