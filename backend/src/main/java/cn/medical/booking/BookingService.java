package cn.medical.booking;

import cn.medical.catalog.CatalogService;
import cn.medical.identity.ApiFailure;
import java.time.Clock;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

@Service
public class BookingService {
    private final BookingMapper booking;
    private final CatalogService catalog;
    private final Clock clock;
    public BookingService(BookingMapper booking, CatalogService catalog, Clock clock) {
        this.booking = booking; this.catalog = catalog; this.clock = clock;
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BookingMapper.Appointment create(String patient, String schedule, String source, String key) {
        var session = catalog.registrationSession(schedule);
        // The stable patient/day/half row always precedes catalog/slot and appointment locks.
        booking.ensureGuard(patient, session.clinicDate(), session.session());
        var occupied = booking.lockGuard(patient, session.clinicDate(), session.session());
        var original = booking.byKey(patient, key);
        if (original != null) {
            if (!original.scheduleId().equals(schedule) || !original.source().equals(source))
                throw new ApiFailure(409, "IDEMPOTENCY_CONFLICT", "同一提交标识不能用于不同登记");
            return expireIfDue(original);
        }
        if (!"ADVANCE".equals(source)) throw new ApiFailure(400, "INVALID_INPUT", "登记来源无效");
        if (occupied != null) {
            var previous = expireIfDue(booking.own(occupied, patient));
            if (!previous.state().equals("EXPIRED")) throw new ApiFailure(409, "REGISTRATION_CONFLICT", "该半天已有有效登记");
        }
        String id = UUID.randomUUID().toString();
        var slot = catalog.reserveAdvanceSlot(schedule, id);
        var appointment = new BookingMapper.Appointment(id, patient, schedule, slot.slotId(), slot.doctorId(), slot.departmentId(),
            slot.clinicDate(), slot.session(), slot.serviceMode(), source, slot.startsAt(), slot.endsAt(), slot.feeMinor(), slot.createdAt(), slot.holdExpiresAt(), "PENDING_PAYMENT", key);
        try { booking.insert(appointment); booking.occupyGuard(appointment); }
        catch (DuplicateKeyException error) { throw new ApiFailure(409, "IDEMPOTENCY_CONFLICT", "同一提交标识不能用于不同登记"); }
        return appointment;
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BookingMapper.Appointment own(String patient, String id) {
        var appointment = booking.own(id, patient);
        if (appointment == null) throw new ApiFailure(404, "NOT_FOUND", "预约不存在");
        booking.lockGuard(patient, appointment.clinicDate(), appointment.session());
        return expireIfDue(booking.own(id, patient));
    }
    private BookingMapper.Appointment expireIfDue(BookingMapper.Appointment appointment) {
        long now = clock.millis();
        if (appointment.state().equals("PENDING_PAYMENT") && appointment.holdExpiresAt() <= now) {
            // Catalog leases become reusable by deadline; never clear a slot that a new order may own.
            if (booking.expire(appointment.id(), now) == 1) booking.releaseGuard(appointment);
            return booking.own(appointment.id(), appointment.patientId());
        }
        return appointment;
    }
}
