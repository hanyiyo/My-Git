package cn.medical.governance;

import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface GovernanceMapper {
    @Select("SELECT id, schedule_id, doctor_id, department_id, requested_by, substitute_doctor_id, state, reason, created_at, updated_at FROM suspension_request WHERE id=#{id}")
    Request request(String id);
    @Select("SELECT id, schedule_id, doctor_id, department_id, requested_by, substitute_doctor_id, state, reason, created_at, updated_at FROM suspension_request ORDER BY created_at DESC, id")
    List<Request> allRequests();
    @Select("SELECT COUNT(*) FROM substitution_choice c JOIN suspension_request r ON r.id=c.request_id WHERE r.schedule_id=#{id} AND c.state='PENDING'")
    int pendingChoices(String id);
    @Select("SELECT id, request_id, appointment_id, patient_id, original_doctor_id, substitute_doctor_id, state, deadline_at, responded_at, reason FROM substitution_choice WHERE id=#{id}")
    Choice choice(String id);
    record Schedule(String id, String doctorId, String departmentId, LocalDate clinicDate, String session, long startsAt, long endsAt, String state) {}
    record Doctor(String id, String departmentId, String credentialStatus, boolean enabled) {}
    record Request(String id, String scheduleId, String doctorId, String departmentId, String requestedBy,
        String substituteDoctorId, String state, String reason, long createdAt, long updatedAt) {}
    record Choice(String id, String requestId, String appointmentId, String patientId, String originalDoctorId,
        String substituteDoctorId, String state, long deadlineAt, Long respondedAt, String reason) {}

    @Select("SELECT id, doctor_id, department_id, clinic_date, clinic_session AS session, starts_at, ends_at, state FROM schedule WHERE id=#{id}")
    Schedule schedule(String id);
    @Select("SELECT id, doctor_assignment.department_id, credential_status, enabled FROM doctor_profile JOIN account ON account.id=doctor_profile.account_id LEFT JOIN doctor_assignment ON doctor_assignment.doctor_id=doctor_profile.account_id WHERE doctor_profile.account_id=#{id}")
    Doctor doctor(String id);
    @Select("SELECT doctor_id FROM department_head WHERE department_id=#{departmentId}")
    String departmentHead(String departmentId);
    @Insert("INSERT INTO department_head (department_id, doctor_id) VALUES (#{departmentId}, #{doctorId}) ON DUPLICATE KEY UPDATE doctor_id=#{doctorId}")
    void assignHead(@Param("departmentId") String departmentId, @Param("doctorId") String doctorId);
    @Select("SELECT COUNT(*) FROM schedule WHERE doctor_id=#{doctorId} AND clinic_date=#{date} AND clinic_session=#{session} AND id<>#{scheduleId} AND state IN ('PUBLISHED','SUSPENSION_PENDING')")
    int conflictingSchedule(@Param("doctorId") String doctorId, @Param("date") LocalDate date, @Param("session") String session, @Param("scheduleId") String scheduleId);
    @Update("UPDATE schedule SET state='SUSPENSION_PENDING' WHERE id=#{id} AND state='PUBLISHED'")
    int pauseSchedule(@Param("id") String id);
    @Update("UPDATE schedule SET state=#{state} WHERE id=#{id}")
    int updateScheduleState(@Param("id") String id, @Param("state") String state);
    @Update("UPDATE catalog_slot SET active=FALSE, reserved_by=NULL, reserved_until=NULL WHERE schedule_id=#{scheduleId}")
    int deactivateSlots(@Param("scheduleId") String scheduleId);

    @Insert("INSERT INTO suspension_request (id, schedule_id, doctor_id, department_id, requested_by, substitute_doctor_id, state, reason, created_at, updated_at) VALUES (#{id}, #{scheduleId}, #{doctorId}, #{departmentId}, #{requestedBy}, #{substituteDoctorId}, #{state}, #{reason}, #{createdAt}, #{updatedAt})")
    void insertRequest(Request request);
    @Select("SELECT id, schedule_id, doctor_id, department_id, requested_by, substitute_doctor_id, state, reason, created_at, updated_at FROM suspension_request WHERE id=#{id} FOR UPDATE")
    Request lockRequest(String id);
    @Select("SELECT id, schedule_id, doctor_id, department_id, requested_by, substitute_doctor_id, state, reason, created_at, updated_at FROM suspension_request WHERE state=#{state} ORDER BY created_at, id")
    List<Request> requests(@Param("state") String state);
    @Update("UPDATE suspension_request SET state=#{state}, substitute_doctor_id=#{substituteDoctorId}, updated_at=#{now} WHERE id=#{id}")
    int updateRequest(@Param("id") String id, @Param("state") String state, @Param("substituteDoctorId") String substituteDoctorId, @Param("now") long now);

    @Insert("INSERT INTO substitution_choice (id, request_id, appointment_id, patient_id, original_doctor_id, substitute_doctor_id, state, deadline_at) VALUES (#{id}, #{requestId}, #{appointmentId}, #{patientId}, #{originalDoctorId}, #{substituteDoctorId}, 'PENDING', #{deadlineAt})")
    void insertChoice(Choice choice);
    @Select("SELECT id, request_id, appointment_id, patient_id, original_doctor_id, substitute_doctor_id, state, deadline_at, responded_at, reason FROM substitution_choice WHERE id=#{id} AND patient_id=#{patientId} FOR UPDATE")
    Choice patientChoice(@Param("id") String id, @Param("patientId") String patientId);
    @Select("SELECT id, request_id, appointment_id, patient_id, original_doctor_id, substitute_doctor_id, state, deadline_at, responded_at, reason FROM substitution_choice WHERE patient_id=#{patientId} AND state='PENDING' ORDER BY deadline_at, id")
    List<Choice> patientChoices(@Param("patientId") String patientId);
    @Select("SELECT id, request_id, appointment_id, patient_id, original_doctor_id, substitute_doctor_id, state, deadline_at, responded_at, reason FROM substitution_choice WHERE request_id=#{requestId}")
    List<Choice> requestChoices(@Param("requestId") String requestId);
    @Update("UPDATE substitution_choice SET state=#{state}, responded_at=#{now}, reason=#{reason} WHERE id=#{id} AND state='PENDING'")
    int updateChoice(@Param("id") String id, @Param("state") String state, @Param("now") long now, @Param("reason") String reason);

    @Insert("INSERT INTO audit_event (id, actor_id, action, object_type, object_id, reason, created_at) VALUES (#{id}, #{actorId}, #{action}, #{objectType}, #{objectId}, #{reason}, #{createdAt})")
    void audit(@Param("id") String id, @Param("actorId") String actorId, @Param("action") String action,
        @Param("objectType") String objectType, @Param("objectId") String objectId, @Param("reason") String reason, @Param("createdAt") long createdAt);
}
