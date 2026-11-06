package cn.medical;

import java.time.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"medical.captcha-ttl=2m", "spring.data.redis.database=5"})
@Import(TestAnswers.class)
class BookingHttpTest extends HttpFixture {
    @LocalServerPort int port;

    @Test void patientCreatesPendingPaymentAppointmentAndClaimsPublishedSlot() throws Exception {
        var admin = new Browser(port);
        assertThat(admin.login("admin-foundation-test", "fictional-test-administrator-password").status()).isEqualTo(200);
        var department = admin.call("POST", "/api/v1/admin/catalog/departments",
            Map.of("name", identifier(), "description", "虚构门诊", "active", true)).body().path("id").asText();
        var doctor = new Browser(port);
        var doctorId = doctor.register(identifier(), "DOCTOR").body().path("account").path("id").asText();
        assertThat(admin.call("PUT", "/api/v1/admin/catalog/doctors/" + doctorId,
            Map.of("displayName", "虚构李医生", "title", "主治医师", "biography", "演示", "departmentId", department, "credentialStatus", "CERTIFIED")).status()).isEqualTo(200);
        var date = LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(2);
        var schedule = admin.call("POST", "/api/v1/admin/catalog/schedules", Map.of("doctorId", doctorId,
            "departmentId", department, "clinicDate", date.toString(), "session", "AM", "serviceMode", "ONLINE", "capacity", 2, "feeMinor", 2000));
        assertThat(schedule.status()).isEqualTo(201);
        var scheduleId = schedule.body().path("id").asText();
        var patient = new Browser(port); var name = identifier();
        patient.register(name, "PATIENT"); assertThat(patient.login(name, PASSWORD).status()).isEqualTo(200);
        long before = System.currentTimeMillis();
        var created = patient.call("POST", "/api/v1/patient/appointments",
            Map.of("scheduleId", scheduleId, "source", "ADVANCE", "idempotencyKey", identifier()));
        assertThat(created.status()).isEqualTo(201);
        var order = created.body();
        assertThat(order.path("id").asText()).isNotBlank();
        assertThat(order.path("state").asText()).isEqualTo("PENDING_PAYMENT");
        assertThat(order.path("scheduleId").asText()).isEqualTo(scheduleId);
        assertThat(order.path("serviceMode").asText()).isEqualTo("ONLINE");
        assertThat(order.path("clinicDate").asText()).isEqualTo(date.toString());
        assertThat(order.path("session").asText()).isEqualTo("AM");
        assertThat(order.path("feeMinor").asLong()).isEqualTo(2000);
        assertThat(order.path("holdExpiresAt").asLong()).isBetween(before + 600_000, System.currentTimeMillis() + 600_000);
        var queried = patient.call("GET", "/api/v1/patient/appointments/" + order.path("id").asText(), null);
        assertThat(queried.status()).isEqualTo(200);
        assertThat(queried.body()).isEqualTo(order);
        var remaining = patient.call("GET", "/api/v1/catalog/schedules?doctorId=" + doctorId, null);
        assertThat(remaining.body().get(0).path("availableSlots").asInt()).isEqualTo(1);
    }
}
