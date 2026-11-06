package cn.medical;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import java.util.Map;
import java.util.concurrent.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestAnswers.class)
class IdentityHttpTest extends HttpFixture {
    @LocalServerPort int port;

    @Test void unauthenticatedProfileIsRejectedWithBusinessError() throws Exception {
        var response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/profile")).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.body()).contains("UNAUTHENTICATED");
    }

    @Test void patientRegistersLogsInRefreshesAndRevokesSession() throws Exception {
        var browser = new Browser(port); String name = identifier();
        assertThat(browser.register(name, "PATIENT").status()).isEqualTo(201);
        var challenge = browser.challenge();
        assertThat(challenge.fieldNames()).toIterable().containsExactlyInAnyOrder("challengeId", "image", "expiresInSeconds");
        assertThat(challenge.path("image").asText()).startsWith("data:image/png;base64,");
        var login = browser.login(name, PASSWORD);
        assertThat(login.status()).isEqualTo(200);
        assertThat(login.body().has("refreshToken")).isFalse();
        assertThat(login.headers().allValues("set-cookie")).anySatisfy(cookie -> assertThat(cookie).contains("MEDICAL_REFRESH=", "HttpOnly", "SameSite=Strict"));
        assertThat(browser.call("GET", "/api/v1/profile", null).body().path("identifier").asText()).isEqualTo(name);
        var stolenRefresh = browser.copyCookies(port);
        var refreshed = browser.call("POST", "/api/v1/auth/refresh", Map.of());
        assertThat(refreshed.status()).isEqualTo(200);
        assertThat(stolenRefresh.call("POST", "/api/v1/auth/refresh", Map.of()).status()).isEqualTo(401);
        browser.access = refreshed.body().path("accessToken").asText();
        assertThat(browser.call("POST", "/api/v1/auth/logout", Map.of()).status()).isEqualTo(204);
        assertThat(browser.call("GET", "/api/v1/profile", null).status()).isEqualTo(401);
        browser.access = null;
        assertThat(browser.call("POST", "/api/v1/auth/refresh", Map.of()).status()).isEqualTo(401);
    }

    @Test void wrongAnswerAndWrongPasswordBothConsumeChallenge() throws Exception {
        var browser = new Browser(port); String name = identifier(); browser.register(name, "PATIENT");
        String id = browser.challenge().path("challengeId").asText();
        assertThat(browser.login(name, PASSWORD, id, "WRNG").status()).isEqualTo(401);
        assertThat(browser.login(name, PASSWORD, id, "AB7K").status()).isEqualTo(401);
        id = browser.challenge().path("challengeId").asText();
        assertThat(browser.login(name, "incorrect-password", id, "AB7K").body().path("error").path("code").asText()).isEqualTo("INVALID_CREDENTIALS");
        assertThat(browser.login(name, PASSWORD, id, "AB7K").status()).isEqualTo(401);
    }

    @Test void missingExpiredRefreshedAndReusedChallengesAreRejected() throws Exception {
        var browser = new Browser(port); String name = identifier(); browser.register(name, "PATIENT");
        assertThat(browser.call("POST", "/api/v1/auth/login", Map.of("identifier", name, "password", PASSWORD)).status()).isEqualTo(400);
        String old = browser.challenge().path("challengeId").asText();
        String current = browser.challenge().path("challengeId").asText();
        assertThat(browser.login(name, PASSWORD, old, "AB7K").status()).isEqualTo(401);
        assertThat(browser.login(name, PASSWORD, current, "AB7K").status()).isEqualTo(200);
        assertThat(browser.login(name, PASSWORD, current, "AB7K").status()).isEqualTo(401);
        String expiring = browser.challenge().path("challengeId").asText();
        Thread.sleep(2200);
        assertThat(browser.login(name, PASSWORD, expiring, "AB7K").status()).isEqualTo(401);
    }

