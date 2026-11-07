package cn.medical;

import java.util.Map;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.Clock;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"medical.captcha-ttl=2m", "spring.data.redis.database=4"})
@Import({TestAnswers.class, CatalogHttpTest.TimeControl.class})
class CatalogHttpTest extends HttpFixture {
    @LocalServerPort int port;
    @Autowired AdjustableClock clock;
    @BeforeEach @AfterEach void resetClock() { clock.now = Instant.now(); }
    static class AdjustableClock extends Clock {
        volatile Instant now = Instant.now();
        public ZoneId getZone() { return ZoneId.of("UTC"); }
        public Clock withZone(ZoneId zone) { return Clock.fixed(now, zone); }
        public Instant instant() { return now; }
    }
    @TestConfiguration static class TimeControl {
        @Bean @Primary AdjustableClock adjustableClock() { return new AdjustableClock(); }
    }
    Browser admin() throws Exception {
        var browser = new Browser(port);
        assertThat(browser.login("admin-foundation-test", "fictional-test-administrator-password").status()).isEqualTo(200);
        return browser;
    }
    String department(Browser admin) throws Exception {
        return admin.call("POST", "/api/v1/admin/catalog/departments", Map.of("name", identifier(), "description", "门诊", "active", true)).body().path("id").asText();
    }
    record DoctorAccount(String id, Browser browser) {}
    DoctorAccount doctor(Browser admin, String department, String status) throws Exception {
        var browser = new Browser(port); String name = identifier();
        String id = browser.register(name, "DOCTOR").body().path("account").path("id").asText();
        browser.login(name, PASSWORD);
        assertThat(admin.call("PUT", "/api/v1/admin/catalog/doctors/" + id, Map.of("displayName", "虚构周医生", "title", "主治医师", "biography", "演示资料", "departmentId", department, "credentialStatus", status)).status()).isEqualTo(200);
        return new DoctorAccount(id, browser);
    }
    Map<String, Object> schedule(String doctor, String department, LocalDate date, String session, String mode, int capacity) {
        return Map.of("doctorId", doctor, "departmentId", department, "clinicDate", date.toString(), "session", session, "serviceMode", mode, "capacity", capacity, "feeMinor", 2000);
    }
    @Test void administratorMaintainsHospitalAndDepartmentsWhilePatientCannotWrite() throws Exception {
        var admin = admin();
        assertThat(admin.call("PUT", "/api/v1/admin/catalog/hospital", Map.of("name", "虚构大学校医院", "address", "虚构路一号", "description", "门诊服务说明")).status()).isEqualTo(200);
        var patient = new Browser(port); String name = identifier(); patient.register(name, "PATIENT"); patient.login(name, PASSWORD);
        assertThat(patient.call("GET", "/api/v1/catalog/hospital", null).body().path("address").asText()).isEqualTo("虚构路一号");
        assertThat(patient.call("PUT", "/api/v1/admin/catalog/hospital", Map.of("name", "越权", "address", "越权", "description", "")).status()).isEqualTo(403);
        var created = admin.call("POST", "/api/v1/admin/catalog/departments", Map.of("name", identifier(), "description", "虚构科室", "active", true));
        assertThat(created.status()).isEqualTo(201);
        String id = created.body().path("id").asText();
        assertThat(patient.call("GET", "/api/v1/catalog/departments", null).body().toString()).contains(id);
        assertThat(admin.call("PUT", "/api/v1/admin/catalog/departments/" + id, Map.of("name", identifier(), "description", "暂停服务", "active", false)).status()).isEqualTo(200);
        assertThat(patient.call("GET", "/api/v1/catalog/departments", null).body().toString()).doesNotContain(id);
    }

