package cn.medical.clinical;

import cn.medical.booking.BookingService;
import cn.medical.identity.ApiFailure;
import cn.medical.reception.ReceptionMapper;
import cn.medical.reception.ReceptionService;
import java.io.IOException;
import java.nio.file.*;
import java.time.Clock;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ClinicalService {
    public record AttachmentView(String id, String originalName, String mediaType, long sizeBytes, long createdAt) {}
    public record MaterialView(String id, int versionNo, String symptomText, boolean shared, long createdAt, List<AttachmentView> attachments) {}
    public record ClinicalView(ClinicalMapper.Consultation consultation, List<MaterialView> materials,
        List<ClinicalMapper.Message> messages, ClinicalMapper.Result result, List<ClinicalMapper.Correction> corrections) {}
    public record Download(ByteArrayResource resource, String mediaType, String originalName) {}
    private final ClinicalMapper clinical;
    private final ReceptionService reception;
    private final BookingService booking;
    private final Clock clock;
    private final Path attachmentRoot;

    public ClinicalService(ClinicalMapper clinical, ReceptionService reception, BookingService booking, Clock clock,
        @Value("${medical.attachments-root:data/medical-attachments}") String attachmentRoot) {
        this.clinical = clinical; this.reception = reception; this.booking = booking; this.clock = clock;
        this.attachmentRoot = Path.of(attachmentRoot).toAbsolutePath().normalize();
    }

    @Transactional
    public ClinicalView patientView(String patient, String id) {
        requirePatient(patient, id);
        return view(id);
    }

    @Transactional
    public ClinicalView doctorView(String doctor, String id) {
        requireDoctor(doctor, id);
        return view(id);
    }

    @Transactional
    public ClinicalView addMaterials(String patient, String id, String symptoms, boolean shared, MultipartFile[] files) {
        var appointment = requirePatient(patient, id);
        if (!Set.of("CONFIRMED", "COMPLETED").contains(appointment.state())) throw new ApiFailure(409, "CLINICAL_NOT_ALLOWED", "当前预约不能提交诊前材料");
        if (!shared) throw new ApiFailure(400, "SHARING_REQUIRED", "提交医疗材料前必须确认共享授权");
        if (symptoms == null || symptoms.isBlank() || symptoms.length() > 10000) throw new ApiFailure(400, "INVALID_INPUT", "症状文本不能为空且不能超过10000字");
        MultipartFile[] submitted = files == null ? new MultipartFile[0] : files;
        if (submitted.length > 5) throw new ApiFailure(400, "ATTACHMENT_LIMIT", "每次最多提交5份医疗附件");
        var materialId = UUID.randomUUID().toString();
        int version = clinical.nextMaterialVersion(id) + 1;
        long now = clock.millis();
        ensureConsultation(id, now);
        var stored = new ArrayList<ClinicalMapper.Attachment>();
        for (var file : submitted) {
            String mediaType = ClinicalAttachmentValidator.validate(file);
            String attachmentId = UUID.randomUUID().toString();
            String key = id + "/" + attachmentId + ".bin";
            Path target = safePath(key);
            try { Files.createDirectories(target.getParent()); Files.write(target, file.getBytes(), StandardOpenOption.CREATE_NEW); }
            catch (IOException error) { throw new ApiFailure(503, "ATTACHMENT_STORAGE_UNAVAILABLE", "医疗附件暂时无法保存"); }
            stored.add(new ClinicalMapper.Attachment(attachmentId, materialId, id, safeName(file.getOriginalFilename()), mediaType, file.getSize(), key, now));
        }
        clinical.insertMaterial(new ClinicalMapper.Material(materialId, id, version, symptoms.trim(), true, now));
        stored.forEach(clinical::insertAttachment);
        return view(id);
    }

    @Transactional
    public ClinicalView message(String actor, String id, String role, String body) {
        var appointment = role.equals("PATIENT") ? requirePatient(actor, id) : requireDoctor(actor, id);
        var queue = reception.queueForAppointment(id);
        if (queue == null || !Set.of("IN_CONSULTATION").contains(queue.state())) throw new ApiFailure(409, "CONSULTATION_NOT_OPEN", "叫号并开始接诊后才能发送图文消息");
        if (body == null || body.isBlank() || body.length() > 10000) throw new ApiFailure(400, "INVALID_INPUT", "消息不能为空且不能超过10000字");
        ensureConsultation(id, clock.millis());
        clinical.insertMessage(new ClinicalMapper.Message(UUID.randomUUID().toString(), id, actor, role, body.trim(), clock.millis()));
        return view(appointment.id());
    }

    @Transactional
    public ClinicalView submitResult(String doctor, String id, String body) {
        var appointment = requireDoctor(doctor, id);
        var queue = reception.queueForAppointment(id);
        if (queue == null || !queue.state().equals("IN_CONSULTATION")) throw new ApiFailure(409, "ENCOUNTER_NOT_OPEN", "只有接诊中的预约可以提交结果");
        if (body == null || body.isBlank() || body.length() > 20000) throw new ApiFailure(400, "INVALID_INPUT", "问诊结果不能为空且不能超过20000字");
        long now = clock.millis(); ensureConsultation(id, now);
        String type = appointment.serviceMode().equals("ONLINE") ? "ONLINE_CHAT" : "OFFLINE_RESULT";
        clinical.insertResult(new ClinicalMapper.Result(UUID.randomUUID().toString(), id, clinical.nextResultVersion(id) + 1, doctor, type, body.trim(), now));
        clinical.markReplied(id); clinical.closeConsultation(id, now);
        reception.completeEncounter(doctor, id); booking.completeAfterEncounter(id);
        return view(id);
    }

    @Transactional
    public ClinicalView correction(String doctor, String id, String reason, String body) {
        requireDoctor(doctor, id);
        var result = clinical.latestResult(id);
        var consultation = clinical.consultation(id);
        if (result == null || consultation == null || !consultation.state().equals("CLOSED")) throw new ApiFailure(409, "CORRECTION_NOT_ALLOWED", "只有已结束问诊才允许追加更正");
        if (reason == null || reason.isBlank() || reason.length() > 500 || body == null || body.isBlank() || body.length() > 20000)
            throw new ApiFailure(400, "INVALID_INPUT", "更正原因和内容不能为空且长度受限");
        clinical.insertCorrection(new ClinicalMapper.Correction(UUID.randomUUID().toString(), result.id(), id, doctor, reason.trim(), body.trim(), clock.millis()));
        return view(id);
    }

    @Transactional(readOnly = true)
    public Download download(String actor, String role, String attachmentId) {
        var attachment = clinical.attachment(attachmentId);
        if (attachment == null) throw new ApiFailure(404, "NOT_FOUND", "医疗附件不存在");
        if (role.equals("PATIENT")) requirePatient(actor, attachment.appointmentId()); else requireDoctor(actor, attachment.appointmentId());
        try { return new Download(new ByteArrayResource(Files.readAllBytes(safePath(attachment.storageKey()))), attachment.mediaType(), attachment.originalName()); }
        catch (IOException error) { throw new ApiFailure(404, "NOT_FOUND", "医疗附件不可用"); }
    }

    private ClinicalView view(String id) {
        var materials = clinical.materials(id).stream().map(material -> new MaterialView(material.id(), material.versionNo(), material.symptomText(), material.shared(), material.createdAt(), clinical.attachments(material.id()).stream().map(a -> new AttachmentView(a.id(), a.originalName(), a.mediaType(), a.sizeBytes(), a.createdAt())).toList())).toList();
        return new ClinicalView(clinical.consultation(id), materials, clinical.messages(id), clinical.latestResult(id), clinical.corrections(id));
    }
    private ClinicalMapper.Appointment requirePatient(String patient, String id) {
        var appointment = clinical.appointment(id);
        if (appointment == null || !appointment.patientId().equals(patient)) throw new ApiFailure(404, "NOT_FOUND", "问诊不存在");
        return appointment;
    }
    private ClinicalMapper.Appointment requireDoctor(String doctor, String id) {
        var appointment = clinical.appointment(id);
        if (appointment == null || !appointment.doctorId().equals(doctor)) throw new ApiFailure(404, "NOT_FOUND", "问诊不存在");
        return appointment;
    }
    private void ensureConsultation(String id, long now) {
        if (clinical.consultation(id) == null) clinical.insertConsultation(id, now);
    }
    private Path safePath(String key) {
        Path path = attachmentRoot.resolve(key).normalize();
        if (!path.startsWith(attachmentRoot)) throw new ApiFailure(400, "ATTACHMENT_INVALID", "附件路径无效");
        return path;
    }
    private String safeName(String name) {
        String value = name == null ? "attachment" : Path.of(name).getFileName().toString();
        return value.length() > 255 ? value.substring(value.length() - 255) : value;
    }
}
