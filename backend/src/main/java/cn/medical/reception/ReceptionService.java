package cn.medical.reception;

import cn.medical.identity.ApiFailure;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReceptionService {
    public record Summary(String state, int queueGroup, long queuedAt, long checkedInAt, int restorationCount,
        Long calledAt, Long startedAt, Long missedAt, Long completedAt) {}
    private final ReceptionMapper reception;
    private final Clock clock;
    public ReceptionService(ReceptionMapper reception, Clock clock) { this.reception = reception; this.clock = clock; }

    @Transactional
    public Summary checkIn(String patient, String id) {
        var appointment = requirePatientAppointment(patient, id);
        if (!appointment.state().equals("CONFIRMED")) throw new ApiFailure(409, "CHECK_IN_NOT_ALLOWED", "只有已确认预约可以签到");
        long now = clock.millis();
        ensureCheckInWindow(appointment, now);
        var current = reception.lockedQueue(id);
        if (current != null) return summary(reception.queue(id));
        int group = appointment.source().equals("WALK_IN") ? 2 : 1;
        try { reception.insertQueue(id, appointment.scheduleId(), appointment.doctorId(), patient, group, now, now); }
        catch (DuplicateKeyException ignored) {}
        return summary(reception.queue(id));
    }

    @Transactional
    public Summary restore(String patient, String id) {
        var appointment = requirePatientAppointment(patient, id);
        var current = reception.lockedQueue(id);
        if (current == null) throw new ApiFailure(409, "RESTORE_NOT_ALLOWED", "请先签到并等待医生叫号");
        long now = clock.millis();
        if (!current.state().equals("MISSED") || current.restorationCount() >= 1)
            throw new ApiFailure(409, "RESTORE_NOT_ALLOWED", "该预约已恢复排队或当前状态不允许恢复");
        if (now < appointment.startsAt() || now >= appointment.endsAt())
            throw new ApiFailure(409, "SESSION_ENDED", "本次门诊时段不在允许恢复排队的时间内");
        reception.restore(id, now);
        return summary(reception.queue(id));
    }

    @Transactional(readOnly = true)
    public Summary status(String patient, String id) {
        requirePatientAppointment(patient, id);
        return summary(reception.queue(id));
    }

    @Transactional(readOnly = true)
    public java.util.List<ReceptionMapper.QueueEntry> doctorQueue(String doctor, String scheduleId, LocalDate date) {
        return reception.doctorQueue(doctor, scheduleId, date);
    }

    @Transactional
    public ReceptionMapper.QueueEntry call(String doctor, String id) {
        var entry = requireDoctorQueue(doctor, id);
        var appointment = reception.appointment(id);
        var next = reception.nextWaiting(entry.scheduleId());
        if (next == null || !next.appointmentId().equals(id)) throw new ApiFailure(409, "QUEUE_ORDER", "请先叫号当前候诊队列的下一位患者");
        long now = clock.millis();
        if (appointment == null || now < appointment.startsAt() || now >= appointment.endsAt()) throw new ApiFailure(409, "SESSION_CLOSED", "当前不在门诊接诊时段内");
        if (reception.call(id, now) != 1) throw new ApiFailure(409, "QUEUE_STATE_CONFLICT", "患者当前不在等待叫号状态");
        return reception.queue(id);
    }

    @Transactional
    public ReceptionMapper.QueueEntry start(String doctor, String id) { return transition(doctor, id, "start"); }
    @Transactional
    public ReceptionMapper.QueueEntry miss(String doctor, String id) { return transition(doctor, id, "miss"); }
    @Transactional
    public ReceptionMapper.QueueEntry complete(String doctor, String id) { return transition(doctor, id, "complete"); }

    private ReceptionMapper.QueueEntry transition(String doctor, String id, String action) {
        var entry = requireDoctorQueue(doctor, id);
        var appointment = reception.appointment(id);
        long now = clock.millis();
        if (appointment == null || now < appointment.startsAt() || (now >= appointment.endsAt() && !action.equals("complete")))
            throw new ApiFailure(409, "SESSION_CLOSED", "当前不在门诊接诊时段内");
        int changed = switch (action) {
            case "start" -> reception.start(id, now);
            case "miss" -> reception.miss(id, now);
            default -> reception.complete(id, now);
        };
        if (changed != 1) throw new ApiFailure(409, "QUEUE_STATE_CONFLICT", "患者当前状态不允许执行该操作");
        return reception.queue(id);
    }

    public Summary summaryFor(String id) { return summary(reception.queue(id)); }
    private ReceptionMapper.Appointment requirePatientAppointment(String patient, String id) {
        var appointment = reception.patientAppointment(id, patient);
        if (appointment == null) throw new ApiFailure(404, "NOT_FOUND", "预约不存在");
        return appointment;
    }
    private ReceptionMapper.QueueEntry requireDoctorQueue(String doctor, String id) {
        var entry = reception.queue(id);
        if (entry == null || !entry.doctorId().equals(doctor)) throw new ApiFailure(404, "NOT_FOUND", "候诊记录不存在");
        return entry;
    }
    private void ensureCheckInWindow(ReceptionMapper.Appointment appointment, long now) {
        if (now < appointment.startsAt() - 30 * 60_000L || now >= appointment.endsAt() - 15 * 60_000L)
            throw new ApiFailure(409, "CHECK_IN_CLOSED", "当前不在签到时间窗口内");
    }
    private Summary summary(ReceptionMapper.QueueEntry entry) {
        return entry == null ? null : new Summary(entry.state(), entry.queueGroup(), entry.queuedAt(), entry.checkedInAt(), entry.restorationCount(), entry.calledAt(), entry.startedAt(), entry.missedAt(), entry.completedAt());
    }
}
