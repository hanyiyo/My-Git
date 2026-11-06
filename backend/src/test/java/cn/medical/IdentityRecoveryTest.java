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
}
