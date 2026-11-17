package cn.medical.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import cn.medical.booking.BookingService;
import cn.medical.catalog.CatalogMapper;
import cn.medical.catalog.CatalogService;
import cn.medical.clinical.ClinicalService;
import cn.medical.clinical.ClinicalMapper;
import cn.medical.clinical.ClinicalAttachmentValidator;
import cn.medical.identity.ApiFailure;
import cn.medical.governance.AuditService;
import cn.medical.governance.ReviewPolicyService;
import cn.medical.delivery.DeliveryService;
import java.time.Clock;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AiService {
    public record Analysis(String summary, String duration, String departmentHint, String followUpQuestions,
        String riskAlert, String safetyNotice, String provider) {}
    public record ConsentView(String id, String provider, String purpose, String scope, boolean accepted,
        long createdAt, Long revokedAt) {}
    public record DoctorRecommendation(String doctorId, String doctorName, String departmentId, String departmentName,
        String scheduleId, java.time.LocalDate clinicDate, String session, String serviceMode) {}
    public record ResultView(String taskId, String state, String provider, String purpose, int attempts,
        String errorCode, int versionNo, boolean stale, Analysis analysis, List<DoctorRecommendation> recommendations,
        String materialId, int materialVersion, String consentId, String modelVersion, String promptVersion) {}
    public record BusinessAnswer(String mode, String answer, List<String> sources) {}
    public record TriageAnswer(String mode, String answer, String departmentHint, boolean imageReceived, List<String> sources) {}
    public record ConversationSummary(Analysis analysis, int messageCount, String provider) {}
    public record ConsentInput(boolean accepted, String provider, String purpose, String scope, String symptomText) {}
    public record AnalyzeInput(String consentId, String symptomText, String idempotencyKey) {}
    public record SummaryConsentInput(boolean accepted, String provider, String purpose, String scope,
        List<String> messageIds, List<String> materialIds) {}
    public record SummaryConsentView(String id, String provider, String purpose, String scope, boolean accepted,
        List<String> messageIds, List<String> materialIds, long createdAt, Long revokedAt) {}
    private record SummaryInput(String text, String hash, int messageCount) {}
    private record Prepared(AiMapper.Task task, boolean created) {}
    private record Claim(AiMapper.Task task,String token) {}
    public static final class ProviderFailure extends RuntimeException {
        private final String code;
        public ProviderFailure(String code) { this.code = code; }
        String code() { return code; }
    }
    private final AiMapper ai;
    private final ClinicalService clinical;
    private final BookingService booking;
    private final CatalogService catalog;
    private final AiTextProvider provider;
    private final ObjectMapper json;
    private final Clock clock;
    private final TransactionTemplate transactions;
    private final AuditService audit;
    private final ReviewPolicyService policy;
    private final DeliveryService delivery;

    public AiService(AiMapper ai, ClinicalService clinical, BookingService booking, CatalogService catalog, AiTextProvider provider,
        ObjectMapper json, Clock clock, PlatformTransactionManager manager, AuditService audit, ReviewPolicyService policy, DeliveryService delivery) {
        this.ai = ai; this.clinical = clinical; this.booking = booking; this.catalog = catalog; this.provider = provider;
        this.json = json; this.clock = clock;
        this.audit = audit; this.policy = policy;
        this.delivery = delivery;
        transactions = new TransactionTemplate(manager);
        transactions.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public ConsentView consent(String patient, String id, ConsentInput input) {
        lockScope(patient, id);
        if (id != null && !requirePatient(patient, id).state().equals("CONFIRMED")) throw new ApiFailure(409, "AI_NOT_ALLOWED", "当前预约不能进行预问诊");
        if (input == null || !Set.of("LOCAL_MOCK", "EXTERNAL_TEXT").contains(Objects.toString(input.provider(), ""))
            || !"PRECONSULTATION_SUMMARY".equals(input.purpose()) || !"SYMPTOM_TEXT".equals(input.scope()))
            throw new ApiFailure(400, "INVALID_INPUT", "仅支持本次症状文本的预问诊摘要授权");
        if (input.accepted()) validateText(input.symptomText());
        String consentId = UUID.randomUUID().toString(); long now = clock.millis();
        ai.insertConsent(new AiMapper.Consent(consentId, id, patient, input.provider(), input.purpose(), input.scope(),
            input.accepted(), now, null, input.accepted() ? hash(input.symptomText()) : null));
        audit.record(patient, input.accepted() ? "AI_CONSENT_ACCEPTED" : "AI_CONSENT_REFUSED", "AI_CONSENT", consentId, input.purpose() + ":" + input.scope());
        return new ConsentView(consentId, input.provider(), input.purpose(), input.scope(), input.accepted(), now, null);
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void revoke(String patient, String id, String consentId) {
        lockScope(patient, id);
        if (id != null) requirePatient(patient, id);
        if (ai.consent(consentId, id, patient) == null) throw new ApiFailure(404, "NOT_FOUND", "授权不存在");
        ai.revokeConsent(consentId, patient, clock.millis());
        audit.record(patient, "AI_CONSENT_REVOKED", "AI_CONSENT", consentId, "患者主动撤回本次文本授权");
    }

    public ResultView analyze(String patient, String id, AnalyzeInput input) {
        Prepared prepared = transactions.execute(status -> prepare(patient, id, input));
        var task = prepared.task();
        if (prepared.created()) for(int attempt=0;attempt<3;attempt++) if(!processTask(task.id()))break;
        return resultView(ai.task(task.id()), id == null ? null : clinical.patientView(patient, id));
    }

    public void processDue() { for(String id:ai.due(clock.millis()))processTask(id); }
    private boolean processTask(String id) {
        var claim=transactions.execute(status -> claim(id));if(claim==null)return false;
        var task=claim.task();var names=catalog.departments(false).stream().map(CatalogMapper.Department::name).toList();
        long started=clock.millis();String body=null,error=null;
        try {
            if(task.provider().equals("EXTERNAL_TEXT"))throw new ProviderFailure("AI_PROVIDER_UNAVAILABLE");
            body=json.writeValueAsString(provider.analyze(task.symptomText(),names));
        } catch(JsonProcessingException failure) { error="AI_RESULT_SERIALIZATION_FAILED"; }
        catch(ProviderFailure failure) { error=failure.code(); }
        catch(RuntimeException failure) { error="AI_PROVIDER_UNAVAILABLE"; }
        final String response=body,code=error;
        return Boolean.FALSE.equals(transactions.execute(status -> {
            lockScope(task.patientId(),task.appointmentId());var current=ai.lockTask(id);var lease=ai.lease(id);
            if(!"RUNNING".equals(current.state()) || !claim.token().equals(lease.token()))return true;
            boolean done=saveAttempt(current,current.attempts(),started,response,code);
            if(!done)ai.finishTask(id,"PENDING",current.attempts(),code,null);
            ai.release(id,claim.token(),clock.millis());return done;
        }));
    }
    private Claim claim(String id) {
        var initial=ai.task(id);if(initial==null)return null;
        lockScope(initial.patientId(),initial.appointmentId());var task=ai.lockTask(id);
        if(!Set.of("PENDING","RUNNING").contains(task.state()))return null;
        var lease=ai.lease(id);long now=clock.millis();
        if(lease.token()!=null && lease.expiresAt()>now)return null;
        if(task.state().equals("RUNNING") && task.attempts()>0)
            ai.insertCallLog(new AiMapper.CallLog(UUID.randomUUID().toString(),id,task.provider(),task.purpose(),"ABANDONED",task.attempts(),0,"AI_LEASE_EXPIRED",now));
        var consent=ai.consent(task.consentId(),task.appointmentId(),task.patientId());
        if(consent==null || consent.revokedAt()!=null || !consent.accepted()) {
            ai.finishTask(id,"CANCELLED",task.attempts(),"AI_CONSENT_REVOKED",now);notifyTerminal(task,"本次AI授权已撤回，任务已取消");return null;
        }
        if(task.attempts()>=3) { ai.finishTask(id,"FAILED",task.attempts(),"AI_LEASE_EXHAUSTED",now);notifyTerminal(task,"AI辅助任务暂不可用，仍可自行挂号");return null; }
        String token=UUID.randomUUID().toString();ai.claim(id,token,now+30_000);return new Claim(ai.task(id),token);
    }

    private Prepared prepare(String patient, String id, AnalyzeInput input) {
        lockScope(patient, id);
        if (id != null) requirePatient(patient, id);
        if (input == null || input.idempotencyKey() == null || input.idempotencyKey().isBlank() || input.idempotencyKey().length() > 80)
            throw new ApiFailure(400, "INVALID_INPUT", "预问诊提交标识不能为空且长度受限");
        var existing = ai.taskByKey(patient, input.idempotencyKey());
        if (existing != null) {
            if (!Objects.equals(existing.appointmentId(), id)
                || !Objects.equals(existing.consentId(), input.consentId())
                || !Objects.equals(existing.symptomText(), input.symptomText()))
                throw new ApiFailure(409, "IDEMPOTENCY_CONFLICT", "提交标识已用于不同的预问诊请求");
            return new Prepared(existing, false);
        }
        validateText(input.symptomText());
        var consent = ai.consent(input.consentId(), id, patient);
        if (consent == null || !consent.accepted() || consent.revokedAt() != null)
            throw new ApiFailure(403, "AI_CONSENT_REQUIRED", "请先确认本次预问诊授权");
        if (!hash(input.symptomText()).equals(consent.textHash())) throw new ApiFailure(403, "AI_CONSENT_SCOPE_MISMATCH", "文本已变化，请重新确认本次授权");
        if (ai.consentUses(consent.id()) > 0) throw new ApiFailure(409, "AI_CONSENT_USED", "本次授权已使用，请重新确认授权");
        var material = id == null ? null : clinical.addMaterials(patient, id, input.symptomText(), true, null).materials().get(0);
        String materialId = material == null ? null : material.id();
        int materialVersion = material == null ? ai.latestDraftVersion(patient) + 1 : material.versionNo();
        var task = new AiMapper.Task(UUID.randomUUID().toString(), id, patient, materialId, consent.id(), input.idempotencyKey(),
            consent.provider(), consent.purpose(), "PENDING", 0, null, clock.millis(), null,
            consent.provider().equals("LOCAL_MOCK") ? provider.modelVersion() : "external-disabled", provider.promptVersion(),
            input.symptomText(), materialVersion, id == null);
        try { ai.insertTask(task); }
        catch (DuplicateKeyException duplicate) { throw new ApiFailure(409, "IDEMPOTENCY_CONFLICT", "提交标识已被使用，请查询已有结果"); }
        return new Prepared(task, true);
    }

    private boolean saveAttempt(AiMapper.Task task, int attempt, long started, String body, String error) {
        lockScope(task.patientId(), task.appointmentId());
        var consent = ai.consent(task.consentId(), task.appointmentId(), task.patientId());
        if (consent.revokedAt() != null) {
            ai.finishTask(task.id(), "CANCELLED", attempt, "AI_CONSENT_REVOKED", clock.millis());
            notifyTerminal(task,"本次AI授权已撤回，任务已取消");
            ai.insertCallLog(new AiMapper.CallLog(UUID.randomUUID().toString(), task.id(), task.provider(), task.purpose(),
                "CANCELLED", attempt, Math.max(0, clock.millis() - started), "AI_CONSENT_REVOKED", clock.millis()));
            return true;
        }
        ai.insertCallLog(new AiMapper.CallLog(UUID.randomUUID().toString(), task.id(), task.provider(), task.purpose(),
            error == null ? "SUCCEEDED" : "FAILED", attempt, Math.max(0, clock.millis() - started), error, clock.millis()));
        if (error == null) {
            ai.insertResult(new AiMapper.Result(UUID.randomUUID().toString(), task.id(), task.appointmentId(), task.materialId(),
                task.draft() ? task.materialVersion() : ai.nextResultVersion(task.appointmentId()) + 1, task.provider(), body, clock.millis()));
            ai.finishTask(task.id(), "SUCCEEDED", attempt, null, clock.millis());
            notifyTerminal(task,"AI辅助任务已完成，请查看结果");return true;
        }
        if (attempt == 3 || error.equals("AI_RESULT_SERIALIZATION_FAILED")) {
            ai.finishTask(task.id(), "FAILED", attempt, error, clock.millis());
            notifyTerminal(task,"AI辅助任务暂不可用，仍可自行挂号");return true;
        }
        return false;
    }
    private void notifyTerminal(AiMapper.Task task,String message) {
        delivery.publish(task.patientId(),"AI_RESULT",task.appointmentId()==null?task.id():task.appointmentId(),message,"ai-result:"+task.id());
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public List<ResultView> doctorResults(String doctor, String id) {
        var view = clinical.doctorView(doctor, id);
        if ("OFFLINE".equals(view.serviceMode())) return List.of();
        var results = appointmentResults(id, view);
        var consent = ai.latestSummaryConsent(id, booking.careScope(id).patientId());
        if (consent != null) {
            try {
                var current = summaryInput(view, consent);
                ai.doctorSummaries(id).stream().filter(summary -> summary.consentId().equals(consent.id()))
                    .map(summary -> summaryView(summary, consent, current, view)).forEach(results::add);
            } catch (ApiFailure ignored) { /* revoked sharing or a restricted input hides the doctor summary */ }
        }
        return results;
    }
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public List<ResultView> patientResults(String patient, String id) {
        var view = id == null ? null : clinical.patientView(patient, id);
        return id == null ? ai.drafts(patient).stream().map(task -> resultView(task, null)).toList() : appointmentResults(id, view);
    }
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public ResultView shareDraft(String patient, String id, String taskId, boolean shared) {
        lockScope(patient, id); requirePatient(patient, id);
        var task = ai.task(taskId);
        if (task == null || !task.patientId().equals(patient) || !task.draft()) throw new ApiFailure(404, "NOT_FOUND", "预约前摘要不存在");
        if (!shared) throw new ApiFailure(400, "SHARING_REQUIRED", "请确认将这份文本和摘要共享给当前预约医生");
        if (!task.state().equals("SUCCEEDED")) throw new ApiFailure(409, "AI_RESULT_UNAVAILABLE", "此任务没有可共享的摘要");
        var previous = ai.sharedDrafts(id).stream().filter(value -> value.taskId().equals(taskId)).findFirst().orElse(null);
        if (previous == null) {
            var material = clinical.addMaterials(patient, id, task.symptomText(), true, null).materials().get(0);
            previous = new AiMapper.DraftShare(taskId, id, material.id()); ai.shareDraft(previous);
        }
        var view = clinical.patientView(patient, id);
        var linked = previous;
        var material = view.materials().stream().filter(value -> value.id().equals(linked.materialId())).findFirst().orElseThrow();
        return resultView(task, view, material.id(), material.versionNo());
    }
    private List<ResultView> appointmentResults(String id, ClinicalService.ClinicalView view) {
        var result = new ArrayList<ResultView>();
        ai.tasks(id).stream().filter(task -> view.materials().stream().anyMatch(material -> material.id().equals(task.materialId())))
            .map(task -> resultView(task, view)).forEach(result::add);
        for (var share : ai.sharedDrafts(id)) view.materials().stream().filter(material -> material.id().equals(share.materialId())).findFirst()
            .ifPresent(material -> result.add(resultView(ai.task(share.taskId()), view, material.id(), material.versionNo())));
        return result;
    }
    @Transactional(readOnly = true)
    public List<AiMapper.CallMetadata> callMetadata() { return ai.callMetadata(); }

    @Transactional(readOnly = true)
    public BusinessAnswer business(String question) {
        if (question == null || question.isBlank() || question.length() > 500) throw new ApiFailure(400, "INVALID_INPUT", "咨询内容不能为空且不超过500字");
        String q = question.trim().toLowerCase();
        if (q.matches("(?s).*(胸痛|呼吸困难|意识不清|自伤|大出血).*"))
            return new BusinessAnswer("TRIAGE_ASSISTANCE", "出现高风险信号，请立即联系急救或人工医疗服务。导诊助手不能提供诊断或处方。", List.of());
        if (q.matches("(?s).*(症状|报告|检查结果|诊断|处方|用药|疼|痛|发热|发烧|咳嗽|乏力|恶心|头晕|我最近|我感觉).*"))
            return new BusinessAnswer("TRIAGE_ASSISTANCE", "我可以根据文字协助查找科室，但不能提供诊断或处方；你也可以直接自行挂号或联系医生。", List.of());
        if (q.matches("(?s).*(最好|排名|最厉害).*"))
            return new BusinessAnswer("RESTRICTED", "无法提供医生主观排名，请按真实科室和有效排班选择服务。", List.of());
        var hospital = catalog.hospital();
        if (q.contains("地址") || q.contains("在哪") || q.contains("位置"))
            return new BusinessAnswer("LOCAL_FACTS", hospital.name() + "地址：" + hospital.address(), List.of("hospital.address"));
        if (q.contains("流程") || q.contains("预约") || q.contains("挂号") || q.contains("支付"))
            return new BusinessAnswer("LOCAL_FACTS", hospital.description(), List.of("hospital.description"));
        for (var department : catalog.departments(false)) if (q.contains(department.name().toLowerCase()))
            return new BusinessAnswer("LOCAL_FACTS", department.name() + "：" + department.description(), List.of("department:" + department.id()));
        throw new ApiFailure(422, "BUSINESS_TOPIC_UNSUPPORTED", "我只能回答医院地址、服务流程和科室职责等院内业务信息");
    }

    @Transactional(readOnly = true)
    public TriageAnswer triage(String question, MultipartFile image) {
        validateText(question);
        boolean imageReceived = image != null && !image.isEmpty();
        if (imageReceived) ClinicalAttachmentValidator.validate(image);
        var analysis = provider.analyze(question, catalog.departments(false).stream().map(CatalogMapper.Department::name).toList());
        return new TriageAnswer("TRIAGE_ASSISTANCE", analysis.safetyNotice() + " " + analysis.summary(),
            analysis.departmentHint(), imageReceived, List.of("configured-departments"));
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public SummaryConsentView summaryConsent(String patient, String id, SummaryConsentInput input) {
        lockScope(patient, id);
        var appointment = requirePatient(patient, id);
        if (!"ONLINE".equals(appointment.serviceMode())) throw new ApiFailure(409, "OFFLINE_NO_CHAT", "线下面诊不提供医患图文摘要");
        if (input == null || !Set.of("LOCAL_MOCK", "EXTERNAL_TEXT").contains(Objects.toString(input.provider(), ""))
            || !"DOCTOR_SYMPTOM_SUMMARY".equals(input.purpose()) || !"EXACT_MESSAGES_AND_MATERIALS".equals(input.scope()))
            throw new ApiFailure(400, "INVALID_INPUT", "仅支持本次医患图文的精确范围摘要授权");
        var view = clinical.patientView(patient, id);
        var messageIds = normalizeIds(input.messageIds()); var materialIds = normalizeIds(input.materialIds());
        if (input.accepted() && messageIds.isEmpty() && materialIds.isEmpty()) throw new ApiFailure(400, "INVALID_INPUT", "至少选择一条消息或一份材料");
        validateSummaryScope(view, messageIds, materialIds);
        long now = clock.millis();
        var previousConsents = ai.summaryConsents(id, patient);
        if (!previousConsents.isEmpty()) {
            long latestCreatedAt = previousConsents.stream().mapToLong(AiMapper.SummaryConsent::createdAt).max().orElse(now);
            now = Math.max(now, latestCreatedAt + 1);
        }
        String consentId = UUID.randomUUID().toString();
        ai.insertSummaryConsent(new AiMapper.SummaryConsent(consentId, id, patient, input.provider(), input.purpose(), input.scope(),
            String.join(",", messageIds), String.join(",", materialIds), input.accepted(), now, null));
        audit.record(patient, input.accepted() ? "DOCTOR_SUMMARY_CONSENT_ACCEPTED" : "DOCTOR_SUMMARY_CONSENT_REFUSED",
            "AI_SUMMARY_CONSENT", consentId, "授权范围：" + messageIds.size() + "条消息、" + materialIds.size() + "份材料");
        return summaryConsentView(new AiMapper.SummaryConsent(consentId, id, patient, input.provider(), input.purpose(), input.scope(),
            String.join(",", messageIds), String.join(",", materialIds), input.accepted(), now, null));
    }

    @Transactional(readOnly = true)
    public List<SummaryConsentView> summaryConsents(String patient, String id) {
        lockScope(patient, id); requirePatient(patient, id);
        return ai.summaryConsents(id, patient).stream().map(this::summaryConsentView).toList();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void revokeSummaryConsent(String patient, String id, String consentId) {
        lockScope(patient, id); requirePatient(patient, id);
        var consent = ai.summaryConsent(consentId, id, patient);
        if (consent == null) throw new ApiFailure(404, "NOT_FOUND", "摘要授权不存在");
        ai.revokeSummaryConsent(consentId, patient, clock.millis());
        audit.record(patient, "DOCTOR_SUMMARY_CONSENT_REVOKED", "AI_SUMMARY_CONSENT", consentId, "患者撤回医生症状摘要授权");
    }

    @Transactional(noRollbackFor = ApiFailure.class, isolation = Isolation.READ_COMMITTED)
    public ConversationSummary summarizeConversation(String doctor, String id) {
        var view = clinical.doctorView(doctor, id);
        if ("OFFLINE".equals(view.serviceMode())) throw new ApiFailure(409, "OFFLINE_NO_CHAT", "线下面诊不提供医患图文摘要");
        var scope = ai.latestSummaryConsent(id, booking.careScope(id).patientId());
        if (scope == null) throw new ApiFailure(403, "AI_SUMMARY_CONSENT_REQUIRED", "患者尚未授权本次摘要范围");
        var input = summaryInput(view, scope);
        if (input.messageCount() == 0 && input.text().isBlank()) throw new ApiFailure(409, "NO_MESSAGES", "授权范围内暂无可摘要的医患图文");
        var previous = ai.doctorSummaries(id).stream().filter(value -> value.consentId().equals(scope.id())
            && value.inputHash().equals(input.hash()) && "SUCCEEDED".equals(value.state())).findFirst().orElse(null);
        if (previous != null) return new ConversationSummary(readAnalysis(previous.body()), previous.messageCount(), previous.provider());
        long now = clock.millis(); int version = ai.nextDoctorSummaryVersion(id) + 1; String body = null; String error = null; Analysis analysis = null;
        try {
            if ("EXTERNAL_TEXT".equals(scope.provider())) throw new ProviderFailure("AI_PROVIDER_UNAVAILABLE");
            analysis = provider.analyze(input.text(), catalog.departments(false).stream().map(CatalogMapper.Department::name).toList());
            body = json.writeValueAsString(analysis);
        } catch (ProviderFailure failure) { error = failure.code(); }
        catch (JsonProcessingException | RuntimeException failure) { error = "AI_PROVIDER_UNAVAILABLE"; }
        ai.insertDoctorSummary(new AiMapper.DoctorSummary(UUID.randomUUID().toString(), id, doctor, scope.id(), version, input.hash(),
            scope.provider(), error == null ? "SUCCEEDED" : "FAILED", error, body, input.messageCount(), now, clock.millis(),
            "LOCAL_MOCK".equals(scope.provider()) ? provider.modelVersion() : "external-disabled", provider.promptVersion()));
        audit.record(doctor, error == null ? "DOCTOR_SUMMARY_GENERATED" : "DOCTOR_SUMMARY_FAILED", "APPOINTMENT", id,
            "根据患者授权范围生成医生可见症状摘要");
        if (error != null) throw new ApiFailure(503, error, "摘要暂不可用，已有摘要仍可查看");
        return new ConversationSummary(analysis, input.messageCount(), scope.provider());
    }

    private SummaryConsentView summaryConsentView(AiMapper.SummaryConsent consent) {
        return new SummaryConsentView(consent.id(), consent.provider(), consent.purpose(), consent.scope(), consent.accepted(),
            splitIds(consent.messageIds()), splitIds(consent.materialIds()), consent.createdAt(), consent.revokedAt());
    }

    private void validateSummaryScope(ClinicalService.ClinicalView view, List<String> messageIds, List<String> materialIds) {
        var messages = view.messages().stream().map(ClinicalMapper.Message::id).collect(java.util.stream.Collectors.toSet());
        if (!messages.containsAll(messageIds)) throw new ApiFailure(400, "INVALID_INPUT", "授权消息不属于本次问诊");
        var materials = view.materials().stream().filter(ClinicalService.MaterialView::shared).collect(java.util.stream.Collectors.toMap(ClinicalService.MaterialView::id, value -> value));
        if (!materials.keySet().containsAll(materialIds)) throw new ApiFailure(400, "SHARING_REQUIRED", "授权材料必须是患者已共享的材料");
    }

    private SummaryInput summaryInput(ClinicalService.ClinicalView view, AiMapper.SummaryConsent consent) {
        var messageIds = new HashSet<>(splitIds(consent.messageIds()));
        var materialIds = new HashSet<>(splitIds(consent.materialIds()));
        var selectedMessages = view.messages().stream().filter(value -> messageIds.contains(value.id())).toList();
        var selectedMaterials = view.materials().stream().filter(value -> materialIds.contains(value.id()) && value.shared()).toList();
        if (selectedMessages.size() != messageIds.size() || selectedMaterials.size() != materialIds.size())
            throw new ApiFailure(403, "AI_SUMMARY_SCOPE_UNAVAILABLE", "摘要授权范围已失效");
        var text = new StringBuilder();
        selectedMessages.forEach(message -> text.append(message.authorRole()).append("：").append(message.body()).append('\n'));
        selectedMaterials.forEach(material -> text.append("材料版本").append(material.versionNo()).append("：").append(material.symptomText()).append('\n'));
        String value = text.toString().trim();
        return new SummaryInput(value, hash(value + "|" + String.join(",", splitIds(consent.messageIds())) + "|" + String.join(",", splitIds(consent.materialIds()))), selectedMessages.size());
    }

    private ResultView summaryView(AiMapper.DoctorSummary summary, AiMapper.SummaryConsent consent, SummaryInput current,
        ClinicalService.ClinicalView view) {
        Analysis analysis = summary.body() == null ? null : readAnalysis(summary.body());
        boolean stale = !summary.inputHash().equals(current.hash());
        if (analysis != null && view.consultation() != null && current.text().contains(ReviewPolicyService.LIMITED)) analysis = null;
        return new ResultView(summary.id(), summary.state(), summary.provider(), "DOCTOR_SYMPTOM_SUMMARY",
            1, summary.errorCode(), summary.versionNo(), stale, analysis, analysis == null ? List.of() : recommendations(analysis.departmentHint()),
            null, 0, consent.id(), summary.modelVersion(), summary.promptVersion());
    }

    private static List<String> normalizeIds(List<String> values) {
        return values == null ? List.of() : values.stream().filter(Objects::nonNull).map(String::trim).filter(value -> !value.isEmpty()).distinct().sorted().toList();
    }

    private static List<String> splitIds(String values) {
        return values == null || values.isBlank() ? List.of() : Arrays.stream(values.split(",")).map(String::trim).filter(value -> !value.isEmpty()).toList();
    }

    private ResultView resultView(AiMapper.Task task, ClinicalService.ClinicalView view) {
        return resultView(task, view, task.materialId(), task.materialVersion());
    }
    private ResultView resultView(AiMapper.Task task, ClinicalService.ClinicalView view, String materialId, int materialVersion) {
        var result = ai.result(task.id());
        var latest = view == null ? null : view.materials().stream().findFirst().orElse(null);
        boolean stale = view == null ? ai.latestDraftVersion(task.patientId()) > task.materialVersion()
            : latest != null && !latest.id().equals(materialId);
        Analysis analysis = result == null ? null : readAnalysis(result.body());
        if (view != null && view.consultation() != null && materialId != null && policy.limited(view.consultation().appointmentId(), "MATERIAL", materialId))
            analysis = new Analysis(ReviewPolicyService.LIMITED, "", "", "", "", "", task.provider());
        return new ResultView(task.id(), task.state(), task.provider(), task.purpose(), task.attempts(), task.errorCode(),
            result == null ? 0 : result.versionNo(), stale, analysis, analysis == null ? List.of() : recommendations(analysis.departmentHint()),
            materialId, materialVersion, task.consentId(), task.modelVersion(), task.promptVersion());
    }
    private Analysis readAnalysis(String body) {
        try { return json.readValue(body, Analysis.class); }
        catch (JsonProcessingException failure) { throw new ApiFailure(503, "AI_RESULT_UNAVAILABLE", "已保存的辅助摘要暂不可用"); }
    }
    private List<DoctorRecommendation> recommendations(String hint) {
        var department = catalog.departments(false).stream().filter(value -> value.name().equals(hint)).findFirst().orElse(null);
        if (department == null) return List.of();
        var result = new ArrayList<DoctorRecommendation>();
        for (var doctor : catalog.doctors(false, department.id())) {
            for (var schedule : catalog.schedules(false, doctor.id(), department.id(), null, null)) {
                if (schedule.advanceAvailable() || schedule.walkInAvailable()) {
                    result.add(new DoctorRecommendation(doctor.id(), doctor.displayName(), department.id(), department.name(),
                        schedule.id(), schedule.clinicDate(), schedule.session(), schedule.serviceMode())); break;
                }
            }
            if (result.size() >= 5) break;
        }
        return result;
    }
    private BookingService.CareScope requirePatient(String patient, String id) {
        var appointment = booking.careScope(id);
        if (appointment == null || !appointment.patientId().equals(patient)) throw new ApiFailure(404, "NOT_FOUND", "问诊不存在");
        return appointment;
    }
    private void lockScope(String patient, String id) {
        ai.ensurePatient(patient); ai.lockPatient(patient);
        if (id != null) booking.lockCareScope(id);
    }
    private static void validateText(String text) {
        if (text == null || text.isBlank() || text.length() > 10000) throw new ApiFailure(400, "INVALID_INPUT", "症状文本不能为空且不能超过10000字");
    }
    private static String hash(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
