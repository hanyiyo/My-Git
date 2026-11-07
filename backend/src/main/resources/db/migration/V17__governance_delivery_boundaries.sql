CREATE TABLE schedule_mutation_guard (
    schedule_id VARCHAR(36) PRIMARY KEY,
    FOREIGN KEY (schedule_id) REFERENCES schedule(id)
);
INSERT INTO schedule_mutation_guard (schedule_id) SELECT id FROM schedule;
ALTER TABLE appointment ADD substitution_pending BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE schedule MODIFY state VARCHAR(24) NOT NULL;
ALTER TABLE substitution_choice
    ADD INDEX substitution_appointment (appointment_id),
    DROP INDEX appointment_id,
    ADD UNIQUE (request_id, appointment_id);
