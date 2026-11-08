ALTER TABLE patient_session_guard MODIFY appointment_id VARCHAR(36) NULL;

ALTER TABLE catalog_slot ADD reserved_until BIGINT NULL;
UPDATE catalog_slot s JOIN appointment a ON a.id=s.reserved_by SET s.reserved_until=a.hold_expires_at;
