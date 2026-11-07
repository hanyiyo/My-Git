package cn.medical.booking;

import cn.medical.catalog.CatalogService;
import cn.medical.billing.BillingService;
import cn.medical.identity.ApiFailure;
import cn.medical.reception.ReceptionService;
import cn.medical.delivery.DeliveryService;
import java.time.Clock;
import java.util.UUID;
import java.util.List;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

@Service
public class BookingService {
    private final BookingMapper booking;
    private final CatalogService catalog;
    private final BillingService billing;
    private final ReceptionService reception;
    private final DeliveryService delivery;
    private final Clock clock;
    public BookingService(BookingMapper booking, CatalogService catalog, BillingService billing, ReceptionService reception, DeliveryService delivery, Clock clock) {
        this.booking = booking; this.catalog = catalog; this.billing = billing; this.reception = reception; this.delivery = delivery; this.clock = clock;
    }
    public record CareScope(String id, String patientId, String doctorId, String serviceMode, String state) {}
    @Transactional(readOnly = true)
    public CareScope careScope(String id) {
        var appointment = booking.byId(id);
        return appointment == null ? null : new CareScope(id, appointment.patientId(), appointment.doctorId(), appointment.serviceMode(), booking.substitutionPending(id) ? "PENDING_SUBSTITUTION" : appointment.state());
    }
    @Transactional
    public void lockCareScope(String id) {
        var appointment = booking.byId(id);
        if (appointment == null) throw new ApiFailure(404, "NOT_FOUND", "预约不存在");
        booking.lockGuard(appointment.patientId(), appointment.clinicDate(), appointment.session());
        booking.lockAppointment(id);
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BookingMapper.Appointment create(String patient, String schedule, String source, String key) {
        catalog.lockMutation(schedule);
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
        if (!java.util.Set.of("ADVANCE", "WALK_IN").contains(source)) throw new ApiFailure(400, "INVALID_INPUT", "登记来源无效");
        if (occupied != null) {
            var previous = expireIfDue(booking.own(occupied, patient));
            if (!previous.state().equals("EXPIRED")) throw new ApiFailure(409, "REGISTRATION_CONFLICT", "该半天已有有效登记");
        }
        String id = UUID.randomUUID().toString();
        var slot = catalog.reserveSlot(schedule, id, source);
        var appointment = new BookingMapper.Appointment(id, patient, schedule, slot.slotId(), slot.doctorId(), slot.departmentId(),
            slot.clinicDate(), slot.session(), slot.serviceMode(), source, slot.startsAt(), slot.endsAt(), slot.feeMinor(), slot.createdAt(), slot.holdExpiresAt(), "PENDING_PAYMENT", key, null, null, null);
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
    public record PaymentReceipt(BookingMapper.Appointment appointment, BillingService.Payment payment) {}
    public record OrderView(String id, String doctorName, String departmentName, java.time.LocalDate clinicDate,
        String session, String serviceMode, long feeMinor, long holdExpiresAt, long startsAt, long endsAt, long serverNow, String state, String credential,
        String cancellationReason, Long cancelledAt, BillingService.Payment payment, BillingService.Refund refund,
        ReceptionService.Summary reception, boolean substitutionPending) {}

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public List<OrderView> orders(String patient) {
        return booking.list(patient).stream().map(this::expireIfDue).map(appointment -> {
            var schedule = catalog.scheduleDetails(appointment.scheduleId());
            var financial = billing.financialStatus(appointment.id());
            return new OrderView(appointment.id(), catalog.ownDoctor(appointment.doctorId()).displayName(),
                schedule == null ? appointment.departmentId() : schedule.departmentName(), appointment.clinicDate(),
                appointment.session(), appointment.serviceMode(), appointment.feeMinor(), appointment.holdExpiresAt(),
                appointment.startsAt(), appointment.endsAt(), clock.millis(), appointment.state(), appointment.credential(), appointment.cancellationReason(), appointment.cancelledAt(), financial.payment(), financial.refund(), reception.summaryFor(appointment.id()), booking.substitutionPending(appointment.id()));
        }).toList();
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PaymentReceipt simulatePayment(String patient, String id) {
        var appointment = booking.own(id, patient);
        if (appointment == null) throw new ApiFailure(404, "NOT_FOUND", "预约不存在");
        catalog.lockMutation(appointment.scheduleId());
        booking.lockGuard(patient, appointment.clinicDate(), appointment.session());
        appointment = booking.own(id, patient);
        var existingPayment = billing.payment(id);
        if (existingPayment != null) return new PaymentReceipt(appointment, existingPayment);
        appointment = expireIfDue(appointment);
        var outcome = appointment.state().equals("PENDING_PAYMENT")
            ? catalog.tryConfirmSlot(appointment.scheduleId(), appointment.slotId(), id)
            : new CatalogService.Confirmation(false, clock.millis());
        long paidAt = outcome.observedAt();
        if (outcome.confirmed()) {
            if (booking.confirm(id, "APT-" + UUID.randomUUID(), paidAt) != 1)
                throw new IllegalStateException("Locked appointment could not be confirmed");
        } else if (appointment.state().equals("PENDING_PAYMENT")) {
            if (appointment.holdExpiresAt() <= paidAt) booking.expire(id, paidAt);
            else booking.cancel(id, "PAYMENT_UNAVAILABLE", paidAt);
            booking.releaseGuard(appointment);
        }
        var payment = billing.recordSimulatedSuccess(id, appointment.feeMinor(), paidAt);
        if (!outcome.confirmed()) billing.requestFullRefund(id, "LATE_PAYMENT", paidAt);
        if (outcome.confirmed()) delivery.publish(patient, "APPOINTMENT_CONFIRMED", id, "预约已确认", "appointment-confirmed:" + id);
        return new PaymentReceipt(booking.own(id, patient), payment);
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BookingMapper.Appointment cancel(String patient, String id) {
        var appointment = booking.own(id, patient);
        if (appointment == null) throw new ApiFailure(404, "NOT_FOUND", "预约不存在");
        catalog.lockMutation(appointment.scheduleId());
        booking.lockGuard(patient, appointment.clinicDate(), appointment.session());
        appointment = expireIfDue(booking.own(id, patient));
        if (appointment.state().equals("CANCELLED") || appointment.state().equals("EXPIRED")) return appointment;
        if (!java.util.Set.of("PENDING_PAYMENT", "CONFIRMED").contains(appointment.state()))
            throw new ApiFailure(409, "CANCEL_NOT_ALLOWED", "当前预约状态不允许自动取消，请联系管理员");
        long now = catalog.releaseSlot(appointment.scheduleId(), appointment.slotId(), id, appointment.state().equals("CONFIRMED"));
        if (appointment.state().equals("PENDING_PAYMENT") && appointment.holdExpiresAt() <= now) {
            booking.expire(id, now);
        } else {
            booking.cancel(id, "PATIENT_CANCELLATION", now);
            billing.requestFullRefund(id, "PATIENT_CANCELLATION", now);
        }
        booking.releaseGuard(appointment);
        delivery.publish(patient, "APPOINTMENT_CANCELLED", id, "预约已取消", "appointment-cancelled:" + id + ":" + appointment.cancellationReason());
        return booking.own(id, patient);
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BillingService.FinancialStatus financialStatus(String patient, String id) {
        own(patient, id);
        return billing.financialStatus(id);
    }
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void completeAfterEncounter(String id) {
        if (booking.completeAfterEncounter(id) != 1) throw new ApiFailure(409, "APPOINTMENT_STATE_CONFLICT", "预约当前不允许结束接诊");
    }
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void cancelForGovernance(BookingMapper.Appointment appointment, String reason, long now) {
        if (!Set.of("PENDING_PAYMENT", "CONFIRMED").contains(appointment.state())) return;
        catalog.releaseSlot(appointment.scheduleId(), appointment.slotId(), appointment.id(), false);
        booking.cancel(appointment.id(), reason, now);
        if (billing.payment(appointment.id()) != null) billing.requestFullRefund(appointment.id(), reason, now);
        booking.releaseGuard(appointment);
        delivery.publish(appointment.patientId(), "APPOINTMENT_CANCELLED", appointment.id(), "预约因医院流程已取消：" + reason, "appointment-cancelled:" + appointment.id() + ":" + reason);
    }
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void transferDoctor(String appointmentId, String doctorId) {
        if (reception.queueForAppointment(appointmentId) != null) throw new ApiFailure(409, "ENCOUNTER_STARTED", "患者已进入候诊或接诊，请人工处置现场情况后重新安排");
        if (booking.transferDoctor(appointmentId, doctorId) != 1)
            throw new ApiFailure(409, "APPOINTMENT_STATE_CONFLICT", "预约当前不能转交替诊医生");
        booking.setSubstitutionPending(appointmentId, false);
    }
    @Transactional
    public List<BookingMapper.Appointment> lockSchedulePatients(String scheduleId) {
        var appointments = booking.activeForSchedule(scheduleId);
        for (var appointment : appointments) lockCareScope(appointment.id());
        return booking.activeForSchedule(scheduleId);
    }
    @Transactional(readOnly = true)
    public List<BookingMapper.Appointment> scheduleAppointments(String id) { return booking.historyForSchedule(id); }
    @Transactional
    public void awaitSubstitution(String id) { booking.setSubstitutionPending(id, true); }
    @Transactional
    public void cancelForGovernance(String id, String reason) {
        lockCareScope(id);
        var appointment = booking.byId(id);
        if (reception.queueForAppointment(id) != null) throw new ApiFailure(409, "ENCOUNTER_STARTED", "患者已进入候诊或接诊，请先人工处理现场情况");
        cancelForGovernance(appointment, reason, clock.millis());
        booking.setSubstitutionPending(id, false);
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
