package cn.medical.ai;

import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface AiMapper {
    @Insert("INSERT INTO ai_patient_guard (patient_id) VALUES (#{patient}) ON DUPLICATE KEY UPDATE patient_id=patient_id")
    void ensurePatient(@Param("patient") String patient);
    @Select("SELECT patient_id FROM ai_patient_guard WHERE patient_id=#{patient} FOR UPDATE")
    String lockPatient(@Param("patient") String patient);
    record Consent(String id, String appointmentId, String patientId, String provider, String purpose,
        String scope, boolean accepted, long createdAt, Long revokedAt, String textHash) {}
    record Task(String id, String appointmentId, String patientId, String materialId, String consentId,
        String idempotencyKey, String provider, String purpose, String state, int attempts, String errorCode,
        long createdAt, Long finishedAt, String modelVersion, String promptVersion, String symptomText, int materialVersion, boolean draft) {}
    record Result(String id, String taskId, String appointmentId, String materialId, int versionNo,
        String provider, String body, long createdAt) {}
    record CallLog(String id, String taskId, String provider, String purpose, String state, int attempt,
        long durationMs, String errorCode, long createdAt) {}
    record SummaryConsent(String id, String appointmentId, String patientId, String provider, String purpose,
        String scope, String messageIds, String materialIds, boolean accepted, long createdAt, Long revokedAt) {}
    record DoctorSummary(String id, String appointmentId, String doctorId, String consentId, int versionNo,
        String inputHash, String provider, String state, String errorCode, String body, int messageCount,
        long createdAt, Long finishedAt, String modelVersion, String promptVersion) {}

    @Insert("INSERT INTO ai_consent (id, appointment_id, patient_id, provider, purpose, scope, accepted, created_at, text_hash) VALUES (#{id}, #{appointmentId}, #{patientId}, #{provider}, #{purpose}, #{scope}, #{accepted}, #{createdAt}, #{textHash})")
    void insertConsent(Consent consent);
    @Select("SELECT id, appointment_id, patient_id, provider, purpose, scope, accepted, created_at, revoked_at, text_hash FROM ai_consent WHERE id=#{id} AND appointment_id <=> #{appointmentId} AND patient_id=#{patientId}")
    Consent consent(@Param("id") String id, @Param("appointmentId") String appointmentId, @Param("patientId") String patientId);
    @Select("SELECT id, appointment_id, patient_id, provider, purpose, scope, accepted, created_at, revoked_at, text_hash FROM ai_consent WHERE appointment_id=#{appointmentId} AND patient_id=#{patientId} ORDER BY created_at DESC, id DESC LIMIT 1")
    Consent latestConsent(@Param("appointmentId") String appointmentId, @Param("patientId") String patientId);
    @Update("UPDATE ai_consent SET revoked_at=#{now} WHERE id=#{id} AND patient_id=#{patientId} AND revoked_at IS NULL")
    int revokeConsent(@Param("id") String id, @Param("patientId") String patientId, @Param("now") long now);

    @Select("SELECT id, appointment_id, patient_id, material_id, consent_id, idempotency_key, provider, purpose, state, attempts, error_code, created_at, finished_at, model_version, prompt_version, symptom_text, material_version, draft FROM ai_task WHERE patient_id=#{patientId} AND idempotency_key=#{key}")
    Task taskByKey(@Param("patientId") String patientId, @Param("key") String key);
    @Insert("INSERT INTO ai_task (id, appointment_id, patient_id, material_id, consent_id, idempotency_key, provider, purpose, state, attempts, created_at, model_version, prompt_version, symptom_text, material_version, draft) VALUES (#{id}, #{appointmentId}, #{patientId}, #{materialId}, #{consentId}, #{idempotencyKey}, #{provider}, #{purpose}, 'PENDING', 0, #{createdAt}, #{modelVersion}, #{promptVersion}, #{symptomText}, #{materialVersion}, #{draft})")
    void insertTask(Task task);
    @Update("UPDATE ai_task SET state=#{state}, attempts=#{attempts}, error_code=#{errorCode}, finished_at=#{finishedAt} WHERE id=#{id}")
    int finishTask(@Param("id") String id, @Param("state") String state, @Param("attempts") int attempts,
        @Param("errorCode") String errorCode, @Param("finishedAt") Long finishedAt);
    @Select("SELECT id, appointment_id, patient_id, material_id, consent_id, idempotency_key, provider, purpose, state, attempts, error_code, created_at, finished_at, model_version, prompt_version, symptom_text, material_version, draft FROM ai_task WHERE id=#{id}")
    Task task(@Param("id") String id);
    @Select("SELECT id, appointment_id, patient_id, material_id, consent_id, idempotency_key, provider, purpose, state, attempts, error_code, created_at, finished_at, model_version, prompt_version, symptom_text, material_version, draft FROM ai_task WHERE id=#{id} FOR UPDATE") Task lockTask(String id);
    record Lease(String token,Long expiresAt) {}
    @Select("SELECT lease_token AS token,COALESCE(lease_expires_at,0) AS expires_at FROM ai_task WHERE id=#{id}") Lease lease(String id);
    @Select("SELECT id FROM ai_task WHERE (state='PENDING' AND next_attempt_at<=#{now}) OR (state='RUNNING' AND (lease_expires_at IS NULL OR lease_expires_at<=#{now})) ORDER BY created_at,id LIMIT 100") List<String> due(long now);
    @Update("UPDATE ai_task SET state='RUNNING',attempts=attempts+1,lease_token=#{token},lease_expires_at=#{expires} WHERE id=#{id}")
    void claim(@Param("id") String id,@Param("token") String token,@Param("expires") long expires);
    @Update("UPDATE ai_task SET lease_token=NULL,lease_expires_at=NULL,next_attempt_at=#{next} WHERE id=#{id} AND lease_token=#{token}")
    void release(@Param("id") String id,@Param("token") String token,@Param("next") long next);

    @Select("SELECT id, appointment_id, patient_id, material_id, consent_id, idempotency_key, provider, purpose, state, attempts, error_code, created_at, finished_at, model_version, prompt_version, symptom_text, material_version, draft FROM ai_task WHERE appointment_id=#{id} ORDER BY created_at DESC, id")
    List<Task> tasks(@Param("id") String id);
    @Select("SELECT id, appointment_id, patient_id, material_id, consent_id, idempotency_key, provider, purpose, state, attempts, error_code, created_at, finished_at, model_version, prompt_version, symptom_text, material_version, draft FROM ai_task WHERE patient_id=#{patient} AND draft=TRUE ORDER BY material_version DESC")
    List<Task> drafts(@Param("patient") String patient);
    @Select("SELECT COALESCE(MAX(material_version), 0) FROM ai_task WHERE patient_id=#{patient} AND draft=TRUE")
    int latestDraftVersion(@Param("patient") String patient);
    record DraftShare(String taskId, String appointmentId, String materialId) {}
    @Insert("INSERT INTO ai_draft_share (task_id, appointment_id, material_id) VALUES (#{taskId}, #{appointmentId}, #{materialId})")
    void shareDraft(DraftShare share);
    @Select("SELECT task_id, appointment_id, material_id FROM ai_draft_share WHERE appointment_id=#{id}")
    List<DraftShare> sharedDrafts(@Param("id") String id);
    @Select("SELECT COUNT(*) FROM ai_task WHERE consent_id=#{id}")
    int consentUses(@Param("id") String id);
    record CallMetadata(String taskId, String provider, String purpose, String state, int attempt, long durationMs,
        String errorCode, long createdAt, String modelVersion, String promptVersion) {}
    @Select("SELECT l.task_id, l.provider, l.purpose, l.state, l.attempt, l.duration_ms, l.error_code, l.created_at, t.model_version, t.prompt_version FROM ai_call_log l JOIN ai_task t ON t.id=l.task_id ORDER BY l.created_at DESC, l.id LIMIT 200")
    List<CallMetadata> callMetadata();

    @Insert("INSERT INTO ai_result (id, task_id, appointment_id, material_id, version_no, provider, body, created_at) VALUES (#{id}, #{taskId}, #{appointmentId}, #{materialId}, #{versionNo}, #{provider}, #{body}, #{createdAt})")
    void insertResult(Result result);
    @Select("SELECT id, task_id, appointment_id, material_id, version_no, provider, body, created_at FROM ai_result WHERE appointment_id=#{appointmentId} ORDER BY version_no, id")
    List<Result> results(@Param("appointmentId") String appointmentId);
    @Select("SELECT id, task_id, appointment_id, material_id, version_no, provider, body, created_at FROM ai_result WHERE task_id=#{id}")
    Result result(@Param("id") String id);
    @Select("SELECT COALESCE(MAX(version_no), 0) FROM ai_result WHERE appointment_id=#{appointmentId}")
    int nextResultVersion(@Param("appointmentId") String appointmentId);

    @Insert("INSERT INTO ai_call_log (id, task_id, provider, purpose, state, attempt, duration_ms, error_code, created_at) VALUES (#{id}, #{taskId}, #{provider}, #{purpose}, #{state}, #{attempt}, #{durationMs}, #{errorCode}, #{createdAt})")
    void insertCallLog(CallLog log);

    @Insert("INSERT INTO ai_summary_consent (id, appointment_id, patient_id, provider, purpose, scope, message_ids, material_ids, accepted, created_at) VALUES (#{id}, #{appointmentId}, #{patientId}, #{provider}, #{purpose}, #{scope}, #{messageIds}, #{materialIds}, #{accepted}, #{createdAt})")
    void insertSummaryConsent(SummaryConsent consent);
    @Select("SELECT id, appointment_id, patient_id, provider, purpose, scope, message_ids, material_ids, accepted, created_at, revoked_at FROM ai_summary_consent WHERE id=#{id} AND appointment_id=#{appointmentId} AND patient_id=#{patientId}")
    SummaryConsent summaryConsent(@Param("id") String id, @Param("appointmentId") String appointmentId, @Param("patientId") String patientId);
    @Select("SELECT id, appointment_id, patient_id, provider, purpose, scope, message_ids, material_ids, accepted, created_at, revoked_at FROM ai_summary_consent WHERE appointment_id=#{appointmentId} AND patient_id=#{patientId} ORDER BY created_at DESC, id DESC")
    List<SummaryConsent> summaryConsents(@Param("appointmentId") String appointmentId, @Param("patientId") String patientId);
    @Select("SELECT id, appointment_id, patient_id, provider, purpose, scope, message_ids, material_ids, accepted, created_at, revoked_at FROM ai_summary_consent WHERE appointment_id=#{appointmentId} AND patient_id=#{patientId} AND accepted=TRUE AND revoked_at IS NULL ORDER BY created_at DESC, id DESC LIMIT 1")
    SummaryConsent latestSummaryConsent(@Param("appointmentId") String appointmentId, @Param("patientId") String patientId);
    @Update("UPDATE ai_summary_consent SET revoked_at=#{now} WHERE id=#{id} AND patient_id=#{patientId} AND revoked_at IS NULL")
    int revokeSummaryConsent(@Param("id") String id, @Param("patientId") String patientId, @Param("now") long now);
    @Insert("INSERT INTO ai_doctor_summary (id, appointment_id, doctor_id, consent_id, version_no, input_hash, provider, state, error_code, body, message_count, created_at, finished_at, model_version, prompt_version) VALUES (#{id}, #{appointmentId}, #{doctorId}, #{consentId}, #{versionNo}, #{inputHash}, #{provider}, #{state}, #{errorCode}, #{body}, #{messageCount}, #{createdAt}, #{finishedAt}, #{modelVersion}, #{promptVersion})")
    void insertDoctorSummary(DoctorSummary summary);
    @Select("SELECT id, appointment_id, doctor_id, consent_id, version_no, input_hash, provider, state, error_code, body, message_count, created_at, finished_at, model_version, prompt_version FROM ai_doctor_summary WHERE appointment_id=#{appointmentId} ORDER BY version_no DESC, id DESC")
    List<DoctorSummary> doctorSummaries(@Param("appointmentId") String appointmentId);
    @Select("SELECT COALESCE(MAX(version_no), 0) FROM ai_doctor_summary WHERE appointment_id=#{appointmentId}")
    int nextDoctorSummaryVersion(@Param("appointmentId") String appointmentId);
}
