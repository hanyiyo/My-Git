package cn.medical.booking;

import cn.medical.catalog.CatalogService;
import cn.medical.identity.ApiFailure;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingService {
    private final BookingMapper booking;
    private final CatalogService catalog;
    private final Clock clock;
    public BookingService(BookingMapper booking, CatalogService catalog, Clock clock) {
        this.booking = booking; this.catalog = catalog; this.clock = clock;
    }
    @Transactional
    public BookingMapper.Appointment create(String patient, String schedule, String source, String key) {
        if (!"ADVANCE".equals(source)) throw new ApiFailure(400, "INVALID_INPUT", "登记来源无效");
        String id = UUID.randomUUID().toString();
        var slot = catalog.reserveAdvanceSlot(schedule, id);
        long now = clock.millis();
        if (now >= slot.startsAt()) throw new ApiFailure(409, "REGISTRATION_CLOSED", "提前预约已截止");
        long expiry = Math.min(now + Duration.ofMinutes(10).toMillis(), slot.endsAt() - Duration.ofMinutes(15).toMillis());
        var appointment = new BookingMapper.Appointment(id, patient, schedule, slot.slotId(), slot.doctorId(), slot.departmentId(),
            slot.clinicDate(), slot.session(), slot.serviceMode(), source, slot.startsAt(), slot.endsAt(), slot.feeMinor(), now, expiry, "PENDING_PAYMENT", key);
        try { booking.insert(appointment); booking.guard(appointment); }
        catch (DuplicateKeyException error) { throw new ApiFailure(409, "REGISTRATION_CONFLICT", "已有该半天登记或重复提交，请查询原订单"); }
        return appointment;
    }
    public BookingMapper.Appointment own(String patient, String id) {
        var appointment = booking.own(id, patient);
        if (appointment == null) throw new ApiFailure(404, "NOT_FOUND", "预约不存在");
        return appointment;
    }
}
