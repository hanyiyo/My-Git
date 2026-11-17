CREATE TABLE pharmacy_medicine (
 id VARCHAR(36) PRIMARY KEY, name VARCHAR(120) NOT NULL, spec VARCHAR(200) NOT NULL, fee_minor BIGINT NOT NULL, stock INT NOT NULL, threshold INT NOT NULL DEFAULT 0, active BOOLEAN NOT NULL DEFAULT TRUE, created_at BIGINT NOT NULL, updated_at BIGINT NOT NULL,
 CHECK (fee_minor >= 0), CHECK (stock >= 0), CHECK (threshold >= 0)
);
CREATE TABLE pharmacy_order (
 id VARCHAR(36) PRIMARY KEY, appointment_id VARCHAR(36) NOT NULL, patient_id VARCHAR(36) NOT NULL, doctor_id VARCHAR(36) NOT NULL, state VARCHAR(20) NOT NULL, total_minor BIGINT NOT NULL, expires_at BIGINT NOT NULL, medicine_payment_hours INT NOT NULL, created_at BIGINT NOT NULL, paid_at BIGINT NULL,
 UNIQUE KEY pharmacy_order_appointment (appointment_id), INDEX pharmacy_order_due(state,expires_at)
);
CREATE TABLE pharmacy_order_line (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, order_id VARCHAR(36) NOT NULL, medicine_id VARCHAR(36) NOT NULL, medicine_name VARCHAR(120) NOT NULL, medicine_spec VARCHAR(200) NOT NULL, fee_minor BIGINT NOT NULL, quantity INT NOT NULL,
 INDEX pharmacy_line_order(order_id), CONSTRAINT pharmacy_line_order_fk FOREIGN KEY(order_id) REFERENCES pharmacy_order(id)
);
