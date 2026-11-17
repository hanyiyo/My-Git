ALTER TABLE clinical_result ADD chief_complaint TEXT NULL;
ALTER TABLE clinical_result ADD present_illness TEXT NULL;
ALTER TABLE clinical_result ADD history_allergy TEXT NULL;
ALTER TABLE clinical_result ADD diagnosis TEXT NULL;
ALTER TABLE clinical_result ADD treatment_plan TEXT NULL;
UPDATE clinical_result SET chief_complaint = COALESCE(chief_complaint, ''), present_illness = COALESCE(present_illness, ''),
    history_allergy = COALESCE(history_allergy, ''), diagnosis = COALESCE(diagnosis, ''), treatment_plan = COALESCE(treatment_plan, '');
ALTER TABLE pharmacy_order_line ADD usage_text VARCHAR(1000) NOT NULL DEFAULT '';
