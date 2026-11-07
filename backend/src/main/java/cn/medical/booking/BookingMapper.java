package cn.medical.booking;

import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface BookingMapper {
    @Select("SELECT substitution_pending FROM appointment WHERE id=#{id}")
    boolean substitutionPending(String id);
    @Update("UPDATE appointment SET substitution_pending=#{pending} WHERE id=#{id}")
    void setSubstitutionPending(@Param("id") String id, @Param("pending") boolean pending);
    record Appointment(String id, String patientId, String scheduleId, String slotId, String doctorId, String departmentId,
        LocalDate clinicDate, String session, String serviceMode, String source, long startsAt, long endsAt,
        long feeMinor, long createdAt, long holdExpiresAt, String state, String idempotencyKey, String credential,
        String cancellationReason, Long cancelledAt) {}

    @Insert("INSERT INTO appointment (id, patient_id, schedule_id, slot_id, doctor_id, department_id, clinic_date, clinic_session, service_mode, source, starts_at, ends_at, fee_minor, created_at, hold_expires_at, state, idempotency_key) VALUES (#{id}, #{patientId}, #{scheduleId}, #{slotId}, #{doctorId}, #{departmentId}, #{clinicDate}, #{session}, #{serviceMode}, #{source}, #{startsAt}, #{endsAt}, #{feeMinor}, #{createdAt}, #{holdExpiresAt}, #{state}, #{idempotencyKey})")
    void insert(Appointment appointment);
    @Insert("INSERT INTO patient_session_guard (patient_id, clinic_date, clinic_session) VALUES (#{patient}, #{date}, #{session}) ON DUPLICATE KEY UPDATE patient_id=patient_id")
    void ensureGuard(@Param("patient") String patient, @Param("date") LocalDate date, @Param("session") String session);
    @Select("SELECT appointment_id FROM patient_session_guard WHERE patient_id=#{patient} AND clinic_date=#{date} AND clinic_session=#{session} FOR UPDATE")
    String lockGuard(@Param("patient") String patient, @Param("date") LocalDate date, @Param("session") String session);
    @Update("UPDATE patient_session_guard SET appointment_id=#{id} WHERE patient_id=#{patientId} AND clinic_date=#{clinicDate} AND clinic_session=#{session}")
    void occupyGuard(Appointment appointment);
    @Update("UPDATE patient_session_guard SET appointment_id=NULL WHERE patient_id=#{patientId} AND clinic_date=#{clinicDate} AND clinic_session=#{session} AND appointment_id=#{id}")
    void releaseGuard(Appointment appointment);
    @Update("UPDATE appointment SET state='EXPIRED' WHERE id=#{id} AND state='PENDING_PAYMENT' AND hold_expires_at <= #{now}")
    int expire(@Param("id") String id, @Param("now") long now);
    @Select("SELECT id, patient_id, schedule_id, slot_id, doctor_id, department_id, clinic_date, clinic_session AS session, service_mode, source, starts_at, ends_at, fee_minor, created_at, hold_expires_at, state, idempotency_key, credential, cancellation_reason, cancelled_at FROM appointment WHERE patient_id=#{patient} AND idempotency_key=#{key}")
    Appointment byKey(@Param("patient") String patient, @Param("key") String key);
    @Select("SELECT id, patient_id, schedule_id, slot_id, doctor_id, department_id, clinic_date, clinic_session AS session, service_mode, source, starts_at, ends_at, fee_minor, created_at, hold_expires_at, state, idempotency_key, credential, cancellation_reason, cancelled_at FROM appointment WHERE id=#{id} AND patient_id=#{patient}")
    Appointment own(@Param("id") String id, @Param("patient") String patient);
    @Select("SELECT id, patient_id, schedule_id, slot_id, doctor_id, department_id, clinic_date, clinic_session AS session, service_mode, source, starts_at, ends_at, fee_minor, created_at, hold_expires_at, state, idempotency_key, credential, cancellation_reason, cancelled_at FROM appointment WHERE patient_id=#{patient} ORDER BY created_at DESC, id DESC")
    List<Appointment> list(@Param("patient") String patient);
    @Select("SELECT id, patient_id, schedule_id, slot_id, doctor_id, department_id, clinic_date, clinic_session AS session, service_mode, source, starts_at, ends_at, fee_minor, created_at, hold_expires_at, state, idempotency_key, credential, cancellation_reason, cancelled_at FROM appointment WHERE id=#{id}")
    Appointment byId(@Param("id") String id);
    @Select("SELECT id FROM appointment WHERE id=#{id} FOR UPDATE")
    String lockAppointment(@Param("id") String id);
    @Update("UPDATE appointment SET state='CONFIRMED', credential=#{credential} WHERE id=#{id} AND state='PENDING_PAYMENT' AND hold_expires_at > #{now}")
    int confirm(@Param("id") String id, @Param("credential") String credential, @Param("now") long now);
    @Update("UPDATE appointment SET state='CANCELLED', cancellation_reason=#{reason}, cancelled_at=#{now} WHERE id=#{id} AND state IN ('PENDING_PAYMENT', 'CONFIRMED')")
    int cancel(@Param("id") String id, @Param("reason") String reason, @Param("now") long now);
    @Update("UPDATE appointment SET state='COMPLETED' WHERE id=#{id} AND state='CONFIRMED'")
    int completeAfterEncounter(@Param("id") String id);
    @Select("SELECT id, patient_id, schedule_id, slot_id, doctor_id, department_id, clinic_date, clinic_session AS session, service_mode, source, starts_at, ends_at, fee_minor, created_at, hold_expires_at, state, idempotency_key, credential, cancellation_reason, cancelled_at FROM appointment WHERE schedule_id=#{scheduleId} AND state IN ('PENDING_PAYMENT', 'CONFIRMED') ORDER BY patient_id")
    List<Appointment> activeForSchedule(@Param("scheduleId") String scheduleId);
    @Select("SELECT id, patient_id, schedule_id, slot_id, doctor_id, department_id, clinic_date, clinic_session AS session, service_mode, source, starts_at, ends_at, fee_minor, created_at, hold_expires_at, state, idempotency_key, credential, cancellation_reason, cancelled_at FROM appointment WHERE schedule_id=#{scheduleId} ORDER BY created_at, id")
    List<Appointment> historyForSchedule(@Param("scheduleId") String scheduleId);
    @Update("UPDATE appointment SET doctor_id=#{doctorId} WHERE id=#{id} AND state='CONFIRMED'")
    int transferDoctor(@Param("id") String id, @Param("doctorId") String doctorId);
}
