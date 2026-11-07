package cn.medical.catalog;

import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.time.*;
import cn.medical.identity.ApiFailure;
import cn.medical.identity.IdentityService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogService {
    public record DoctorView(String id, String displayName, String title, String biography, String credentialStatus, boolean enabled, String departmentId, String departmentName) {}
    public record ScheduleView(String id, String doctorId, String doctorName, String departmentId, String departmentName, LocalDate clinicDate,
        String session, String serviceMode, long startsAt, long endsAt, String zoneId, int capacity, long feeMinor, String state,
        int availableSlots, boolean advanceAvailable, boolean walkInAvailable) {}
    private final CatalogMapper catalog;
    private final IdentityService identity;
    private final Clock clock;
    private static final ZoneId HOSPITAL_ZONE = ZoneId.of("Asia/Shanghai");
    public CatalogService(CatalogMapper catalog, IdentityService identity, Clock clock) { this.catalog = catalog; this.identity = identity; this.clock = clock; }
    public CatalogMapper.Hospital hospital() { return catalog.hospital(); }
    public record SlotReservation(String slotId, String scheduleId, String doctorId, String departmentId,
        LocalDate clinicDate, String session, String serviceMode, long startsAt, long endsAt, long feeMinor, long createdAt, long holdExpiresAt) {}
    public record RegistrationSession(LocalDate clinicDate, String session) {}
    public RegistrationSession registrationSession(String id) {
        var schedule = catalog.schedule(id);
        if (schedule == null) throw new ApiFailure(404, "NOT_FOUND", "排班不存在");
        return new RegistrationSession(schedule.clinicDate(), schedule.session());
    }

    @Transactional
    public SlotReservation reserveSlot(String scheduleId, String appointmentId, String source) {
        if (!Set.of("ADVANCE", "WALK_IN").contains(source)) throw new ApiFailure(400, "INVALID_INPUT", "登记来源无效");
        var initial = catalog.schedule(scheduleId);
        if (initial == null) throw new ApiFailure(404, "NOT_FOUND", "排班不存在");
        // Follow publication's doctor -> department -> schedule lock order before checking eligibility.
        var doctor = identity.lockDoctor(initial.doctorId());
        var department = catalog.lockDepartment(initial.departmentId());
        var schedule = catalog.lockSchedule(scheduleId);
        if (!doctor.enabled() || !doctor.credentialStatus().equals("CERTIFIED") || department == null || !department.active()
            || !schedule.state().equals("PUBLISHED")
            || catalog.assignments().stream().noneMatch(value -> value.doctorId().equals(doctor.id()) && value.departmentId().equals(schedule.departmentId())))
            throw new ApiFailure(409, "SCHEDULE_UNAVAILABLE", "排班当前不可预约");
        if (!registrationOpen(schedule, source, clock.millis())) throw new ApiFailure(409, "REGISTRATION_CLOSED", "当前时间或服务方式不允许此类登记");
        var slot = catalog.lockAvailableSlot(scheduleId, clock.millis());
        long now = clock.millis();
        if (!registrationOpen(schedule, source, now)) throw new ApiFailure(409, "REGISTRATION_CLOSED", "当前时间或服务方式不允许此类登记");
        long expiry = Math.min(now + Duration.ofMinutes(10).toMillis(), schedule.endsAt() - Duration.ofMinutes(15).toMillis());
        if (slot == null || catalog.reserveSlot(slot.id(), appointmentId, now, expiry) != 1)
            throw new ApiFailure(409, "NO_SLOTS", "号源已用完");
        return new SlotReservation(slot.id(), schedule.id(), schedule.doctorId(), schedule.departmentId(), schedule.clinicDate(),
            schedule.session(), schedule.serviceMode(), schedule.startsAt(), schedule.endsAt(), schedule.feeMinor(), now, expiry);
    }
    private boolean registrationOpen(CatalogMapper.Schedule schedule, String source, long now) {
        return source.equals("ADVANCE") ? now < schedule.startsAt()
            : schedule.serviceMode().equals("OFFLINE") && now >= schedule.startsAt() && now < schedule.endsAt() - Duration.ofMinutes(15).toMillis();
    }
    public record Confirmation(boolean confirmed, long observedAt) {}
    @Transactional
    public Confirmation tryConfirmSlot(String scheduleId, String slotId, String appointmentId) {
        var initial = catalog.schedule(scheduleId);
        if (initial == null) return new Confirmation(false, clock.millis());
        var doctor = identity.lockDoctor(initial.doctorId());
        var department = catalog.lockDepartment(initial.departmentId());
        var schedule = catalog.lockSchedule(scheduleId);
        var slot = catalog.lockSlot(slotId);
        long now = clock.millis();
        boolean eligible = doctor.enabled() && doctor.credentialStatus().equals("CERTIFIED") && department != null && department.active()
            && schedule.state().equals("PUBLISHED") && slot != null && slot.scheduleId().equals(scheduleId)
            && catalog.assignments().stream().anyMatch(value -> value.doctorId().equals(doctor.id()) && value.departmentId().equals(schedule.departmentId()));
        if (eligible && catalog.confirmSlot(slotId, appointmentId, now) == 1) return new Confirmation(true, now);
        // An ineligible success result must be refundable without marking the transaction rollback-only.
        if (slot != null && slot.scheduleId().equals(scheduleId)) catalog.releaseSlot(slotId, appointmentId);
        return new Confirmation(false, now);
    }
    @Transactional
    public long releaseSlot(String scheduleId, String slotId, String appointmentId, boolean requireBeforeOpening) {
        var initial = catalog.schedule(scheduleId);
        if (initial == null) throw new ApiFailure(404, "NOT_FOUND", "排班不存在");
        identity.lockDoctor(initial.doctorId());
        catalog.lockDepartment(initial.departmentId());
        var schedule = catalog.lockSchedule(scheduleId);
        var slot = catalog.lockSlot(slotId);
        long now = clock.millis();
        if (requireBeforeOpening && now >= schedule.startsAt())
            throw new ApiFailure(409, "CANCEL_NOT_ALLOWED", "门诊已开始，请联系管理员人工处理取消及退款");
        if (slot == null || !slot.scheduleId().equals(scheduleId)) throw new ApiFailure(409, "RESERVATION_CHANGED", "号源归属已变化");
        if (catalog.releaseSlot(slotId, appointmentId) != 1 && requireBeforeOpening)
            throw new ApiFailure(409, "RESERVATION_CHANGED", "号源归属已变化");
        return now;
    }
    public List<CatalogMapper.Department> departments(boolean management) {
        return catalog.departments().stream().filter(department -> management || department.active()).toList();
    }
    @Transactional(readOnly = true)
    public List<DoctorView> doctors(boolean management, String departmentId) {
        var assignments = catalog.assignments();
        return identity.doctors().stream().filter(doctor -> management || (doctor.enabled() && doctor.credentialStatus().equals("CERTIFIED")))
            .map(doctor -> doctorView(doctor, assignments))
            .filter(doctor -> departmentId == null || departmentId.equals(doctor.departmentId()))
            .filter(doctor -> management || assignments.stream().anyMatch(assignment -> assignment.doctorId().equals(doctor.id()) && assignment.active()))
            .toList();
    }
    public DoctorView ownDoctor(String id) {
        return doctors(true, null).stream().filter(doctor -> doctor.id().equals(id)).findFirst()
            .orElseThrow(() -> new ApiFailure(404, "NOT_FOUND", "医生不存在"));
    }
    private DoctorView doctorView(IdentityService.Doctor doctor, List<CatalogMapper.Assignment> assignments) {
        var assignment = assignments.stream().filter(value -> value.doctorId().equals(doctor.id())).findFirst().orElse(null);
        return new DoctorView(doctor.id(), doctor.displayName().isBlank() ? doctor.identifier() : doctor.displayName(), doctor.title(), doctor.biography(), doctor.credentialStatus(), doctor.enabled(),
            assignment == null ? null : assignment.departmentId(), assignment == null ? null : assignment.departmentName());
    }
    @Transactional
    public DoctorView saveDoctor(String id, String displayName, String title, String biography, String departmentId, String credentialStatus) {
        identity.lockDoctor(id);
        var department = catalog.lockDepartment(departmentId);
        if (department == null || !department.active()) throw new ApiFailure(409, "DEPARTMENT_UNAVAILABLE", "请选择有效科室");
        var previous = catalog.assignments().stream().filter(assignment -> assignment.doctorId().equals(id)).findFirst().orElse(null);
        if (previous != null && !previous.departmentId().equals(departmentId) && catalog.openSchedules(id, clock.millis()) > 0)
            throw new ApiFailure(409, "SCHEDULE_COMMITMENT", "医生仍有未结束排班，不能变更科室归属");
        identity.updateDoctor(id, displayName, title, biography, credentialStatus);
        catalog.assignDoctor(id, departmentId);
        return ownDoctor(id);
    }
    @Transactional(readOnly = true)
    public List<ScheduleView> schedules(boolean management, String doctorId, String departmentId, LocalDate date, String mode) {
        if (mode != null && !Set.of("ONLINE", "OFFLINE").contains(mode)) throw new ApiFailure(400, "INVALID_INPUT", "服务方式无效");
        var doctors = doctors(true, null).stream().collect(Collectors.toMap(DoctorView::id, Function.identity()));
        var departments = catalog.departments().stream().collect(Collectors.toMap(CatalogMapper.Department::id, Function.identity()));
        long now = clock.millis();
        return catalog.schedules(now).stream()
            .filter(schedule -> doctorId == null || doctorId.equals(schedule.doctorId()))
            .filter(schedule -> departmentId == null || departmentId.equals(schedule.departmentId()))
            .filter(schedule -> date == null || date.equals(schedule.clinicDate()))
            .filter(schedule -> management || date != null || schedule.startsAt() < now + Duration.ofDays(7).toMillis())
            .filter(schedule -> mode == null || mode.equals(schedule.serviceMode()))
            .map(schedule -> scheduleView(schedule, doctors, departments, now))
            .filter(schedule -> management || schedule.advanceAvailable() || schedule.walkInAvailable()).toList();
    }
    /** Returns one schedule label for an existing appointment without exposing the management collection. */
    public ScheduleView scheduleDetails(String id) {
        var schedule = catalog.schedule(id);
        if (schedule == null) return null;
        var doctors = doctors(true, null).stream().collect(Collectors.toMap(DoctorView::id, Function.identity()));
        var departments = catalog.departments().stream().collect(Collectors.toMap(CatalogMapper.Department::id, Function.identity()));
        return scheduleView(schedule, doctors, departments, clock.millis());
    }
    private ScheduleView scheduleView(CatalogMapper.Schedule schedule, Map<String, DoctorView> doctors, Map<String, CatalogMapper.Department> departments, long now) {
        var doctor = doctors.get(schedule.doctorId()); var department = departments.get(schedule.departmentId());
        boolean valid = doctor != null && doctor.enabled() && doctor.credentialStatus().equals("CERTIFIED")
            && schedule.departmentId().equals(doctor.departmentId()) && department != null && department.active()
            && schedule.state().equals("PUBLISHED") && schedule.availableSlots() > 0;
        return new ScheduleView(schedule.id(), schedule.doctorId(), doctor == null ? "" : doctor.displayName(), schedule.departmentId(), department == null ? "" : department.name(),
            schedule.clinicDate(), schedule.session(), schedule.serviceMode(), schedule.startsAt(), schedule.endsAt(), schedule.zoneId(), schedule.capacity(), schedule.feeMinor(), schedule.state(), schedule.availableSlots(),
            valid && now < schedule.startsAt(), valid && schedule.serviceMode().equals("OFFLINE") && now >= schedule.startsAt() && now < schedule.endsAt() - Duration.ofMinutes(15).toMillis());
    }
    @Transactional
    public ScheduleView publish(String doctorId, String departmentId, LocalDate date, String session, String mode, int capacity, long feeMinor) {
        if (!Set.of("AM", "PM").contains(session) || !Set.of("ONLINE", "OFFLINE").contains(mode) || date.getYear() < 1000 || date.getYear() > 9999)
            throw new ApiFailure(400, "INVALID_INPUT", "日期、上午下午或服务方式无效");
        long starts = date.atTime(session.equals("AM") ? 8 : 14, 0).atZone(HOSPITAL_ZONE).toInstant().toEpochMilli();
        long ends = date.atTime(session.equals("AM") ? 12 : 18, 0).atZone(HOSPITAL_ZONE).toInstant().toEpochMilli();
        // Serialize doctor assignment, certification and publication before checking the shared AM/PM unique key.
        var doctor = identity.lockDoctor(doctorId);
        var department = catalog.lockDepartment(departmentId);
        if (department == null || !department.active()) throw new ApiFailure(409, "DEPARTMENT_UNAVAILABLE", "科室已停用或不存在");
        if (!doctor.enabled() || !doctor.credentialStatus().equals("CERTIFIED")) throw new ApiFailure(409, "DOCTOR_NOT_CERTIFIED", "医生未认证或已禁用，不能发布排班");
        if (catalog.assignments().stream().noneMatch(assignment -> assignment.doctorId().equals(doctorId) && assignment.departmentId().equals(departmentId)))
            throw new ApiFailure(409, "DEPARTMENT_MISMATCH", "医生不属于所选科室");
        // Lock acquisition can cross the opening time; validate the boundary immediately before writing.
        if (starts <= clock.millis()) throw new ApiFailure(400, "SESSION_STARTED", "只能发布尚未开始的排班");
        String id = UUID.randomUUID().toString();
        try { catalog.insertSchedule(new CatalogMapper.Schedule(id, doctorId, departmentId, date, session, mode, starts, ends, HOSPITAL_ZONE.getId(), capacity, feeMinor, "PUBLISHED", 0)); }
        catch (DuplicateKeyException error) { throw new ApiFailure(409, "SCHEDULE_CONFLICT", "医生在该日期、半天已有排班，线上线下不能重复发布"); }
        catalog.insertSlots(java.util.stream.IntStream.rangeClosed(1, capacity).mapToObj(position -> new CatalogMapper.Slot(UUID.randomUUID().toString(), id, position)).toList());
        return schedules(true, doctorId, null, date, mode).stream().filter(schedule -> schedule.id().equals(id)).findFirst().orElseThrow();
    }
    @Transactional
    public CatalogMapper.Hospital saveHospital(String name, String address, String description) {
        var hospital = new CatalogMapper.Hospital(name.trim(), address.trim(), description.trim());
        catalog.saveHospital(hospital); return hospital;
    }
    @Transactional
    public CatalogMapper.Department saveDepartment(String id, String name, String description, boolean active) {
        var department = new CatalogMapper.Department(id == null ? UUID.randomUUID().toString() : id, name.trim(), description.trim(), active);
        try {
            if (id == null) catalog.insertDepartment(department);
            else if (catalog.updateDepartment(department) == 0) throw new ApiFailure(404, "NOT_FOUND", "科室不存在");
        } catch (DuplicateKeyException error) { throw new ApiFailure(409, "DEPARTMENT_EXISTS", "科室名称已存在"); }
        return department;
    }
}
