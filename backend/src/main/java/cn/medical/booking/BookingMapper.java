package cn.medical.booking;

import java.time.LocalDate;
import org.apache.ibatis.annotations.*;

@Mapper
public interface BookingMapper {
    record Appointment(String id, String patientId, String scheduleId, String slotId, String doctorId, String departmentId,
        LocalDate clinicDate, String session, String serviceMode, String source, long startsAt, long endsAt,
        long feeMinor, long createdAt, long holdExpiresAt, String state, String idempotencyKey) {}

    @Insert("INSERT INTO appointment (id, patient_id, schedule_id, slot_id, doctor_id, department_id, clinic_date, clinic_session, service_mode, source, starts_at, ends_at, fee_minor, created_at, hold_expires_at, state, idempotency_key) VALUES (#{id}, #{patientId}, #{scheduleId}, #{slotId}, #{doctorId}, #{departmentId}, #{clinicDate}, #{session}, #{serviceMode}, #{source}, #{startsAt}, #{endsAt}, #{feeMinor}, #{createdAt}, #{holdExpiresAt}, #{state}, #{idempotencyKey})")
    void insert(Appointment appointment);
    @Insert("INSERT INTO patient_session_guard (patient_id, clinic_date, clinic_session, appointment_id) VALUES (#{patientId}, #{clinicDate}, #{session}, #{id})")
    void guard(Appointment appointment);
    @Select("SELECT id, patient_id, schedule_id, slot_id, doctor_id, department_id, clinic_date, clinic_session AS session, service_mode, source, starts_at, ends_at, fee_minor, created_at, hold_expires_at, state, idempotency_key FROM appointment WHERE id=#{id} AND patient_id=#{patient}")
    Appointment own(@Param("id") String id, @Param("patient") String patient);
}
