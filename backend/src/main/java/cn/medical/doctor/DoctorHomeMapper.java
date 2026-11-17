package cn.medical.doctor;

import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DoctorHomeMapper {
    record Schedule(String id, LocalDate clinicDate, String session, String serviceMode,
                    long startsAt, long endsAt, String state) {}

    record QueueCounts(int waiting, int inConsultation, int completed, String activeAppointmentId) {}

    @Select("SELECT id, clinic_date, clinic_session AS session, service_mode, starts_at, ends_at, state "
        + "FROM schedule WHERE doctor_id=#{doctor} AND state IN ('PUBLISHED','SUSPENSION_PENDING') "
        + "AND ends_at > #{now} ORDER BY starts_at, id")
    List<Schedule> upcomingSchedules(@Param("doctor") String doctor, @Param("now") long now);

    @Select("SELECT "
        + "SUM(CASE WHEN q.state='WAITING' AND a.state='CONFIRMED' THEN 1 ELSE 0 END) AS waiting, "
        + "SUM(CASE WHEN q.state='IN_CONSULTATION' THEN 1 ELSE 0 END) AS in_consultation, "
        + "SUM(CASE WHEN q.state='COMPLETED' THEN 1 ELSE 0 END) AS completed, "
        + "MAX(CASE WHEN q.state='IN_CONSULTATION' THEN q.appointment_id ELSE NULL END) AS active_appointment_id "
        + "FROM reception_queue q JOIN appointment a ON a.id=q.appointment_id "
        + "WHERE q.doctor_id=#{doctor} AND a.clinic_date=#{date}")
    QueueCounts queueCounts(@Param("doctor") String doctor, @Param("date") LocalDate date);

    @Select("SELECT COUNT(*) FROM ("
        + "SELECT id FROM suspension_request WHERE requested_by=#{doctor} "
        + "AND state IN ('HEAD_REVIEW','ADMIN_REVIEW','SUBSTITUTE_REVIEW') "
        + "UNION SELECT r.id FROM suspension_request r JOIN doctor_assignment a ON a.department_id=r.department_id "
        + "JOIN department d ON d.id=r.department_id "
        + "WHERE a.doctor_id=#{doctor} AND d.active=TRUE AND r.state='HEAD_REVIEW' "
        + "UNION SELECT CONCAT('choice:', c.id) FROM substitution_choice c "
        + "WHERE c.substitute_doctor_id=#{doctor} AND c.state='PENDING'"
        + ") pending")
    int pendingApplications(@Param("doctor") String doctor);
}
