package cn.medical;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {"medical.captcha-ttl=2m", "spring.data.redis.database=5"})
@Import(TestAnswers.class)
class PharmacyHttpTest extends HttpFixture {
    @LocalServerPort int port;

    Browser admin() throws Exception {
        var browser = new Browser(port);
        assertThat(browser.login("admin-foundation-test", "fictional-test-administrator-password").status()).isEqualTo(200);
        return browser;
    }

    @Test void adminMaintainsMedicineProfileInventoryAndHistoryBoundary() throws Exception {
        var admin = admin();
        var created = admin.call("POST", "/api/v1/admin/pharmacy/medicines", Map.of("name", "演示维生素", "spec", "10片 / 盒", "feeMinor", 1250, "stock", 10, "threshold", 3, "active", true));
        assertThat(created.status()).isEqualTo(201);
        String id = created.body().path("id").asText();
        var listed = admin.call("GET", "/api/v1/admin/pharmacy/medicines", null);
        var item = java.util.stream.StreamSupport.stream(listed.body().spliterator(), false).filter(value -> value.path("id").asText().equals(id)).findFirst().orElseThrow();
        assertThat(item.path("availableStock").asInt()).isEqualTo(10); assertThat(item.path("heldStock").asInt()).isZero(); assertThat(item.path("totalStock").asInt()).isEqualTo(10); assertThat(item.path("hasPrescriptionHistory").asBoolean()).isFalse();
        var adjusted = admin.call("PATCH", "/api/v1/admin/pharmacy/medicines/" + id + "/stock", Map.of("totalStock", 6));
        assertThat(adjusted.status()).isEqualTo(200); assertThat(adjusted.body().path("availableStock").asInt()).isEqualTo(6); assertThat(adjusted.body().path("totalStock").asInt()).isEqualTo(6);
        var updated = admin.call("PUT", "/api/v1/admin/pharmacy/medicines/" + id, Map.of("name", "演示维生素升级", "spec", "20片 / 盒", "feeMinor", 1500, "threshold", 2, "active", false));
        assertThat(updated.status()).isEqualTo(200); assertThat(updated.body().path("name").asText()).isEqualTo("演示维生素升级"); assertThat(updated.body().path("stock").asInt()).isEqualTo(6);
        assertThat(admin.call("GET", "/api/v1/admin/pharmacy/medicines/" + id + "/history", null).body()).isEmpty();
        assertThat(admin.call("DELETE", "/api/v1/admin/pharmacy/medicines/" + id, null).status()).isEqualTo(200);
        var patient = new Browser(port); String patientName = identifier(); patient.register(patientName, "PATIENT"); assertThat(patient.login(patientName, PASSWORD).status()).isEqualTo(200);
        assertThat(patient.call("GET", "/api/v1/admin/pharmacy/medicines", null).status()).isEqualTo(403);
    }
}
