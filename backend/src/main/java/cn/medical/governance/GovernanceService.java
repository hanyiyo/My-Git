package cn.medical.governance;

import cn.medical.booking.BookingMapper;
import cn.medical.booking.BookingService;
import cn.medical.identity.ApiFailure;
import cn.medical.delivery.DeliveryService;
import java.time.Clock;
import java.time.Duration;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GovernanceService {
    public record RequestView(String id, String scheduleId, String doctorId, String departmentId, String substituteDoctorId,
        String state, String reason, long createdAt, long updatedAt) {}
    public record ChoiceView(String id, String requestId, String appointmentId, String substituteDoctorId,
        String state, long deadlineAt, Long respondedAt, String reason) {}
    public record SubmitInput(String scheduleId, String reason) {}

    private final GovernanceMapper governance;
    private final BookingMapper booking;
    private final BookingService bookings;
    private final Clock clock;
    private final DeliveryService delivery;
    public GovernanceService(GovernanceMapper governance, BookingMapper booking, BookingService bookings, Clock clock) { this(governance, booking, bookings, null, clock); }
    @Autowired
    public GovernanceService(GovernanceMapper governance, BookingMapper booking, BookingService bookings, DeliveryService delivery, Clock clock) {
        this.governance = governance; this.booking = booking; this.bookings = bookings; this.delivery = delivery; this.clock = clock;
    }

    @Transactional
    public RequestView submit(String doctorId, String scheduleId, String reason) {
        var schedule = requireSchedule(scheduleId);
        if (!schedule.doctorId().equals(doctorId)) throw new ApiFailure(404, "NOT_FOUND", "排班不存在");
        if (reason == null || reason.isBlank() || reason.length() > 1000) throw new ApiFailure(400, "INVALID_INPUT", "停诊原因不能为空且长度受限");
        long now = clock.millis();
        if (schedule.startsAt() - now < Duration.ofHours(24).toMillis()) throw new ApiFailure(409, "EMERGENCY_REQUIRED", "距离出诊不足24小时，请由管理员办理紧急停诊");
        if (governance.pauseSchedule(scheduleId) != 1) throw new ApiFailure(409, "SUSPENSION_CONFLICT", "该排班已有变更流程");
        String state = doctorId.equals(governance.departmentHead(schedule.departmentId())) ? "ADMIN_REVIEW" : "HEAD_REVIEW";
        String id = UUID.randomUUID().toString();
        governance.insertRequest(new GovernanceMapper.Request(id, scheduleId, doctorId, schedule.departmentId(), doctorId, null, state, reason.trim(), now, now));
        audit(doctorId, "SUSPENSION_SUBMITTED", "SUSPENSION", id, reason);
        return view(governance.lockRequest(id));
    }

    @Transactional
    public List<RequestView> headInbox(String headId) {
        var result = new ArrayList<RequestView>();
        for (var request : governance.requests("HEAD_REVIEW")) if (headId.equals(governance.departmentHead(request.departmentId()))) result.add(view(request));
        return result;
    }
    @Transactional(readOnly = true)
    public List<RequestView> adminInbox() { return governance.requests("ADMIN_REVIEW").stream().map(this::view).toList(); }
    @Transactional(readOnly = true)
    public List<RequestView> substituteInbox(String doctorId) {
        return governance.requests("SUBSTITUTE_REVIEW").stream().filter(request -> doctorId.equals(request.substituteDoctorId())).map(this::view).toList();
    }

    @Transactional
    public RequestView headReview(String headId, String id, String plan, String substituteDoctorId, String reason) {
        var request = requireRequest(id);
        if (!headId.equals(governance.departmentHead(request.departmentId())) || request.doctorId().equals(headId))
            throw new ApiFailure(403, "FORBIDDEN", "无权审核该停诊申请");
        if (!request.state().equals("HEAD_REVIEW")) throw new ApiFailure(409, "STATE_CONFLICT", "申请当前不在科主任审核阶段");
        if (reason == null || reason.isBlank()) throw new ApiFailure(400, "INVALID_INPUT", "审核理由不能为空");
        if (plan.equals("REJECT")) {
            governance.updateRequest(id, "HEAD_REJECTED", null, clock.millis()); governance.updateScheduleState(request.scheduleId(), "PUBLISHED");
            audit(headId, "SUSPENSION_REJECTED", "SUSPENSION", id, reason); return view(governance.lockRequest(id));
        }
        if (plan.equals("STOP")) {
            governance.updateRequest(id, "ADMIN_REVIEW", null, clock.millis()); audit(headId, "SUSPENSION_DIRECT_STOP", "SUSPENSION", id, reason);
            return view(governance.lockRequest(id));
        }
        if (!plan.equals("SUBSTITUTE") || substituteDoctorId == null || substituteDoctorId.isBlank()) throw new ApiFailure(400, "INVALID_INPUT", "请选择直接停诊或替诊医生");
        validateSubstitute(request, substituteDoctorId);
        governance.updateRequest(id, "SUBSTITUTE_REVIEW", substituteDoctorId, clock.millis()); audit(headId, "SUBSTITUTE_INVITED", "SUSPENSION", id, reason);
        return view(governance.lockRequest(id));
    }

    @Transactional
    public RequestView substituteRespond(String doctorId, String id, boolean accepted, String reason) {
        var request = requireRequest(id);
        if (!doctorId.equals(request.substituteDoctorId()) || !request.state().equals("SUBSTITUTE_REVIEW")) throw new ApiFailure(403, "FORBIDDEN", "无权处理该替诊邀请");
        if (reason == null || reason.isBlank()) throw new ApiFailure(400, "INVALID_INPUT", "替诊处理理由不能为空");
        String state = accepted ? "ADMIN_REVIEW" : "HEAD_REVIEW";
        governance.updateRequest(id, state, accepted ? request.substituteDoctorId() : null, clock.millis());
        audit(doctorId, accepted ? "SUBSTITUTE_ACCEPTED" : "SUBSTITUTE_REJECTED", "SUSPENSION", id, reason);
        return view(governance.lockRequest(id));
    }

    @Transactional
    public RequestView adminReview(String adminId, String id, boolean approve, String reason) {
        var request = requireRequest(id);
        if (!Set.of("ADMIN_REVIEW").contains(request.state())) throw new ApiFailure(409, "STATE_CONFLICT", "申请当前不能由管理员复核");
        if (reason == null || reason.isBlank()) throw new ApiFailure(400, "INVALID_INPUT", "管理员复核理由不能为空");
        var schedule = requireSchedule(request.scheduleId());
        if (!approve) {
            governance.updateRequest(id, "HEAD_REJECTED", null, clock.millis()); governance.updateScheduleState(request.scheduleId(), "PUBLISHED");
            audit(adminId, "SUSPENSION_ADMIN_REJECTED", "SUSPENSION", id, reason); return view(governance.lockRequest(id));
        }
        if (request.substituteDoctorId() == null) { applyStop(adminId, request, reason); governance.updateRequest(id, "APPROVED_STOP", null, clock.millis()); }
        else { validateSubstitute(request, request.substituteDoctorId()); applySubstitution(adminId, request, schedule, reason); }
        return view(governance.lockRequest(id));
    }

    @Transactional
    public RequestView emergencyStop(String adminId, String id, String reason) {
        var request = requireRequest(id);
        if (reason == null || reason.isBlank()) throw new ApiFailure(400, "INVALID_INPUT", "紧急停诊理由不能为空");
        applyStop(adminId, request, reason); governance.updateRequest(id, "EMERGENCY_STOP", null, clock.millis());
        return view(governance.lockRequest(id));
    }

    @Transactional(readOnly = true)
    public List<ChoiceView> choices(String patientId) { return governance.patientChoices(patientId).stream().map(this::choice).toList(); }

    @Transactional
    public ChoiceView choose(String patientId, String id, boolean accepted, String reason) {
        var choice = governance.patientChoice(id, patientId);
        if (choice == null) throw new ApiFailure(404, "NOT_FOUND", "替诊选择不存在");
        long now = clock.millis();
        if (!choice.state().equals("PENDING")) return choice(choice);
        if (choice.deadlineAt() <= now) { expireChoice(choice, now); return choice(governance.patientChoice(id, patientId)); }
        if (reason == null || reason.isBlank()) throw new ApiFailure(400, "INVALID_INPUT", "选择理由不能为空");
        if (accepted) bookings.transferDoctor(choice.appointmentId(), choice.substituteDoctorId());
        else bookings.cancelForGovernance(booking.own(choice.appointmentId(), patientId), "SUBSTITUTE_REJECTED", now);
        governance.updateChoice(id, accepted ? "ACCEPTED" : "REJECTED", now, reason.trim());
        audit(patientId, accepted ? "SUBSTITUTE_CHOICE_ACCEPTED" : "SUBSTITUTE_CHOICE_REJECTED", "CHOICE", id, reason);
        return choice(governance.patientChoice(id, patientId));
    }

    @Transactional
    public int expireChoices(String actorId) {
        int count = 0; long now = clock.millis();
        for (var request : governance.requests("APPROVED_SUBSTITUTE")) for (var choice : governance.requestChoices(request.id()))
            if (choice.state().equals("PENDING") && choice.deadlineAt() <= now) { expireChoice(choice, now); count++; }
        audit(actorId, "SUBSTITUTION_EXPIRY_SCAN", "SYSTEM", actorId, "expired=" + count);
        return count;
    }

    @Transactional
    public void assignHead(String adminId, String departmentId, String doctorId) {
        var doctor = governance.doctor(doctorId);
        if (doctor == null || !doctor.enabled() || !doctor.credentialStatus().equals("CERTIFIED") || !departmentId.equals(doctor.departmentId()))
            throw new ApiFailure(409, "HEAD_INVALID", "科主任必须是该科室已认证医生");
        governance.assignHead(departmentId, doctorId); audit(adminId, "DEPARTMENT_HEAD_ASSIGNED", "DEPARTMENT", departmentId, doctorId);
    }

    private void applyStop(String actor, GovernanceMapper.Request request, String reason) {
        governance.updateScheduleState(request.scheduleId(), "CANCELLED");
        for (var appointment : booking.activeForSchedule(request.scheduleId())) bookings.cancelForGovernance(appointment, "CLINIC_SUSPENSION", clock.millis());
        governance.deactivateSlots(request.scheduleId()); audit(actor, "SUSPENSION_EFFECTIVE", "SCHEDULE", request.scheduleId(), reason);
    }
    private void applySubstitution(String actor, GovernanceMapper.Request request, GovernanceMapper.Schedule schedule, String reason) {
        long deadline = Math.min(clock.millis() + Duration.ofHours(2).toMillis(), schedule.startsAt() - Duration.ofHours(2).toMillis());
        for (var appointment : booking.activeForSchedule(request.scheduleId())) {
            if (appointment.state().equals("PENDING_PAYMENT")) bookings.cancelForGovernance(appointment, "CLINIC_SUSPENSION", clock.millis());
            else {
                governance.insertChoice(new GovernanceMapper.Choice(UUID.randomUUID().toString(), request.id(), appointment.id(), appointment.patientId(), appointment.doctorId(), request.substituteDoctorId(), "PENDING", deadline, null, null));
                if (delivery != null) delivery.publish(appointment.patientId(), "SUBSTITUTION_PENDING", appointment.id(), "请在截止时间前确认替诊", "substitution-pending:" + appointment.id());
            }
        }
        governance.updateScheduleState(request.scheduleId(), "SUSPENSION_PENDING"); governance.updateRequest(request.id(), "APPROVED_SUBSTITUTE", request.substituteDoctorId(), clock.millis());
        audit(actor, "SUBSTITUTION_REVIEWED", "SCHEDULE", request.scheduleId(), reason);
    }
    private void expireChoice(GovernanceMapper.Choice choice, long now) {
        bookings.cancelForGovernance(booking.own(choice.appointmentId(), choice.patientId()), "SUBSTITUTE_TIMEOUT", now);
        governance.updateChoice(choice.id(), "EXPIRED", now, "患者未在期限内确认替诊");
    }
    private void validateSubstitute(GovernanceMapper.Request request, String doctorId) {
        var doctor = governance.doctor(doctorId); var schedule = requireSchedule(request.scheduleId());
        if (doctor == null || !doctor.enabled() || !doctor.credentialStatus().equals("CERTIFIED") || !request.departmentId().equals(doctor.departmentId()) || governance.conflictingSchedule(doctorId, schedule.clinicDate(), schedule.session(), schedule.id()) > 0)
            throw new ApiFailure(409, "SUBSTITUTE_INVALID", "替诊医生必须同科室、已认证且无时间冲突");
    }
    private GovernanceMapper.Schedule requireSchedule(String id) { var schedule = governance.schedule(id); if (schedule == null) throw new ApiFailure(404, "NOT_FOUND", "排班不存在"); return schedule; }
    private GovernanceMapper.Request requireRequest(String id) { var request = governance.lockRequest(id); if (request == null) throw new ApiFailure(404, "NOT_FOUND", "停诊申请不存在"); return request; }
    private RequestView view(GovernanceMapper.Request value) { return new RequestView(value.id(), value.scheduleId(), value.doctorId(), value.departmentId(), value.substituteDoctorId(), value.state(), value.reason(), value.createdAt(), value.updatedAt()); }
    private ChoiceView choice(GovernanceMapper.Choice value) { return new ChoiceView(value.id(), value.requestId(), value.appointmentId(), value.substituteDoctorId(), value.state(), value.deadlineAt(), value.respondedAt(), value.reason()); }
    private void audit(String actor, String action, String type, String id, String reason) { governance.audit(UUID.randomUUID().toString(), actor, action, type, id, reason == null ? "" : reason, clock.millis()); }
}