    @Test void otherBrowserCannotConsumeOwnersChallenge() throws Exception {
        var owner = new Browser(port); String name = identifier(); owner.register(name, "PATIENT");
        String id = owner.challenge().path("challengeId").asText(); var other = new Browser(port); other.challenge();
        assertThat(other.login(name, PASSWORD, id, "AB7K").status()).isEqualTo(401);
        assertThat(owner.login(name, PASSWORD, id, "AB7K").status()).isEqualTo(200);
    }

    @Test void concurrentChallengeConsumptionSignsInOnlyOnce() throws Exception {
        var browser = new Browser(port); String name = identifier(); browser.register(name, "PATIENT");
        String id = browser.challenge().path("challengeId").asText();
        browser.call("GET", "/api/v1/auth/csrf", null);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var ready = new CountDownLatch(1);
            var tasks = java.util.stream.IntStream.range(0, 6).mapToObj(index -> executor.submit(() -> { ready.await(); return browser.login(name, PASSWORD, id, "AB7K").status(); })).toList();
            ready.countDown(); int successes = 0;
            for (var task : tasks) { int status = task.get(); assertThat(status).isIn(200, 401); if (status == 200) successes++; }
            assertThat(successes).isEqualTo(1);
        }
    }

    @Test void csrfAndRoleEscalationAndCrossAccountAccessAreRejected() throws Exception {
        var untrusted = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/auth/register")).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString("{}")).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(untrusted.statusCode()).isEqualTo(403);
        var patient = new Browser(port); String name = identifier();
        assertThat(patient.register(name, "ADMIN").status()).isEqualTo(403);
        patient.register(name, "PATIENT"); patient.login(name, PASSWORD);
        assertThat(patient.call("GET", "/api/v1/admin/home", null).status()).isEqualTo(403);
        assertThat(patient.call("GET", "/api/v1/doctor/home", null).status()).isEqualTo(403);
        assertThat(patient.call("GET", "/api/v1/accounts/another-patient", null).status()).isEqualTo(403);
        var doctor = new Browser(port); String doctorName = identifier(); doctor.register(doctorName, "DOCTOR"); doctor.login(doctorName, PASSWORD);
        assertThat(doctor.call("GET", "/api/v1/doctor/home", null).body().path("credentialStatus").asText()).isEqualTo("PENDING");
        assertThat(doctor.call("GET", "/api/v1/patient/home", null).status()).isEqualTo(403);
        assertThat(doctor.call("GET", "/api/v1/admin/home", null).status()).isEqualTo(403);
    }

    @Test void administratorCanProvisionAndDisableAccountsWithoutTrustingStaleJwtRoles() throws Exception {
        var admin = new Browser(port); assertThat(admin.login("admin-foundation-test", "fictional-test-administrator-password").status()).isEqualTo(200);
        String name = identifier(); var created = admin.call("POST", "/api/v1/admin/accounts", Map.of("identifier", name, "password", PASSWORD, "role", "ADMIN"));
        assertThat(created.status()).isEqualTo(201);
        var account = new Browser(port); account.login(name, PASSWORD);
        assertThat(account.call("GET", "/api/v1/admin/home", null).status()).isEqualTo(200);
        assertThat(admin.call("PATCH", "/api/v1/admin/accounts/" + created.body().path("account").path("id").asText() + "/enabled", Map.of("enabled", false)).status()).isEqualTo(204);
        assertThat(account.call("GET", "/api/v1/profile", null).status()).isEqualTo(401);
        account.access = null;
        assertThat(account.call("POST", "/api/v1/auth/refresh", Map.of()).status()).isEqualTo(401);
    }

    @Test void repeatedLoginAttemptsAreRateLimitedByAccount() throws Exception {
        var browser = new Browser(port); String name = identifier(); browser.register(name, "PATIENT");
        for (int attempt = 0; attempt < 15; attempt++) assertThat(browser.login(name, "wrong-password").status()).isEqualTo(401);
        assertThat(browser.login(name, PASSWORD).status()).isEqualTo(429);
    }
}