    @Test void doctorSeesOwnStatusAndPatientSeesOnlyCertifiedActiveDepartmentMembers() throws Exception {
        var admin = admin(); var doctor = new Browser(port); String name = identifier();
        String doctorId = doctor.register(name, "DOCTOR").body().path("account").path("id").asText();
        doctor.login(name, PASSWORD);
        assertThat(doctor.call("GET", "/api/v1/doctor/catalog/profile", null).body().path("credentialStatus").asText()).isEqualTo("PENDING");
        String department = admin.call("POST", "/api/v1/admin/catalog/departments", Map.of("name", identifier(), "description", "门诊", "active", true)).body().path("id").asText();
        var details = Map.of("displayName", "虚构林医生", "title", "主治医师", "biography", "虚构资料", "departmentId", department, "credentialStatus", "CERTIFIED");
        assertThat(doctor.call("PUT", "/api/v1/admin/catalog/doctors/" + doctorId, details).status()).isEqualTo(403);
        assertThat(admin.call("PUT", "/api/v1/admin/catalog/doctors/" + doctorId, details).status()).isEqualTo(200);
        assertThat(doctor.call("GET", "/api/v1/doctor/catalog/profile", null).body().path("departmentId").asText()).isEqualTo(department);
        assertThat(doctor.call("GET", "/api/v1/doctor/catalog/profile", null).body().path("credentialStatus").asText()).isEqualTo("CERTIFIED");
        var patient = new Browser(port); String patientName = identifier(); patient.register(patientName, "PATIENT"); patient.login(patientName, PASSWORD);
        var visible = patient.call("GET", "/api/v1/catalog/doctors?departmentId=" + department, null);
        assertThat(visible.body().toString()).contains(doctorId, "虚构林医生").doesNotContain(name);
        assertThat(patient.call("GET", "/api/v1/doctor/catalog/profile", null).status()).isEqualTo(403);
        assertThat(admin.call("PUT", "/api/v1/admin/catalog/doctors/" + doctorId, Map.of("displayName", "虚构林医生", "title", "主治医师", "biography", "", "departmentId", department, "credentialStatus", "REJECTED")).status()).isEqualTo(200);
        assertThat(patient.call("GET", "/api/v1/catalog/doctors?departmentId=" + department, null).body().toString()).doesNotContain(doctorId);
    }

