package cn.medical.ai;

import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface AiMapper {
    record Consent(String id, String appointmentId, String patientId, String provider, String purpose,
        String scope, boolean accepted, long createdAt, Long revokedAt) {}
    record Task(String id, String appointmentId, String patientId, String materialId, String consentId,
        String idempotencyKey, String provider, String purpose, String state, int attempts, String errorCode,
        long createdAt, Long finishedAt) {}
    record Result(String id, String taskId, String appointmentId, String materialId, int versionNo,
        String provider, String body, long createdAt) {}
    record CallLog(String id, String taskId, String provider, String purpose, String state, int attempt,
        long durationMs, String errorCode, long createdAt) {}

    @Insert("INSERT INTO ai_consent (id, appointment_id, patient_id, provider, purpose, scope, accepted, created_at) VALUES (#{id}, #{appointmentId}, #{patientId}, #{provider}, #{purpose}, #{scope}, #{accepted}, #{createdAt})")
    void insertConsent(Consent consent);
    @Select("SELECT id, appointment_id, patient_id, provider, purpose, scope, accepted, created_at, revoked_at FROM ai_consent WHERE id=#{id} AND appointment_id=#{appointmentId} AND patient_id=#{patientId}")
    Consent consent(@Param("id") String id, @Param("appointmentId") String appointmentId, @Param("patientId") String patientId);
    @Select("SELECT id, appointment_id, patient_id, provider, purpose, scope, accepted, created_at, revoked_at FROM ai_consent WHERE appointment_id=#{appointmentId} AND patient_id=#{patientId} ORDER BY created_at DESC, id DESC LIMIT 1")
    Consent latestConsent(@Param("appointmentId") String appointmentId, @Param("patientId") String patientId);
    @Update("UPDATE ai_consent SET revoked_at=#{now} WHERE id=#{id} AND patient_id=#{patientId} AND revoked_at IS NULL")
    int revokeConsent(@Param("id") String id, @Param("patientId") String patientId, @Param("now") long now);

    @Select("SELECT id, appointment_id, patient_id, material_id, consent_id, idempotency_key, provider, purpose, state, attempts, error_code, created_at, finished_at FROM ai_task WHERE patient_id=#{patientId} AND idempotency_key=#{key}")
    Task taskByKey(@Param("patientId") String patientId, @Param("key") String key);
    @Insert("INSERT INTO ai_task (id, appointment_id, patient_id, material_id, consent_id, idempotency_key, provider, purpose, state, attempts, created_at) VALUES (#{id}, #{appointmentId}, #{patientId}, #{materialId}, #{consentId}, #{idempotencyKey}, #{provider}, #{purpose}, 'RUNNING', 1, #{createdAt})")
    void insertTask(Task task);
    @Update("UPDATE ai_task SET state=#{state}, attempts=#{attempts}, error_code=#{errorCode}, finished_at=#{finishedAt} WHERE id=#{id}")
    int finishTask(@Param("id") String id, @Param("state") String state, @Param("attempts") int attempts,
        @Param("errorCode") String errorCode, @Param("finishedAt") long finishedAt);
    @Select("SELECT id, appointment_id, patient_id, material_id, consent_id, idempotency_key, provider, purpose, state, attempts, error_code, created_at, finished_at FROM ai_task WHERE id=#{id}")
    Task task(@Param("id") String id);

    @Insert("INSERT INTO ai_result (id, task_id, appointment_id, material_id, version_no, provider, body, created_at) VALUES (#{id}, #{taskId}, #{appointmentId}, #{materialId}, #{versionNo}, #{provider}, #{body}, #{createdAt})")
    void insertResult(Result result);
    @Select("SELECT id, task_id, appointment_id, material_id, version_no, provider, body, created_at FROM ai_result WHERE appointment_id=#{appointmentId} ORDER BY version_no, id")
    List<Result> results(@Param("appointmentId") String appointmentId);
    @Select("SELECT COALESCE(MAX(version_no), 0) FROM ai_result WHERE appointment_id=#{appointmentId}")
    int nextResultVersion(@Param("appointmentId") String appointmentId);

    @Insert("INSERT INTO ai_call_log (id, task_id, provider, purpose, state, attempt, duration_ms, error_code, created_at) VALUES (#{id}, #{taskId}, #{provider}, #{purpose}, #{state}, #{attempt}, #{durationMs}, #{errorCode}, #{createdAt})")
    void insertCallLog(CallLog log);
}
