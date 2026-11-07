package cn.medical.reception;

import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface ReceptionMapper {
    record Appointment(String id, String patientId, String scheduleId, String doctorId, LocalDate clinicDate,
        String session, String serviceMode, String source, long startsAt, long endsAt, String state) {}
    record QueueEntry(long id, String appointmentId, String scheduleId, String doctorId, String patientId,
        String patientIdentifier, LocalDate clinicDate, String session, String serviceMode, String source,
        int queueGroup, long queuedAt, long checkedInAt, int restorationCount, String state,
        Long calledAt, Long startedAt, Long missedAt, Long completedAt) {}

    @Select("SELECT id, patient_id, schedule_id, doctor_id, clinic_date, clinic_session AS session, service_mode, source, starts_at, ends_at, state FROM appointment WHERE id=#{id} AND patient_id=#{patient} FOR UPDATE")
    Appointment patientAppointment(@Param("id") String id, @Param("patient") String patient);
    @Select("SELECT id, patient_id, schedule_id, doctor_id, clinic_date, clinic_session AS session, service_mode, source, starts_at, ends_at, state FROM appointment WHERE id=#{id} FOR UPDATE")
    Appointment appointment(@Param("id") String id);
    @Select("SELECT id, patient_id, schedule_id, doctor_id, clinic_date, clinic_session AS session, service_mode, source, starts_at, ends_at, state FROM appointment WHERE state='CONFIRMED' AND ends_at > #{now} AND ends_at - 900000 <= #{now} AND NOT EXISTS (SELECT 1 FROM reception_queue q WHERE q.appointment_id=appointment.id) FOR UPDATE")
    List<Appointment> dueNoShows(@Param("now") long now);
    @Select("SELECT q.id, q.appointment_id, q.schedule_id, q.doctor_id, q.patient_id, a.identifier AS patient_identifier, p.clinic_date, p.clinic_session AS session, p.service_mode, p.source, q.queue_group, q.queued_at, q.checked_in_at, q.restoration_count, q.state, q.called_at, q.started_at, q.missed_at, q.completed_at FROM reception_queue q JOIN account a ON a.id=q.patient_id JOIN appointment p ON p.id=q.appointment_id WHERE q.appointment_id=#{id} FOR UPDATE")
    QueueEntry lockedQueue(@Param("id") String id);
    @Insert("INSERT INTO reception_queue (appointment_id, schedule_id, doctor_id, patient_id, queue_group, queued_at, checked_in_at, restoration_count, state) VALUES (#{appointmentId}, #{scheduleId}, #{doctorId}, #{patientId}, #{queueGroup}, #{queuedAt}, #{checkedInAt}, 0, 'WAITING')")
    void insertQueue(@Param("appointmentId") String appointmentId, @Param("scheduleId") String scheduleId, @Param("doctorId") String doctorId,
        @Param("patientId") String patientId, @Param("queueGroup") int queueGroup, @Param("queuedAt") long queuedAt, @Param("checkedInAt") long checkedInAt);
    @Insert("INSERT INTO reception_queue (appointment_id, schedule_id, doctor_id, patient_id, queue_group, queued_at, checked_in_at, restoration_count, state, missed_at) VALUES (#{appointmentId}, #{scheduleId}, #{doctorId}, #{patientId}, 1, #{now}, #{now}, 0, 'MISSED', #{now})")
    void insertNoShow(@Param("appointmentId") String appointmentId, @Param("scheduleId") String scheduleId, @Param("doctorId") String doctorId,
        @Param("patientId") String patientId, @Param("now") long now);
    @Select("SELECT q.id, q.appointment_id, q.schedule_id, q.doctor_id, q.patient_id, a.identifier AS patient_identifier, p.clinic_date, p.clinic_session AS session, p.service_mode, p.source, q.queue_group, q.queued_at, q.checked_in_at, q.restoration_count, q.state, q.called_at, q.started_at, q.missed_at, q.completed_at FROM reception_queue q JOIN account a ON a.id=q.patient_id JOIN appointment p ON p.id=q.appointment_id WHERE q.appointment_id=#{id}")
    QueueEntry queue(@Param("id") String id);
    @Update("UPDATE reception_queue SET queue_group=3, queued_at=#{now}, restoration_count=1, state='WAITING', missed_at=NULL WHERE appointment_id=#{id} AND state='MISSED' AND restoration_count=0")
    int restore(@Param("id") String id, @Param("now") long now);
    @Select("<script>SELECT q.id, q.appointment_id, q.schedule_id, q.doctor_id, q.patient_id, a.identifier AS patient_identifier, p.clinic_date, p.clinic_session AS session, p.service_mode, p.source, q.queue_group, q.queued_at, q.checked_in_at, q.restoration_count, q.state, q.called_at, q.started_at, q.missed_at, q.completed_at FROM reception_queue q JOIN account a ON a.id=q.patient_id JOIN appointment p ON p.id=q.appointment_id WHERE q.doctor_id=#{doctor} <if test='scheduleId != null'>AND q.schedule_id=#{scheduleId}</if> <if test='date != null'>AND p.clinic_date=#{date}</if> ORDER BY p.starts_at, q.queue_group, q.queued_at, q.id</script>")
    List<QueueEntry> doctorQueue(@Param("doctor") String doctor, @Param("scheduleId") String scheduleId, @Param("date") LocalDate date);
    @Select("SELECT q.id, q.appointment_id, q.schedule_id, q.doctor_id, q.patient_id, a.identifier AS patient_identifier, p.clinic_date, p.clinic_session AS session, p.service_mode, p.source, q.queue_group, q.queued_at, q.checked_in_at, q.restoration_count, q.state, q.called_at, q.started_at, q.missed_at, q.completed_at FROM reception_queue q JOIN account a ON a.id=q.patient_id JOIN appointment p ON p.id=q.appointment_id WHERE q.schedule_id=#{schedule} AND q.state='WAITING' ORDER BY q.queue_group, q.queued_at, q.id LIMIT 1")
    QueueEntry nextWaiting(@Param("schedule") String schedule);
    @Update("UPDATE reception_queue SET state='CALLED', called_at=#{now} WHERE appointment_id=#{id} AND state='WAITING'")
    int call(@Param("id") String id, @Param("now") long now);
    @Update("UPDATE reception_queue SET state='IN_CONSULTATION', started_at=#{now} WHERE appointment_id=#{id} AND state='CALLED'")
    int start(@Param("id") String id, @Param("now") long now);
    @Update("UPDATE reception_queue SET state='MISSED', missed_at=#{now} WHERE appointment_id=#{id} AND state='CALLED'")
    int miss(@Param("id") String id, @Param("now") long now);
    @Update("UPDATE reception_queue SET state='COMPLETED', completed_at=#{now} WHERE appointment_id=#{id} AND state='IN_CONSULTATION'")
    int complete(@Param("id") String id, @Param("now") long now);
}