    @Test void publishedSchedulesHaveRealSlotsRejectConflictsAndDisappearWhenResourcesBecomeInvalid() throws Exception {
        var admin = admin(); String department = department(admin); var doctor = doctor(admin, department, "CERTIFIED");
        var date = LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(2);
        var published = admin.call("POST", "/api/v1/admin/catalog/schedules", schedule(doctor.id(), department, date, "AM", "ONLINE", 3));
        assertThat(published.status()).isEqualTo(201);
        String id = published.body().path("id").asText();
        assertThat(published.body().path("availableSlots").asInt()).isEqualTo(3);
        assertThat(published.body().path("startsAt").asLong()).isEqualTo(date.atTime(8, 0).atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli());
        assertThat(published.body().path("endsAt").asLong()).isEqualTo(date.atTime(12, 0).atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli());
        assertThat(published.body().path("feeMinor").asInt()).isEqualTo(2000);
        assertThat(doctor.browser().call("GET", "/api/v1/doctor/catalog/schedules", null).body().toString()).contains(id);
        assertThat(doctor.browser().call("POST", "/api/v1/admin/catalog/schedules", schedule(doctor.id(), department, date.plusDays(1), "AM", "ONLINE", 3)).status()).isEqualTo(403);
        var conflict = admin.call("POST", "/api/v1/admin/catalog/schedules", schedule(doctor.id(), department, date, "AM", "OFFLINE", 3));
        assertThat(conflict.status()).isEqualTo(409);
        assertThat(conflict.body().path("error").path("message").asText()).isNotBlank();
        assertThat(admin.call("POST", "/api/v1/admin/catalog/schedules", schedule(doctor.id(), department, date, "PM", "OFFLINE", 0)).status()).isEqualTo(400);
        assertThat(admin.call("POST", "/api/v1/admin/catalog/schedules", schedule(doctor.id(), department, date.minusDays(10), "PM", "OFFLINE", 2)).status()).isEqualTo(400);
        assertThat(admin.call("POST", "/api/v1/admin/catalog/schedules", schedule(doctor.id(), department, date, "EVENING", "OFFLINE", 2)).status()).isEqualTo(400);
        var pending = doctor(admin, department, "PENDING");
        assertThat(pending.browser().call("GET", "/api/v1/doctor/catalog/schedules", null).body().toString()).doesNotContain(id);
        assertThat(admin.call("POST", "/api/v1/admin/catalog/schedules", schedule(pending.id(), department, date, "PM", "ONLINE", 2)).status()).isEqualTo(409);
        String otherDepartment = department(admin);
        assertThat(admin.call("POST", "/api/v1/admin/catalog/schedules", schedule(doctor.id(), otherDepartment, date, "PM", "ONLINE", 2)).status()).isEqualTo(409);
        var patient = new Browser(port); String patientName = identifier(); patient.register(patientName, "PATIENT"); patient.login(patientName, PASSWORD);
        assertThat(patient.call("GET", "/api/v1/catalog/schedules?doctorId=" + doctor.id(), null).body().toString()).contains(id);
        assertThat(admin.call("PUT", "/api/v1/admin/catalog/doctors/" + doctor.id(), Map.of("displayName", "虚构周医生", "title", "主治医师", "biography", "", "departmentId", department, "credentialStatus", "REJECTED")).status()).isEqualTo(200);
        assertThat(patient.call("GET", "/api/v1/catalog/schedules?doctorId=" + doctor.id(), null).body().toString()).doesNotContain(id);
        assertThat(admin.call("PUT", "/api/v1/admin/catalog/doctors/" + doctor.id(), Map.of("displayName", "虚构周医生", "title", "主治医师", "biography", "", "departmentId", department, "credentialStatus", "CERTIFIED")).status()).isEqualTo(200);
        assertThat(admin.call("PATCH", "/api/v1/admin/accounts/" + doctor.id() + "/enabled", Map.of("enabled", false)).status()).isEqualTo(204);
        assertThat(patient.call("GET", "/api/v1/catalog/schedules?doctorId=" + doctor.id(), null).body().toString()).doesNotContain(id);
        assertThat(admin.call("PATCH", "/api/v1/admin/accounts/" + doctor.id() + "/enabled", Map.of("enabled", true)).status()).isEqualTo(204);
        for (int invalid : new int[] {-1, 501}) assertThat(admin.call("POST", "/api/v1/admin/catalog/schedules", schedule(doctor.id(), department, date, "PM", "ONLINE", invalid)).status()).isEqualTo(400);
        var invalidFee = new java.util.HashMap<>(schedule(doctor.id(), department, date, "PM", "ONLINE", 2));
        invalidFee.put("feeMinor", -1);
        assertThat(admin.call("POST", "/api/v1/admin/catalog/schedules", invalidFee).status()).isEqualTo(400);
        assertThat(patient.call("GET", "/api/v1/catalog/schedules?doctorId=" + doctor.id(), null).body().toString()).contains(id);
        assertThat(admin.call("PUT", "/api/v1/admin/catalog/departments/" + department, Map.of("name", identifier(), "description", "停用", "active", false)).status()).isEqualTo(200);
        assertThat(patient.call("GET", "/api/v1/catalog/schedules?doctorId=" + doctor.id(), null).body().toString()).doesNotContain(id);
        assertThat(patient.call("GET", "/api/v1/catalog/doctors?departmentId=" + department, null).body().toString()).doesNotContain(doctor.id());
        assertThat(admin.call("POST", "/api/v1/admin/catalog/schedules", schedule(doctor.id(), department, date, "PM", "ONLINE", 2)).status()).isEqualTo(409);
        assertThat(doctor.browser().call("GET", "/api/v1/doctor/catalog/schedules", null).body().toString()).contains(id);
    }

