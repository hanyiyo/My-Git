package cn.medical.catalog;

import java.util.List;
import java.time.LocalDate;
import org.apache.ibatis.annotations.*;

@Mapper
public interface CatalogMapper {
    record Hospital(String name, String address, String description) {}
    record Department(String id, String name, String description, boolean active) {}
    record Assignment(String doctorId, String departmentId, String departmentName, boolean active) {}
    record Schedule(String id, String doctorId, String departmentId, LocalDate clinicDate, String session, String serviceMode,
        long startsAt, long endsAt, String zoneId, int capacity, long feeMinor, String state, int availableSlots) {}
    record Slot(String id, String scheduleId, int position) {}
    @Select("SELECT name, address, description FROM hospital WHERE id = 1")
    Hospital hospital();
    @Update("UPDATE hospital SET name=#{name}, address=#{address}, description=#{description} WHERE id=1")
    void saveHospital(Hospital hospital);
    @Select("SELECT id, name, description, active FROM department ORDER BY name, id")
    List<Department> departments();
    @Select("SELECT id, name, description, active FROM department WHERE id=#{id} FOR UPDATE")
    Department lockDepartment(String id);
    @Insert("INSERT INTO department (id, name, description, active) VALUES (#{id}, #{name}, #{description}, #{active})")
    void insertDepartment(Department department);
    @Update("UPDATE department SET name=#{name}, description=#{description}, active=#{active} WHERE id=#{id}")
    int updateDepartment(Department department);
    @Select("SELECT a.doctor_id, a.department_id, d.name AS department_name, d.active FROM doctor_assignment a JOIN department d ON d.id=a.department_id ORDER BY a.doctor_id")
    List<Assignment> assignments();
    @Insert("INSERT INTO doctor_assignment (doctor_id, department_id) VALUES (#{doctor}, #{department}) ON DUPLICATE KEY UPDATE department_id=VALUES(department_id)")
    void assignDoctor(@Param("doctor") String doctor, @Param("department") String department);
    @Select("SELECT s.id, s.doctor_id, s.department_id, s.clinic_date, s.clinic_session AS session, s.service_mode, s.starts_at, s.ends_at, s.zone_id, s.capacity, s.fee_minor, s.state, (SELECT COUNT(*) FROM catalog_slot t WHERE t.schedule_id=s.id AND t.active=TRUE) AS available_slots FROM schedule s ORDER BY s.starts_at, s.id")
    List<Schedule> schedules();
    @Select("SELECT COUNT(*) FROM schedule WHERE doctor_id=#{doctor} AND ends_at > #{now} AND state='PUBLISHED'")
    int openSchedules(@Param("doctor") String doctor, @Param("now") long now);
    @Insert("INSERT INTO schedule (id, doctor_id, department_id, clinic_date, clinic_session, service_mode, starts_at, ends_at, zone_id, capacity, fee_minor, state) VALUES (#{id}, #{doctorId}, #{departmentId}, #{clinicDate}, #{session}, #{serviceMode}, #{startsAt}, #{endsAt}, #{zoneId}, #{capacity}, #{feeMinor}, #{state})")
    void insertSchedule(Schedule schedule);
    @Insert("<script>INSERT INTO catalog_slot (id, schedule_id, position, active) VALUES <foreach collection='slots' item='slot' separator=','>(#{slot.id}, #{slot.scheduleId}, #{slot.position}, TRUE)</foreach></script>")
    void insertSlots(@Param("slots") List<Slot> slots);
}
