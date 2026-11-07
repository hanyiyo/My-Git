package cn.medical.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import cn.medical.catalog.CatalogMapper;
import cn.medical.catalog.CatalogService;
import cn.medical.clinical.ClinicalMapper;
import cn.medical.identity.ApiFailure;
import java.time.Clock;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiService {
    public record Analysis(String summary, String duration, String departmentHint, String followUpQuestions,
        String riskAlert, String safetyNotice, String provider) {}
    public record ConsentView(String id, String provider, String purpose, String scope, boolean accepted,
        long createdAt, Long revokedAt) {}
    public record DoctorRecommendation(String doctorId, String doctorName, String departmentId, String departmentName,
        String scheduleId, java.time.LocalDate clinicDate, String session, String serviceMode) {}
    public record ResultView(String taskId, String state, String provider, String purpose, int attempts,
        String errorCode, int versionNo, boolean stale, Analysis analysis, List<DoctorRecommendation> recommendations) {}
    public record BusinessAnswer(String mode, String answer, List<String> sources) {}
    public record ConsentInput(boolean accepted, String provider, String purpose, String scope) {}
    public record AnalyzeInput(String consentId, String symptomText, String idempotencyKey) {}
    public static final class ProviderFailure extends RuntimeException {
        private final String code;
        ProviderFailure(String code) { this.code = code; }
        String code() { return code; }
    }

    private final AiMapper ai;
    private final ClinicalMapper clinical;
    private final CatalogService catalog;
    private final AiTextProvider localProvider;
    private final ObjectMapper json;
    private final Clock clock;

    public AiService(AiMapper ai, ClinicalMapper clinical, CatalogService catalog, AiTextProvider localProvider,
        ObjectMapper json, Clock clock) {
        this.ai = ai; this.clinical = clinical; this.catalog = catalog; this.localProvider = localProvider;
        this.json = json; this.clock = clock;
    }

    @Transactional
    public ConsentView consent(String patient, String appointmentId, ConsentInput input) {
        var appointment = requirePatient(patient, appointmentId);
        if (!Set.of("CONFIRMED", "COMPLETED").contains(appointment.state()))
            throw new ApiFailure(409, "AI_NOT_ALLOWED", "当前预约不能进行预问诊");
        validateConsent(input);
        String id = UUID.randomUUID().toString(); long now = clock.millis();
        ai.insertConsent(new AiMapper.Consent(id, appointmentId, patient, input.provider(), input.purpose(), input.scope(), input.accepted(), now, null));
        return new ConsentView(id, input.provider(), input.purpose(), input.scope(), input.accepted(), now, null);
    }

    @Transactional
    public ResultView analyze(String patient, String appointmentId, AnalyzeInput input) {
        var appointment = requirePatient(patient, appointmentId);
        if (input == null || input.idempotencyKey() == null || input.idempotencyKey().isBlank() || input.idempotencyKey().length() > 80)
            throw new ApiFailure(400, "INVALID_INPUT", "预问诊提交标识不能为空且长度受限");
        var existing = ai.taskByKey(patient, input.idempotencyKey());
        if (existing != null) {
            if (!existing.appointmentId().equals(appointmentId))
                throw new ApiFailure(409, "IDEMPOTENCY_CONFLICT", "同一提交标识不能用于不同预约");
            return resultView(existing, appointmentId);
        }
        if (input.symptomText() == null || input.symptomText().isBlank() || input.symptomText().length() > 10000)
            throw new ApiFailure(400, "INVALID_INPUT", "症状文本不能为空且不能超过10000字");
        var consent = ai.consent(input.consentId(), appointmentId, patient);
        if (consent == null || !consent.accepted() || consent.revokedAt() != null)
            throw new ApiFailure(403, "AI_CONSENT_REQUIRED", "请先确认本次预问诊授权");
        if (!consent.provider().equals("LOCAL_MOCK") && !consent.provider().equals("EXTERNAL_TEXT"))
            throw new ApiFailure(400, "INVALID_INPUT", "AI provider 无效");
        long now = clock.millis();
        if (clinical.consultation(appointmentId) == null) clinical.insertConsultation(appointmentId, now);
        String materialId = UUID.randomUUID().toString();
        clinical.insertMaterial(new ClinicalMapper.Material(materialId, appointmentId, clinical.nextMaterialVersion(appointmentId) + 1,
            input.symptomText().trim(), true, now));
        String taskId = UUID.randomUUID().toString();
        var task = new AiMapper.Task(taskId, appointmentId, patient, materialId, consent.id(), input.idempotencyKey(),
            consent.provider(), consent.purpose(), "RUNNING", 1, null, now, null);
        try { ai.insertTask(task); }
        catch (DuplicateKeyException duplicate) { return resultView(ai.taskByKey(patient, input.idempotencyKey()), appointmentId); }
        long started = clock.millis();
        try {
            if (consent.provider().equals("EXTERNAL_TEXT")) throw new ProviderFailure("AI_PROVIDER_UNAVAILABLE");
            var departmentNames = catalog.departments(false).stream().map(CatalogMapper.Department::name).toList();
            var analysis = localProvider.analyze(input.symptomText(), departmentNames);
            ai.insertResult(new AiMapper.Result(UUID.randomUUID().toString(), taskId, appointmentId, materialId,
                ai.nextResultVersion(appointmentId) + 1, consent.provider(), json.writeValueAsString(analysis), clock.millis()));
            ai.finishTask(taskId, "SUCCEEDED", 1, null, clock.millis());
            ai.insertCallLog(new AiMapper.CallLog(UUID.randomUUID().toString(), taskId, consent.provider(), consent.purpose(),
                "SUCCEEDED", 1, Math.max(0, clock.millis() - started), null, clock.millis()));
        } catch (ProviderFailure failure) {
            ai.finishTask(taskId, "FAILED", 1, failure.code(), clock.millis());
            ai.insertCallLog(new AiMapper.CallLog(UUID.randomUUID().toString(), taskId, consent.provider(), consent.purpose(),
                "FAILED", 1, Math.max(0, clock.millis() - started), failure.code(), clock.millis()));
        } catch (JsonProcessingException failure) {
            ai.finishTask(taskId, "FAILED", 1, "AI_RESULT_SERIALIZATION_FAILED", clock.millis());
            ai.insertCallLog(new AiMapper.CallLog(UUID.randomUUID().toString(), taskId, consent.provider(), consent.purpose(),
                "FAILED", 1, Math.max(0, clock.millis() - started), "AI_RESULT_SERIALIZATION_FAILED", clock.millis()));
        }
        return resultView(ai.task(taskId), appointmentId);
    }

    @Transactional(readOnly = true)
    public List<ResultView> doctorResults(String doctor, String appointmentId) {
        var appointment = clinical.appointment(appointmentId);
        if (appointment == null || !appointment.doctorId().equals(doctor)) throw new ApiFailure(404, "NOT_FOUND", "问诊不存在");
        return ai.results(appointmentId).stream().map(result -> resultView(result, appointmentId)).toList();
    }

    @Transactional(readOnly = true)
    public List<ResultView> patientResults(String patient, String appointmentId) {
        requirePatient(patient, appointmentId);
        return ai.results(appointmentId).stream().map(result -> resultView(result, appointmentId)).toList();
    }

    @Transactional(readOnly = true)
    public BusinessAnswer business(String question) {
        if (question == null || question.isBlank()) throw new ApiFailure(400, "INVALID_INPUT", "咨询内容不能为空");
        String q = question.trim().toLowerCase();
        var hospital = catalog.hospital();
        if (q.contains("地址") || q.contains("在哪") || q.contains("位置"))
            return new BusinessAnswer("LOCAL_FACTS", hospital.name() + "地址：" + hospital.address(), List.of("hospital.address"));
        if (q.contains("流程") || q.contains("预约") || q.contains("挂号") || q.contains("支付"))
            return new BusinessAnswer("LOCAL_FACTS", hospital.description(), List.of("hospital.description"));
        for (var department : catalog.departments(false)) {
            if (q.contains(department.name().toLowerCase()))
                return new BusinessAnswer("LOCAL_FACTS", department.name() + "：" + department.description(), List.of("department:" + department.id()));
        }
        throw new ApiFailure(422, "BUSINESS_TOPIC_UNSUPPORTED", "我只能回答医院地址、服务流程和科室职责等院内业务信息");
    }

    private ResultView resultView(AiMapper.Result result, String appointmentId) {
        if (result == null) return new ResultView(null, "FAILED", "", "", 0, "AI_TASK_NOT_FOUND", 0, false, null, List.of());
        var task = ai.task(result.taskId());
        var latest = clinical.materials(appointmentId).stream().findFirst().orElse(null);
        boolean stale = latest != null && !latest.id().equals(result.materialId());
        Analysis analysis = readAnalysis(result.body());
        return new ResultView(result.taskId(), task == null ? "SUCCEEDED" : task.state(), result.provider(),
            task == null ? "PRECONSULTATION_SUMMARY" : task.purpose(), task == null ? 1 : task.attempts(),
            task == null ? null : task.errorCode(), result.versionNo(), stale, analysis,
            analysis == null ? List.of() : recommendations(analysis.departmentHint()));
    }

    private ResultView resultView(AiMapper.Task task, String appointmentId) {
        if (task == null) return new ResultView(null, "FAILED", "", "", 0, "AI_TASK_NOT_FOUND", 0, false, null, List.of());
        var result = ai.results(appointmentId).stream().filter(value -> value.taskId().equals(task.id())).findFirst().orElse(null);
        if (result != null) return resultView(result, appointmentId);
        return new ResultView(task.id(), task.state(), task.provider(), task.purpose(), task.attempts(), task.errorCode(), 0, false, null, List.of());
    }

    private Analysis readAnalysis(String body) {
        try { return json.readValue(body, Analysis.class); }
        catch (Exception ignored) { return null; }
    }

    private List<DoctorRecommendation> recommendations(String hint) {
        var departments = catalog.departments(false);
        var department = departments.stream().filter(value -> value.name().equals(hint)).findFirst()
            .orElseGet(() -> departments.stream().findFirst().orElse(null));
        if (department == null) return List.of();
        var result = new ArrayList<DoctorRecommendation>();
        for (var doctor : catalog.doctors(false, department.id())) {
            for (var schedule : catalog.schedules(false, doctor.id(), department.id(), null, null)) {
                if (schedule.advanceAvailable() || schedule.walkInAvailable()) {
                    result.add(new DoctorRecommendation(doctor.id(), doctor.displayName(), department.id(), department.name(),
                        schedule.id(), schedule.clinicDate(), schedule.session(), schedule.serviceMode()));
                    break;
                }
                if (result.size() >= 5) return result;
            }
            if (result.size() >= 5) break;
        }
        return result;
    }

    private void validateConsent(ConsentInput input) {
        if (input == null || input.provider() == null || input.purpose() == null || input.scope() == null
            || input.purpose().isBlank() || input.scope().isBlank() || input.purpose().length() > 60 || input.scope().length() > 120)
            throw new ApiFailure(400, "INVALID_INPUT", "预问诊授权信息不完整");
        if (!Set.of("LOCAL_MOCK", "EXTERNAL_TEXT").contains(input.provider()))
            throw new ApiFailure(400, "INVALID_INPUT", "AI provider 无效");
    }

    private ClinicalMapper.Appointment requirePatient(String patient, String id) {
        var appointment = clinical.appointment(id);
        if (appointment == null || !appointment.patientId().equals(patient)) throw new ApiFailure(404, "NOT_FOUND", "问诊不存在");
        return appointment;
    }
}
