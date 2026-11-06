package cn.medical;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

class IdentityRecoveryTest extends HttpFixture {
    private ServletWebServerApplicationContext start(String... args) {
        return (ServletWebServerApplicationContext) new SpringApplicationBuilder(MedicalApplication.class, TestAnswers.class)
            .run(java.util.stream.Stream.concat(java.util.stream.Stream.of("--server.port=0", "--spring.main.banner-mode=off"), java.util.Arrays.stream(args)).toArray(String[]::new));
    }

    @Test void accountAndRefreshSurviveApplicationRestartAndRedisOutageDoesNotBreakExistingSession() throws Exception {
        String name = identifier(); Browser original;
        try (var first = start()) {
            original = new Browser(first.getWebServer().getPort());
            assertThat(original.register(name, "PATIENT").status()).isEqualTo(201);
            assertThat(original.login(name, PASSWORD).status()).isEqualTo(200);
        }
        try (var restarted = start("--spring.data.redis.port=1")) {
            var afterRestart = original.copyCookies(restarted.getWebServer().getPort());
            afterRestart.access = original.access;
            assertThat(afterRestart.call("GET", "/api/v1/profile", null).body().path("identifier").asText()).isEqualTo(name);
            var refresh = afterRestart.call("POST", "/api/v1/auth/refresh", Map.of());
            assertThat(refresh.status()).isEqualTo(200);
            afterRestart.access = refresh.body().path("accessToken").asText();
            assertThat(afterRestart.call("GET", "/api/v1/auth/captcha", null).status()).isEqualTo(503);
            assertThat(afterRestart.call("POST", "/api/v1/auth/login", Map.of("identifier", name, "password", PASSWORD, "challengeId", "missing", "captcha", "AB7K")).status()).isEqualTo(503);
            assertThat(afterRestart.call("GET", "/api/v1/profile", null).status()).isEqualTo(200);
        }
    }

    @Test void challengeGenerationIsLimited() throws Exception {
        try (var context = start("--medical.captcha-limit=2", "--spring.data.redis.database=1")) {
            var browser = new Browser(context.getWebServer().getPort());
            assertThat(browser.call("GET", "/api/v1/auth/captcha", null).status()).isEqualTo(200);
            assertThat(browser.call("GET", "/api/v1/auth/captcha", null).status()).isEqualTo(200);
            assertThat(browser.call("GET", "/api/v1/auth/captcha", null).status()).isEqualTo(429);
        }
    }

    @Test void lostRedisStateInvalidatesChallengeAndNewChallengeAllowsLogin() throws Exception {
        try (var context = start("--spring.data.redis.database=3")) {
            var browser = new Browser(context.getWebServer().getPort()); String name = identifier();
            browser.register(name, "PATIENT"); String old = browser.challenge().path("challengeId").asText();
            // Simulate the documented Redis restart/state-loss boundary, only in the isolated test DB.
            var redis = context.getBean(org.springframework.data.redis.connection.RedisConnectionFactory.class);
            try (var connection = redis.getConnection()) { connection.serverCommands().flushDb(); }
            assertThat(browser.login(name, PASSWORD, old, "AB7K").status()).isEqualTo(401);
            assertThat(browser.login(name, PASSWORD).status()).isEqualTo(200);
        }
    }

    @Test void hospitalDoctorAndPublishedSlotsSurviveApplicationRestart() throws Exception {
        Browser original; String doctorId; String scheduleId;
        try (var first = start("--spring.data.redis.database=5", "--medical.captcha-ttl=2m")) {
            original = new Browser(first.getWebServer().getPort());
            assertThat(original.login("admin-foundation-test", "fictional-test-administrator-password").status()).isEqualTo(200);
            assertThat(original.call("PUT", "/api/v1/admin/catalog/hospital", Map.of("name", "虚构重启测试医院", "address", "虚构重启路", "description", "重启持久化验证")).status()).isEqualTo(200);
            String department = original.call("POST", "/api/v1/admin/catalog/departments", Map.of("name", identifier(), "description", "重启验证", "active", true)).body().path("id").asText();
            doctorId = original.call("POST", "/api/v1/admin/accounts", Map.of("identifier", identifier(), "password", PASSWORD, "role", "DOCTOR")).body().path("account").path("id").asText();
            assertThat(original.call("PUT", "/api/v1/admin/catalog/doctors/" + doctorId, Map.of("displayName", "虚构恢复医生", "title", "主治医师", "biography", "", "departmentId", department, "credentialStatus", "CERTIFIED")).status()).isEqualTo(200);
            var published = original.call("POST", "/api/v1/admin/catalog/schedules", Map.of("doctorId", doctorId, "departmentId", department, "clinicDate", java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).plusDays(3).toString(), "session", "PM", "serviceMode", "OFFLINE", "capacity", 2, "feeMinor", 750));
            assertThat(published.status()).isEqualTo(201); scheduleId = published.body().path("id").asText();
        }
        try (var restarted = start("--spring.data.redis.database=5", "--medical.captcha-ttl=2m")) {
            var restored = original.copyCookies(restarted.getWebServer().getPort()); restored.access = original.access;
            assertThat(restored.call("GET", "/api/v1/catalog/hospital", null).body().path("address").asText()).isEqualTo("虚构重启路");
            var schedules = restored.call("GET", "/api/v1/catalog/schedules?doctorId=" + doctorId, null).body();
            assertThat(schedules.size()).isEqualTo(1);
            assertThat(schedules.get(0).path("id").asText()).isEqualTo(scheduleId);
            assertThat(schedules.get(0).path("availableSlots").asInt()).isEqualTo(2);
            assertThat(schedules.get(0).path("feeMinor").asInt()).isEqualTo(750);
            assertThat(restored.call("GET", "/api/v1/catalog/doctors", null).body().toString()).contains(doctorId, "虚构恢复医生");
        }
    }
}