    @Test void concurrentPublicationAcrossModesCreatesOneScheduleAndOneSetOfSlots() throws Exception {
        var admin = admin(); String department = department(admin); var doctor = doctor(admin, department, "CERTIFIED");
        var date = LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(3);
        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var ready = new java.util.concurrent.CountDownLatch(1);
            var tasks = java.util.stream.IntStream.range(0, 6).mapToObj(index -> executor.submit(() -> {
                ready.await();
                return admin.call("POST", "/api/v1/admin/catalog/schedules", schedule(doctor.id(), department, date, "AM", index % 2 == 0 ? "ONLINE" : "OFFLINE", 2)).status();
            })).toList();
            ready.countDown(); int published = 0;
            for (var task : tasks) { int status = task.get(); assertThat(status).isIn(201, 409); if (status == 201) published++; }
            assertThat(published).isEqualTo(1);
        }
        var visible = admin.call("GET", "/api/v1/catalog/schedules?doctorId=" + doctor.id(), null);
        assertThat(visible.body().size()).isEqualTo(1);
        assertThat(visible.body().get(0).path("availableSlots").asInt()).isEqualTo(2);
        String otherDepartment = department(admin);
        assertThat(admin.call("PUT", "/api/v1/admin/catalog/doctors/" + doctor.id(), Map.of("displayName", "虚构周医生", "title", "主治医师", "biography", "", "departmentId", otherDepartment, "credentialStatus", "CERTIFIED")).status()).isEqualTo(409);
    }

    @Test void onlineAdvanceAndOfflineWalkInCloseAtExactHospitalTimeBoundaries() throws Exception {
        var admin = admin(); String department = department(admin); var online = doctor(admin, department, "CERTIFIED"); var offline = doctor(admin, department, "CERTIFIED");
        var date = LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(2);
        var first = admin.call("POST", "/api/v1/admin/catalog/schedules", schedule(online.id(), department, date, "AM", "ONLINE", 2));
        var second = admin.call("POST", "/api/v1/admin/catalog/schedules", schedule(offline.id(), department, date, "AM", "OFFLINE", 2));
        assertThat(first.status()).isEqualTo(201); assertThat(second.status()).isEqualTo(201);
        clock.now = Instant.ofEpochMilli(first.body().path("startsAt").asLong());
        assertThat(admin.call("POST", "/api/v1/admin/catalog/schedules", schedule(offline.id(), department, date, "AM", "ONLINE", 2)).status()).isEqualTo(400);
        assertThat(admin.call("GET", "/api/v1/catalog/schedules?doctorId=" + online.id(), null).body().size()).isZero();
        var walkIn = admin.call("GET", "/api/v1/catalog/schedules?doctorId=" + offline.id(), null).body();
        assertThat(walkIn.size()).isEqualTo(1);
        assertThat(walkIn.get(0).path("walkInAvailable").asBoolean()).isTrue();
        assertThat(walkIn.get(0).path("advanceAvailable").asBoolean()).isFalse();
        clock.now = Instant.ofEpochMilli(second.body().path("endsAt").asLong() - java.time.Duration.ofMinutes(15).toMillis());
        assertThat(admin.call("GET", "/api/v1/catalog/schedules?doctorId=" + offline.id(), null).body().size()).isZero();
        assertThat(offline.browser().call("GET", "/api/v1/doctor/catalog/schedules", null).body().toString()).contains(second.body().path("id").asText());
        assertThat(admin.call("GET", "/api/v1/catalog/schedules?date=invalid-date", null).status()).isEqualTo(400);
    }

    @Test void patientDefaultWindowIsSevenDaysAndExplicitDateCanQueryBeyondIt() throws Exception {
        var admin = admin(); String department = department(admin); var doctor = doctor(admin, department, "CERTIFIED");
        var today = clock.instant().atZone(ZoneId.of("Asia/Shanghai")).toLocalDate();
        clock.now = today.atTime(8, 0).atZone(ZoneId.of("Asia/Shanghai")).toInstant();
        var inside = admin.call("POST", "/api/v1/admin/catalog/schedules", schedule(doctor.id(), department, today.plusDays(6), "AM", "ONLINE", 2));
        var outside = admin.call("POST", "/api/v1/admin/catalog/schedules", schedule(doctor.id(), department, today.plusDays(7), "AM", "ONLINE", 2));
        assertThat(inside.status()).isEqualTo(201); assertThat(outside.status()).isEqualTo(201);
        String included = inside.body().path("id").asText(), excluded = outside.body().path("id").asText();
        var defaults = admin.call("GET", "/api/v1/catalog/schedules?doctorId=" + doctor.id(), null);
        assertThat(defaults.body().toString()).contains(included).doesNotContain(excluded);
        assertThat(admin.call("GET", "/api/v1/catalog/schedules?doctorId=" + doctor.id() + "&date=" + today.plusDays(7), null).body().toString()).contains(excluded);
        assertThat(doctor.browser().call("GET", "/api/v1/doctor/catalog/schedules", null).body().toString()).contains(included, excluded);
    }
}
