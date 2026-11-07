package cn.medical.governance;

import cn.medical.booking.BookingService;
import cn.medical.catalog.CatalogService;
import cn.medical.delivery.DeliveryService;
import cn.medical.identity.ApiFailure;
import java.time.Clock;
import java.time.Duration;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
public class GovernanceService {
    public record RequestView(String id, String scheduleId, String doctorId, String departmentId, String substituteDoctorId,
        String state, String reason, long createdAt, long updatedAt) {}
    public record ChoiceView(String id, String requestId, String appointmentId, String substituteDoctorId,
        String state, long deadlineAt, Long respondedAt, String reason) {}
    private final GovernanceMapper governance;
    private final BookingService bookings;
    private final CatalogService catalog;
    private final DeliveryService delivery;
    private final Clock clock;
    private final TransactionTemplate transactions;
    private final AuditService auditService;
    public GovernanceService(GovernanceMapper governance, BookingService bookings, CatalogService catalog, DeliveryService delivery, Clock clock, PlatformTransactionManager manager, AuditService auditService) {
        this.governance = governance; this.bookings = bookings; this.catalog = catalog; this.delivery = delivery; this.clock = clock;
        transactions = new TransactionTemplate(manager); transactions.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.auditService = auditService;
    }
    public RequestView submit(String doctorId, String scheduleId, String reason) {
        catalog.lockMutation(scheduleId);
        var schedule = catalog.governanceSchedule(scheduleId);
        if (!schedule.doctorId().equals(doctorId)) throw new ApiFailure(404, "NOT_FOUND", "排班不存在");
        reason(reason);
        if (governance.pendingChoices(scheduleId) > 0) throw new ApiFailure(409, "SUBSTITUTION_PENDING", "仍有患者尚未确认当前替诊，请先处理完成");
        if (schedule.startsAt() - clock.millis() < Duration.ofHours(24).toMillis()) throw new ApiFailure(409, "EMERGENCY_REQUIRED", "距离出诊不足24小时，请由管理员办理紧急停诊");
        return create(doctorId, scheduleId, reason, doctorId.equals(governance.departmentHead(schedule.departmentId())) ? "ADMIN_REVIEW" : "HEAD_REVIEW");
    }
    private RequestView create(String actor, String scheduleId, String reason, String state) {
        catalog.lockMutation(scheduleId);
        var affected = bookings.lockSchedulePatients(scheduleId);
        var schedule = catalog.governanceSchedule(scheduleId);
        if (!schedule.state().equals("PUBLISHED")) throw new ApiFailure(409, "SUSPENSION_CONFLICT", "排班已有变更流程");
        catalog.changeScheduleState(scheduleId, "SUSPENSION_PENDING");
        for (var appointment : affected) if (appointment.state().equals("PENDING_PAYMENT")) bookings.cancelForGovernance(appointment.id(), "CLINIC_SUSPENSION");
        long now = clock.millis(); String id = UUID.randomUUID().toString();
        governance.insertRequest(new GovernanceMapper.Request(id, scheduleId, schedule.doctorId(), schedule.departmentId(), actor, null, state, reason.trim(), now, now));
        audit(actor, "SUSPENSION_SUBMITTED", "SUSPENSION", id, reason);
        String head = governance.departmentHead(schedule.departmentId());
        if (state.equals("HEAD_REVIEW") && head != null) notify(head, "SUSPENSION_REVIEW", id, "本科室有待审核停诊申请", "head-review:" + id);
        return view(governance.lockRequest(id));
    }
    @Transactional(readOnly = true)
    public List<RequestView> ownRequests(String doctor) {
        return governance.allRequests().stream().filter(request -> request.doctorId().equals(doctor)).map(this::view).toList();
    }
    @Transactional(readOnly = true)
    public List<RequestView> headInbox(String head) {
        return governance.requests("HEAD_REVIEW").stream().filter(request -> head.equals(governance.departmentHead(request.departmentId())) && !head.equals(request.doctorId())).map(this::view).toList();
    }
    @Transactional(readOnly = true)
    public List<RequestView> adminInbox() { return governance.allRequests().stream().map(this::view).toList(); }
    @Transactional(readOnly = true)
    public List<RequestView> substituteInbox(String doctor) {
        return governance.requests("SUBSTITUTE_REVIEW").stream().filter(request -> doctor.equals(request.substituteDoctorId())).map(this::view).toList();
    }
    public RequestView headReview(String head, String id, String plan, String substitute, String reason) {
        var request = lockRequest(id);
        if (!head.equals(governance.departmentHead(request.departmentId())) || head.equals(request.doctorId())) throw new ApiFailure(403, "FORBIDDEN", "无权审核该申请或须回避本人申请");
        if (!request.state().equals("HEAD_REVIEW")) throw conflict();
        return plan(head, request, plan, substitute, reason);
    }
    public RequestView adminPlan(String admin, String id, String plan, String substitute, String reason) {
        var request = lockRequest(id);
        if (!request.state().equals("ADMIN_REVIEW") || request.substituteDoctorId() != null || !request.doctorId().equals(governance.departmentHead(request.departmentId()))) throw conflict();
        return plan(admin, request, plan, substitute, reason);
    }
    private RequestView plan(String actor, GovernanceMapper.Request request, String plan, String substitute, String reason) {
        reason(reason);
        if (plan.equals("REJECT")) { reject(actor, request, reason); }
        else if (plan.equals("STOP")) { governance.updateRequest(request.id(), "ADMIN_REVIEW", null, clock.millis()); audit(actor, "SUSPENSION_DIRECT_STOP", "SUSPENSION", request.id(), reason); }
        else if (plan.equals("SUBSTITUTE") && substitute != null && !substitute.isBlank()) {
            catalog.validateSubstitute(request.scheduleId(), substitute);
            governance.updateRequest(request.id(), "SUBSTITUTE_REVIEW", substitute, clock.millis());
            audit(actor, "SUBSTITUTE_INVITED", "SUSPENSION", request.id(), reason);
            notify(substitute, "SUBSTITUTE_INVITED", request.id(), "请确认或拒绝替诊邀请", "substitute-invited:" + request.id() + ":" + substitute + ":" + clock.millis());
        } else throw new ApiFailure(400, "INVALID_INPUT", "请选择替诊、直接停诊或驳回");
        return view(governance.lockRequest(request.id()));
    }
    public RequestView substituteRespond(String doctor, String id, boolean accepted, String reason) {
        var request = lockRequest(id); reason(reason);
        if (!doctor.equals(request.substituteDoctorId()) || !request.state().equals("SUBSTITUTE_REVIEW")) throw new ApiFailure(403, "FORBIDDEN", "无权处理此邀请");
        boolean recusal = request.doctorId().equals(governance.departmentHead(request.departmentId()));
        governance.updateRequest(id, accepted || recusal ? "ADMIN_REVIEW" : "HEAD_REVIEW", accepted ? doctor : null, clock.millis());
        audit(doctor, accepted ? "SUBSTITUTE_ACCEPTED" : "SUBSTITUTE_REJECTED", "SUSPENSION", id, reason);
        return view(governance.lockRequest(id));
    }
    public RequestView adminReview(String admin, String id, boolean approve, String reason) {
        var request = lockRequest(id); reason(reason);
        if (!request.state().equals("ADMIN_REVIEW")) throw conflict();
        if (!approve) { reject(admin, request, reason); return view(governance.lockRequest(id)); }
        var schedule = catalog.governanceSchedule(request.scheduleId());
        if (request.substituteDoctorId() == null) {
            applyStop(admin, request, reason); governance.updateRequest(id, "APPROVED_STOP", null, clock.millis());
        } else {
            if (schedule.startsAt() - clock.millis() <= Duration.ofHours(2).toMillis()) throw new ApiFailure(409, "EMERGENCY_REQUIRED", "距离出诊不足2小时，请人工紧急处理");
            catalog.substituteSchedule(request.scheduleId(), request.substituteDoctorId());
            long deadline = Math.min(clock.millis() + Duration.ofHours(2).toMillis(), schedule.startsAt() - Duration.ofHours(2).toMillis());
            for (var appointment : bookings.lockSchedulePatients(request.scheduleId())) {
                if (!appointment.state().equals("CONFIRMED")) continue;
                bookings.awaitSubstitution(appointment.id());
                governance.insertChoice(new GovernanceMapper.Choice(UUID.randomUUID().toString(), id, appointment.id(), appointment.patientId(), appointment.doctorId(), request.substituteDoctorId(), "PENDING", deadline, null, null));
                notify(appointment.patientId(), "SUBSTITUTION_PENDING", appointment.id(), "请在截止时间前确认替诊；不响应将取消并退款", "substitution-pending:" + id + ":" + appointment.id());
            }
            governance.updateRequest(id, "APPROVED_SUBSTITUTE", request.substituteDoctorId(), clock.millis());
            audit(admin, "SUBSTITUTION_REVIEWED", "SCHEDULE", request.scheduleId(), reason);
        }
        return view(governance.lockRequest(id));
    }
    private void reject(String actor, GovernanceMapper.Request request, String reason) {
        governance.updateRequest(request.id(), "HEAD_REJECTED", null, clock.millis()); catalog.changeScheduleState(request.scheduleId(), "PUBLISHED");
        audit(actor, "SUSPENSION_REJECTED", "SUSPENSION", request.id(), reason);
    }
    public RequestView emergencySchedule(String admin, String scheduleId, String reason) {
        reason(reason); catalog.lockMutation(scheduleId);
        var pending = governance.allRequests().stream().filter(request -> request.scheduleId().equals(scheduleId) && Set.of("HEAD_REVIEW", "ADMIN_REVIEW", "SUBSTITUTE_REVIEW", "APPROVED_SUBSTITUTE").contains(request.state())).findFirst().orElse(null);
        return emergencyStop(admin, pending == null ? create(admin, scheduleId, reason, "ADMIN_REVIEW").id() : pending.id(), reason);
    }
    public RequestView emergencyStop(String admin, String id, String reason) {
        var request = lockRequest(id); reason(reason);
        if (!Set.of("HEAD_REVIEW", "ADMIN_REVIEW", "SUBSTITUTE_REVIEW", "APPROVED_SUBSTITUTE").contains(request.state())) throw conflict();
        applyStop(admin, request, reason); governance.updateRequest(id, "EMERGENCY_STOP", null, clock.millis());
        return view(governance.lockRequest(id));
    }
    private void applyStop(String actor, GovernanceMapper.Request request, String reason) {
        for (var appointment : bookings.lockSchedulePatients(request.scheduleId())) bookings.cancelForGovernance(appointment.id(), "CLINIC_SUSPENSION");
        for (var choice : governance.requestChoices(request.id())) if (choice.state().equals("PENDING")) governance.updateChoice(choice.id(), "REJECTED", clock.millis(), "医院紧急停诊");
        catalog.changeScheduleState(request.scheduleId(), "CANCELLED");
        audit(actor, "SUSPENSION_EFFECTIVE", "SCHEDULE", request.scheduleId(), reason);
    }
    @Transactional(readOnly = true)
    public List<ChoiceView> choices(String patient) { return governance.patientChoices(patient).stream().map(this::choice).toList(); }
    public ChoiceView choose(String patient, String id, boolean accepted, String reason) {
        var initial = governance.choice(id);
        if (initial == null || !initial.patientId().equals(patient)) throw new ApiFailure(404, "NOT_FOUND", "替诊选择不存在");
        var request = lockRequest(initial.requestId());
        var choice = governance.patientChoice(id, patient);
        if (!choice.state().equals("PENDING")) return choice(choice);
        if (!request.state().equals("APPROVED_SUBSTITUTE")) throw conflict();
        reason(reason);
        if (choice.deadlineAt() <= clock.millis()) { expireChoice(choice); return choice(governance.patientChoice(id, patient)); }
        if (accepted) {
            var appointment = bookings.own(patient, choice.appointmentId());
            if (!appointment.state().equals("CONFIRMED")) throw new ApiFailure(409, "APPOINTMENT_STATE_CONFLICT", "预约已失效，不能接受替诊");
            bookings.transferDoctor(choice.appointmentId(), choice.substituteDoctorId());
        } else bookings.cancelForGovernance(choice.appointmentId(), "SUBSTITUTE_REJECTED");
        governance.updateChoice(id, accepted ? "ACCEPTED" : "REJECTED", clock.millis(), reason.trim());
        audit(patient, accepted ? "SUBSTITUTE_CHOICE_ACCEPTED" : "SUBSTITUTE_CHOICE_REJECTED", "CHOICE", id, reason);
        return choice(governance.patientChoice(id, patient));
    }
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public int expireChoices(String actor) {
        int count = 0;
        for (var pending : governance.requests("APPROVED_SUBSTITUTE")) {
            count += transactions.execute(status -> {
                lockRequest(pending.id()); int expired = 0;
                for (var choice : governance.requestChoices(pending.id())) if (choice.state().equals("PENDING") && choice.deadlineAt() <= clock.millis()) { expireChoice(choice); expired++; }
                return expired;
            });
        }
        return count;
    }
    private void expireChoice(GovernanceMapper.Choice choice) {
        bookings.cancelForGovernance(choice.appointmentId(), "SUBSTITUTE_TIMEOUT");
        governance.updateChoice(choice.id(), "EXPIRED", clock.millis(), "患者未在期限内确认替诊");
        audit(governance.request(choice.requestId()).requestedBy(), "SUBSTITUTE_CHOICE_EXPIRED", "CHOICE", choice.id(), "系统按截止时间自动执行；非患者主动操作");
    }
    public void assignHead(String admin, String departmentId, String doctorId) {
        var doctor = catalog.ownDoctor(doctorId);
        if (!doctor.enabled() || !doctor.credentialStatus().equals("CERTIFIED") || !departmentId.equals(doctor.departmentId())) throw new ApiFailure(409, "HEAD_INVALID", "科主任必须是本科室认证医生");
        governance.assignHead(departmentId, doctorId); audit(admin, "DEPARTMENT_HEAD_ASSIGNED", "DEPARTMENT", departmentId, doctorId);
    }
    public record Impact(String appointmentId, String patientId, String doctorId, String state) {}
    @Transactional(readOnly = true)
    public List<Impact> impacts(String id) {
        var request = governance.request(id);
        if (request == null) throw new ApiFailure(404, "NOT_FOUND", "申请不存在");
        return bookings.scheduleAppointments(request.scheduleId()).stream().map(a -> new Impact(a.id(), a.patientId(), a.doctorId(), a.state())).toList();
    }
    public RequestView emergencySubstitution(String admin, String id, Map<String, Boolean> patientChoices, String reason) {
        var request = lockRequest(id); reason(reason);
        if (!request.state().equals("ADMIN_REVIEW") || request.substituteDoctorId() == null) throw conflict();
        var affected = bookings.lockSchedulePatients(request.scheduleId()).stream().filter(a -> a.state().equals("CONFIRMED")).toList();
        if (patientChoices == null || !patientChoices.keySet().equals(affected.stream().map(a -> a.id()).collect(java.util.stream.Collectors.toSet())) || patientChoices.values().stream().anyMatch(Objects::isNull))
            throw new ApiFailure(400, "PATIENT_CHOICES_REQUIRED", "须逐一记录全部受影响患者的明确选择，不能默认接受");
        catalog.substituteSchedule(request.scheduleId(), request.substituteDoctorId());
        for (var appointment : affected) {
            String choiceId = UUID.randomUUID().toString(); boolean accepted = patientChoices.get(appointment.id());
            governance.insertChoice(new GovernanceMapper.Choice(choiceId, id, appointment.id(), appointment.patientId(), appointment.doctorId(), request.substituteDoctorId(), "PENDING", clock.millis(), null, null));
            if (accepted) bookings.transferDoctor(appointment.id(), request.substituteDoctorId()); else bookings.cancelForGovernance(appointment.id(), "SUBSTITUTE_REJECTED");
            governance.updateChoice(choiceId, accepted ? "ACCEPTED" : "REJECTED", clock.millis(), reason);
            audit(admin, accepted ? "EMERGENCY_SUBSTITUTE_ACCEPTED" : "EMERGENCY_SUBSTITUTE_REJECTED", "CHOICE", choiceId, reason);
            notify(appointment.patientId(), "SUBSTITUTION_RESOLVED", appointment.id(), accepted ? "已根据人工确认安排替诊" : "已根据人工确认取消并退款", "emergency-choice:" + choiceId);
        }
        governance.updateRequest(id, "APPROVED_SUBSTITUTE", request.substituteDoctorId(), clock.millis());
        audit(admin, "EMERGENCY_SUBSTITUTION_REVIEWED", "SCHEDULE", request.scheduleId(), reason);
        return view(governance.lockRequest(id));
    }
    private GovernanceMapper.Request lockRequest(String id) {
        var initial = governance.request(id);
        if (initial == null) throw new ApiFailure(404, "NOT_FOUND", "停诊申请不存在");
        catalog.lockMutation(initial.scheduleId()); bookings.lockSchedulePatients(initial.scheduleId());
        return governance.lockRequest(id);
    }
    private static void reason(String reason) { if (reason == null || reason.isBlank() || reason.length() > 1000) throw new ApiFailure(400, "INVALID_INPUT", "处理理由不能为空且不超过1000字"); }
    private static ApiFailure conflict() { return new ApiFailure(409, "STATE_CONFLICT", "当前流程阶段不允许此操作"); }
    private RequestView view(GovernanceMapper.Request v) { return new RequestView(v.id(), v.scheduleId(), v.doctorId(), v.departmentId(), v.substituteDoctorId(), v.state(), v.reason(), v.createdAt(), v.updatedAt()); }
    private ChoiceView choice(GovernanceMapper.Choice v) { return new ChoiceView(v.id(), v.requestId(), v.appointmentId(), v.substituteDoctorId(), v.state(), v.deadlineAt(), v.respondedAt(), v.reason()); }
    private void notify(String recipient, String type, String id, String text, String key) { delivery.publish(recipient, type, id, text, key); }
    private void audit(String actor, String action, String type, String id, String reason) { auditService.record(actor, action, type, id, reason); }
}
