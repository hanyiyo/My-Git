package cn.medical.clinical;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface ClinicalMapper {
    record Appointment(String id, String patientId, String doctorId, String serviceMode, String state) {}
    record Consultation(String appointmentId, String state, long createdAt, Long closedAt) {}
    record Material(String id, String appointmentId, int versionNo, String symptomText, boolean shared, long createdAt) {}
    record Attachment(String id, String materialId, String appointmentId, String originalName, String mediaType, long sizeBytes, String storageKey, long createdAt) {}
    record Message(String id, String appointmentId, String authorId, String authorRole, String body, long createdAt) {}
    record Result(String id, String appointmentId, int versionNo, String authorId, String encounterType, String body, long createdAt) {}
    record Correction(String id, String resultId, String appointmentId, String authorId, String reason, String body, long createdAt) {}

    @Select("SELECT id, patient_id, doctor_id, service_mode, state FROM appointment WHERE id=#{id}")
    Appointment appointment(@Param("id") String id);
    @Select("SELECT appointment_id, state, created_at, closed_at FROM consultation WHERE appointment_id=#{id}")
    Consultation consultation(@Param("id") String id);
    @Insert("INSERT INTO consultation (appointment_id, state, created_at) VALUES (#{id}, 'OPEN', #{now})")
    void insertConsultation(@Param("id") String id, @Param("now") long now);
    @Update("UPDATE consultation SET state='CLOSED', closed_at=#{now} WHERE appointment_id=#{id} AND state <> 'CLOSED'")
    int closeConsultation(@Param("id") String id, @Param("now") long now);
    @Update("UPDATE consultation SET state='REPLIED' WHERE appointment_id=#{id} AND state='OPEN'")
    int markReplied(@Param("id") String id);
    @Select("SELECT COALESCE(MAX(version_no), 0) FROM material_revision WHERE appointment_id=#{id}")
    int nextMaterialVersion(@Param("id") String id);
    @Insert("INSERT INTO material_revision (id, appointment_id, version_no, symptom_text, shared, created_at) VALUES (#{id}, #{appointmentId}, #{versionNo}, #{symptomText}, #{shared}, #{createdAt})")
    void insertMaterial(Material material);
    @Select("SELECT id, appointment_id, version_no, symptom_text, shared, created_at FROM material_revision WHERE appointment_id=#{id} ORDER BY version_no DESC")
    List<Material> materials(@Param("id") String id);
    @Insert("INSERT INTO medical_attachment (id, material_id, appointment_id, original_name, media_type, size_bytes, storage_key, created_at) VALUES (#{id}, #{materialId}, #{appointmentId}, #{originalName}, #{mediaType}, #{sizeBytes}, #{storageKey}, #{createdAt})")
    void insertAttachment(Attachment attachment);
    @Select("SELECT id, material_id, appointment_id, original_name, media_type, size_bytes, storage_key, created_at FROM medical_attachment WHERE material_id=#{id} ORDER BY created_at, id")
    List<Attachment> attachments(@Param("id") String id);
    @Select("SELECT id, material_id, appointment_id, original_name, media_type, size_bytes, storage_key, created_at FROM medical_attachment WHERE id=#{id}")
    Attachment attachment(@Param("id") String id);
    @Insert("INSERT INTO consultation_message (id, appointment_id, author_id, author_role, body, created_at) VALUES (#{id}, #{appointmentId}, #{authorId}, #{authorRole}, #{body}, #{createdAt})")
    void insertMessage(Message message);
    @Select("SELECT id, appointment_id, author_id, author_role, body, created_at FROM consultation_message WHERE appointment_id=#{id} ORDER BY created_at, id")
    List<Message> messages(@Param("id") String id);
    @Select("SELECT id, appointment_id, version_no, author_id, encounter_type, body, created_at FROM clinical_result WHERE appointment_id=#{id} ORDER BY version_no DESC LIMIT 1")
    Result latestResult(@Param("id") String id);
    @Select("SELECT id, appointment_id, version_no, author_id, encounter_type, body, created_at FROM clinical_result WHERE appointment_id=#{id} ORDER BY version_no, id")
    List<Result> results(@Param("id") String id);
    @Select("SELECT COALESCE(MAX(version_no), 0) FROM clinical_result WHERE appointment_id=#{id}")
    int nextResultVersion(@Param("id") String id);
    @Insert("INSERT INTO clinical_result (id, appointment_id, version_no, author_id, encounter_type, body, created_at) VALUES (#{id}, #{appointmentId}, #{versionNo}, #{authorId}, #{encounterType}, #{body}, #{createdAt})")
    void insertResult(Result result);
    @Insert("INSERT INTO clinical_correction (id, result_id, appointment_id, author_id, reason, body, created_at) VALUES (#{id}, #{resultId}, #{appointmentId}, #{authorId}, #{reason}, #{body}, #{createdAt})")
    void insertCorrection(Correction correction);
    @Select("SELECT id, result_id, appointment_id, author_id, reason, body, created_at FROM clinical_correction WHERE appointment_id=#{id} ORDER BY created_at, id")
    List<Correction> corrections(@Param("id") String id);
}
