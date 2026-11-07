package cn.medical.clinical;

import cn.medical.booking.BookingService;
import cn.medical.identity.ApiFailure;
import cn.medical.reception.ReceptionService;
import cn.medical.governance.ReviewPolicyService;
import cn.medical.governance.AuditService;
import cn.medical.delivery.DeliveryService;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.*;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ClinicalService {
    public record AttachmentView(String id, String originalName, String mediaType, long sizeBytes, long createdAt) {}
    public record MaterialView(String id, int versionNo, String symptomText, boolean shared, long createdAt, List<AttachmentView> attachments) {}
    public record ClinicalView(ClinicalMapper.Consultation consultation, List<MaterialView> materials,
        List<ClinicalMapper.Message> messages, ClinicalMapper.Result result, List<ClinicalMapper.Correction> corrections) {}
    public record Download(ByteArrayResource resource, String mediaType, String originalName) {}
    public record ReviewSnapshot(List<MaterialView> materials, List<ClinicalMapper.Result> results,
        List<ClinicalMapper.Message> messages, List<ClinicalMapper.Correction> corrections) {}
    private final ClinicalMapper clinical;
    private final ReceptionService reception;
    private final BookingService booking;
    private final Clock clock;
    private final ClinicalFileStore files;
    private final ReviewPolicyService policy;
    private final AuditService audit;
    private final DeliveryService delivery;

    public ClinicalService(ClinicalMapper clinical, ReceptionService reception, BookingService booking, Clock clock,
        ClinicalFileStore files, ReviewPolicyService policy, AuditService audit, DeliveryService delivery) {
        this.clinical = clinical; this.reception = reception; this.booking = booking; this.clock = clock;
        this.files = files;
        this.policy = policy; this.audit = audit;
        this.delivery = delivery;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ClinicalView patientView(String patient, String id) {
        requirePatient(patient, id);
        booking.lockCareScope(id); requirePatient(patient,id);
        audit.record(patient, "MEDICAL_RECORD_VIEWED", "APPOINTMENT", id, "患者查看本人问诊");
        return view(id);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ClinicalView doctorView(String doctor, String id) {
        requireDoctor(doctor, id);
        booking.lockCareScope(id); requireDoctor(doctor,id);
        audit.record(doctor, "MEDICAL_RECORD_VIEWED", "APPOINTMENT", id, "当前负责医生查看本次问诊");
        return view(id, true);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ClinicalView addMaterials(String patient, String id, String symptoms, boolean shared, MultipartFile[] files) {
        booking.lockCareScope(id);
        var appointment = requirePatient(patient, id);
        if (!appointment.state().equals("CONFIRMED") || (clinical.consultation(id) != null && clinical.consultation(id).state().equals("CLOSED")))
            throw new ApiFailure(409, "CLINICAL_NOT_ALLOWED", "已结束或未确认的预约不能提交诊前材料");
        if (!shared) throw new ApiFailure(400, "SHARING_REQUIRED", "提交医疗材料前必须确认共享授权");
        if (symptoms == null || symptoms.isBlank() || symptoms.length() > 10000) throw new ApiFailure(400, "INVALID_INPUT", "症状文本不能为空且不能超过10000字");
        MultipartFile[] submitted = files == null ? new MultipartFile[0] : files;
        if (submitted.length > 5) throw new ApiFailure(400, "ATTACHMENT_LIMIT", "每次最多提交5份医疗附件");
        for (var file : submitted) ClinicalAttachmentValidator.validate(file);
        var materialId = UUID.randomUUID().toString();
        int version = clinical.nextMaterialVersion(id) + 1;
        long now = clock.millis();
        ensureConsultation(id, now);
        var stored = new ArrayList<ClinicalMapper.Attachment>();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) for (var attachment : stored) {
                    try { ClinicalService.this.files.delete(attachment.storageKey()); }
                    catch (IOException error) { org.slf4j.LoggerFactory.getLogger(ClinicalService.class).warn("Private attachment rollback cleanup failed", error); }
                }
            }
        });
        for (var file : submitted) {
            String mediaType = ClinicalAttachmentValidator.validate(file);
            String attachmentId = UUID.randomUUID().toString();
            String key = id + "/" + attachmentId + ".bin";
            String originalName = safeName(file.getOriginalFilename());
            try { this.files.write(key, file.getBytes()); }
            catch (IOException error) { throw new ApiFailure(503, "ATTACHMENT_STORAGE_UNAVAILABLE", "医疗附件暂时无法保存"); }
            stored.add(new ClinicalMapper.Attachment(attachmentId, materialId, id, originalName, mediaType, file.getSize(), key, now));
        }
        clinical.insertMaterial(new ClinicalMapper.Material(materialId, id, version, symptoms.trim(), true, now));
        stored.forEach(clinical::insertAttachment);
        audit.record(patient, "MATERIAL_SHARED", "MATERIAL", materialId, "主动共享本次材料及附件");
        return view(id);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ClinicalView message(String actor, String id, String role, String body) {
        booking.lockCareScope(id);
        var appointment = role.equals("PATIENT") ? requirePatient(actor, id) : requireDoctor(actor, id);
        var queue = reception.queueForAppointment(id);
        if (!appointment.state().equals("CONFIRMED") || queue == null || !queue.state().equals("IN_CONSULTATION")) throw new ApiFailure(409, "CONSULTATION_NOT_OPEN", "叫号并开始接诊后才能发送图文消息");
        if (body == null || body.isBlank() || body.length() > 10000) throw new ApiFailure(400, "INVALID_INPUT", "消息不能为空且不能超过10000字");
        ensureConsultation(id, clock.millis());
        String messageId=UUID.randomUUID().toString();
        clinical.insertMessage(new ClinicalMapper.Message(messageId, id, actor, role, body.trim(), clock.millis()));
        delivery.publish(role.equals("PATIENT")?appointment.doctorId():appointment.patientId(),"CLINICAL_MESSAGE",id,"本次问诊有新图文消息，请进入诊室查看","clinical-message:"+messageId);
        return view(appointment.id(), role.equals("DOCTOR"));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ClinicalView submitResult(String doctor, String id, String body) {
        booking.lockCareScope(id);
        var appointment = requireDoctor(doctor, id);
        var queue = reception.queueForAppointment(id);
        if (queue == null || !queue.state().equals("IN_CONSULTATION")) throw new ApiFailure(409, "ENCOUNTER_NOT_OPEN", "只有接诊中的预约可以提交结果");
        if (body == null || body.isBlank() || body.length() > 20000) throw new ApiFailure(400, "INVALID_INPUT", "问诊结果不能为空且不能超过20000字");
        long now = clock.millis(); ensureConsultation(id, now);
        String type = appointment.serviceMode().equals("ONLINE") ? "ONLINE_CHAT" : "OFFLINE_RESULT";
        clinical.insertResult(new ClinicalMapper.Result(UUID.randomUUID().toString(), id, clinical.nextResultVersion(id) + 1, doctor, type, body.trim(), now));
        clinical.markReplied(id); clinical.closeConsultation(id, now);
        reception.completeEncounter(doctor, id); booking.completeAfterEncounter(id);
        audit.record(doctor, "CLINICAL_RESULT_SUBMITTED", "APPOINTMENT", id, "提交结果并结束接诊");
        return view(id, true);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ClinicalView correction(String doctor, String id, String reason, String body) {
        booking.lockCareScope(id);
        requireDoctor(doctor, id);
        var result = clinical.latestResult(id);
        var consultation = clinical.consultation(id);
        if (result == null || consultation == null || !consultation.state().equals("CLOSED")) throw new ApiFailure(409, "CORRECTION_NOT_ALLOWED", "只有已结束问诊才允许追加更正");
        if (reason == null || reason.isBlank() || reason.length() > 500 || body == null || body.isBlank() || body.length() > 20000)
            throw new ApiFailure(400, "INVALID_INPUT", "更正原因和内容不能为空且长度受限");
        clinical.insertCorrection(new ClinicalMapper.Correction(UUID.randomUUID().toString(), result.id(), id, doctor, reason.trim(), body.trim(), clock.millis()));
        audit.record(doctor, "CLINICAL_RESULT_CORRECTED", "RESULT", result.id(), "追加更正；医疗理由保留于受控更正记录");
        return view(id, true);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Download download(String actor, String role, String attachmentId) {
        var attachment = clinical.attachment(attachmentId);
        if (attachment == null) throw new ApiFailure(404, "NOT_FOUND", "医疗附件不存在");
        if (role.equals("PATIENT")) requirePatient(actor, attachment.appointmentId()); else requireDoctor(actor, attachment.appointmentId());
        booking.lockCareScope(attachment.appointmentId());
        if (role.equals("PATIENT")) requirePatient(actor, attachment.appointmentId()); else requireDoctor(actor, attachment.appointmentId());
        if (role.equals("DOCTOR")) {
            var material = clinical.material(attachment.materialId());
            if (material == null || !material.shared()) throw new ApiFailure(403, "SHARING_REVOKED", "患者已撤回该材料共享授权");
        }
        policy.requireVisible(attachment.appointmentId(), "MATERIAL", attachment.materialId());
        policy.requireVisible(attachment.appointmentId(), "ATTACHMENT", attachment.id());
        var result = readAttachment(attachment);
        audit.record(actor, "MEDICAL_ATTACHMENT_DOWNLOADED", "ATTACHMENT", attachment.id(), "当前预约授权下载");
        return result;
    }

    private ClinicalView view(String id) { return view(id, false); }
    private ClinicalView view(String id, boolean doctor) {
        var materials = clinical.materials(id).stream().filter(material -> !doctor || material.shared()).map(material -> new MaterialView(material.id(), material.versionNo(), policy.display(id, "MATERIAL", material.id(), material.symptomText()), material.shared(), material.createdAt(),
            policy.limited(id, "MATERIAL", material.id()) ? List.of() : attachmentViews(material.id(), true))).toList();
        var messages = clinical.messages(id).stream().map(m -> new ClinicalMapper.Message(m.id(), m.appointmentId(), m.authorId(), m.authorRole(), policy.display(id,"MESSAGE",m.id(),m.body()),m.createdAt())).toList();
        var result = clinical.latestResult(id);
        if (result != null) result = new ClinicalMapper.Result(result.id(), id, result.versionNo(), result.authorId(), result.encounterType(), policy.display(id,"RESULT",result.id(),result.body()),result.createdAt());
        var corrections = clinical.corrections(id).stream().map(c -> new ClinicalMapper.Correction(c.id(),c.resultId(),id,c.authorId(),
            policy.limited(id,"CORRECTION",c.id()) || policy.limited(id,"RESULT",c.resultId()) ? ReviewPolicyService.LIMITED : c.reason(),
            policy.limited(id,"RESULT",c.resultId()) ? ReviewPolicyService.LIMITED : policy.display(id,"CORRECTION",c.id(),c.body()),c.createdAt())).toList();
        return new ClinicalView(clinical.consultation(id), materials, messages, result, corrections);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ClinicalView revokeSharing(String patient, String id) {
        booking.lockCareScope(id);
        requirePatient(patient, id);
        clinical.revokeSharing(id);
        audit.record(patient, "MATERIAL_SHARING_REVOKED", "APPOINTMENT", id, "患者主动撤回材料共享");
        return view(id);
    }
    // Reviewers see preserved originals only through an independently authorized, audited application seam.
    @Transactional
    public ReviewSnapshot reviewSnapshot(String actor, String reviewId) {
        var review = policy.active(actor, reviewId, null); String id = review.appointmentId();
        boolean record = !review.scope().equals("ATTACHMENTS"), attachments = !review.scope().equals("RECORD");
        var materials = clinical.materials(id).stream().map(m -> new MaterialView(m.id(),m.versionNo(),record ? m.symptomText() : null,m.shared(),m.createdAt(),attachments ? attachmentViews(m.id(),false) : List.of())).toList();
        return new ReviewSnapshot(materials,record ? clinical.results(id) : List.of(),record ? clinical.messages(id) : List.of(),record ? clinical.corrections(id) : List.of());
    }
    @Transactional
    public boolean ownsReviewTarget(String actor, String reviewId, String type, String target) {
        var snapshot = reviewSnapshot(actor,reviewId);
        return switch (type) {
            case "MATERIAL" -> snapshot.materials().stream().anyMatch(m -> m.id().equals(target));
            case "ATTACHMENT" -> snapshot.materials().stream().flatMap(m -> m.attachments().stream()).anyMatch(a -> a.id().equals(target));
            case "RESULT" -> snapshot.results().stream().anyMatch(r -> r.id().equals(target));
            case "MESSAGE" -> snapshot.messages().stream().anyMatch(m -> m.id().equals(target));
            case "CORRECTION" -> snapshot.corrections().stream().anyMatch(c -> c.id().equals(target));
            default -> false;
        };
    }
    @Transactional
    public Download reviewDownload(String actor, String reviewId, String attachmentId) {
        var review = policy.active(actor,reviewId,"ATTACHMENTS");
        var attachment = clinical.attachment(attachmentId);
        if (attachment == null || !attachment.appointmentId().equals(review.appointmentId())) throw new ApiFailure(404,"NOT_FOUND","附件不属于此次审核授权");
        var result = readAttachment(attachment);
        audit.record(actor,"MEDICAL_REVIEW_ATTACHMENT_DOWNLOADED","ATTACHMENT",attachmentId,review.reason());
        return result;
    }
    private List<AttachmentView> attachmentViews(String material, boolean restricted) {
        return clinical.attachments(material).stream().map(a -> new AttachmentView(a.id(), restricted && policy.limited(a.appointmentId(),"ATTACHMENT",a.id()) ? ReviewPolicyService.LIMITED : a.originalName(),a.mediaType(),a.sizeBytes(),a.createdAt())).toList();
    }
    private Download readAttachment(ClinicalMapper.Attachment attachment) {
        try { return new Download(new ByteArrayResource(files.read(attachment.storageKey())),attachment.mediaType(),attachment.originalName()); }
        catch (IOException error) { throw new ApiFailure(404,"NOT_FOUND","医疗附件不可用"); }
    }
    private BookingService.CareScope requirePatient(String patient, String id) {
        var appointment = booking.careScope(id);
        if (appointment == null || !appointment.patientId().equals(patient)) throw new ApiFailure(404, "NOT_FOUND", "问诊不存在");
        return appointment;
    }
    private BookingService.CareScope requireDoctor(String doctor, String id) {
        var appointment = booking.careScope(id);
        if (appointment == null || !appointment.doctorId().equals(doctor)) throw new ApiFailure(404, "NOT_FOUND", "问诊不存在");
        if (appointment.state().equals("PENDING_SUBSTITUTION")) throw new ApiFailure(403, "SUBSTITUTION_PENDING", "等待患者确认替诊，暂不可访问材料");
        return appointment;
    }
    private void ensureConsultation(String id, long now) {
        if (clinical.consultation(id) == null) clinical.insertConsultation(id, now);
    }
    private String safeName(String name) {
        if (name != null && name.chars().anyMatch(Character::isISOControl)) throw new ApiFailure(400, "ATTACHMENT_INVALID", "医疗附件名称无效");
        String value;
        try { value = name == null ? "attachment" : Path.of(name).getFileName().toString(); }
        catch (java.nio.file.InvalidPathException | NullPointerException error) { throw new ApiFailure(400, "ATTACHMENT_INVALID", "医疗附件名称无效"); }
        return value.length() > 255 ? value.substring(value.length() - 255) : value;
    }
}
