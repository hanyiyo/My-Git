package cn.medical.doctor;

import cn.medical.catalog.CatalogService;
import cn.medical.identity.IdentityService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DoctorHomeService {
    public record HomeView(IdentityService.Profile account, CatalogService.DoctorView doctor,
                           LocalDate hospitalDate, String hospitalZoneId,
                           ScheduleView currentSchedule, ScheduleView nextSchedule,
                           int waitingCount, int inConsultationCount, int completedCount,
                           int pendingApplicationCount, String activeAppointmentId) {}
    public record ScheduleView(String id, LocalDate clinicDate, String session, String serviceMode,
                               long startsAt, long endsAt, String state) {}

    private static final ZoneId HOSPITAL_ZONE = ZoneId.of("Asia/Shanghai");
    private final DoctorHomeMapper home;
    private final IdentityService identity;
    private final CatalogService catalog;
    private final Clock clock;

    public DoctorHomeService(DoctorHomeMapper home, IdentityService identity, CatalogService catalog, Clock clock) {
        this.home = home;
        this.identity = identity;
        this.catalog = catalog;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public HomeView ownHome(String doctorId) {
        long now = clock.millis();
        LocalDate hospitalDate = Instant.ofEpochMilli(now).atZone(HOSPITAL_ZONE).toLocalDate();
        var account = identity.profile(doctorId);
        var doctor = catalog.ownDoctor(doctorId);
        var schedules = home.upcomingSchedules(doctorId, now);
        ScheduleView current = schedules.stream()
            .filter(schedule -> schedule.startsAt() <= now && now < schedule.endsAt())
            .findFirst().map(this::view).orElse(null);
        ScheduleView next = schedules.stream()
            .filter(schedule -> schedule.startsAt() > now)
            .findFirst().map(this::view).orElse(null);
        var counts = home.queueCounts(doctorId, hospitalDate);
        int waiting = counts == null ? 0 : counts.waiting();
        int inConsultation = counts == null ? 0 : counts.inConsultation();
        int completed = counts == null ? 0 : counts.completed();
        String active = counts == null ? null : counts.activeAppointmentId();
        return new HomeView(account, doctor, hospitalDate, HOSPITAL_ZONE.getId(), current, next,
            waiting, inConsultation, completed, home.pendingApplications(doctorId), active);
    }

    private ScheduleView view(DoctorHomeMapper.Schedule schedule) {
        return new ScheduleView(schedule.id(), schedule.clinicDate(), schedule.session(), schedule.serviceMode(),
            schedule.startsAt(), schedule.endsAt(), schedule.state());
    }
}
