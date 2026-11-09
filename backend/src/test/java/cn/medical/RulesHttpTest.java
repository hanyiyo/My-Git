package cn.medical;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestAnswers.class)
class RulesHttpTest extends HttpFixture {
    @LocalServerPort int port;
    @Test void administratorUpdatesValidatedRulesWithoutGivingPatientsWriteAccess() throws Exception {
        var admin = new Browser(port);
        assertThat(admin.login("admin-foundation-test", "fictional-test-administrator-password").status()).isEqualTo(200);
        var original = admin.call("GET", "/api/v1/admin/settings/rules", null);
        assertThat(original.status()).isEqualTo(200);
        var changed = original.body().deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) changed).put("missedWaitMinutes", 8);
        try {
            var saved = admin.call("PUT", "/api/v1/admin/settings/rules", changed);
            assertThat(saved.status()).isEqualTo(200);
            var patient = new Browser(port); var name = identifier(); patient.register(name, "PATIENT"); patient.login(name, PASSWORD);
            assertThat(patient.call("GET", "/api/v1/settings/rules", null).body().path("missedWaitMinutes").asInt()).isEqualTo(8);
            assertThat(patient.call("PUT", "/api/v1/admin/settings/rules", changed).status()).isEqualTo(403);
            ((com.fasterxml.jackson.databind.node.ObjectNode) changed).put("amEnd", "19:00");
            assertThat(admin.call("PUT", "/api/v1/admin/settings/rules", changed).status()).isEqualTo(400);
            assertThat(admin.call("GET", "/api/v1/settings/rules", null).body().path("amEnd").asText()).isEqualTo(original.body().path("amEnd").asText());
        } finally { assertThat(admin.call("PUT", "/api/v1/admin/settings/rules", original.body()).status()).isEqualTo(200); }
    }
}
