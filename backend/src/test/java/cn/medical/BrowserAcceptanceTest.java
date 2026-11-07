package cn.medical;

import java.net.ServerSocket;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {"medical.captcha-ttl=2m", "spring.data.redis.database=2", "medical.reception-scan-delay=3600000", "medical.refund-scanner-enabled=false"})
@Import({TestAnswers.class, BookingHttpTest.TimeControl.class, BrowserClockControl.class})
class BrowserAcceptanceTest {
    @LocalServerPort int port;
    @Test void vueIdentityFlowsUseRealHttpMySqlAndRedis() throws Exception {
        Path frontend = Path.of("..").toAbsolutePath().normalize().resolve("frontend");
        assertThat(frontend.resolve("node_modules/@playwright/test/cli.js")).exists();
        int frontendPort;
        try (var socket = new ServerSocket(0)) { frontendPort = socket.getLocalPort(); }
        // Preserve child output outside Surefire's native stream so CI retains complete assertion diagnostics.
        Path report = frontend.resolve("playwright-results.json");
        var builder = new ProcessBuilder("node", "node_modules/@playwright/test/cli.js", "test", "--reporter=json")
            .directory(frontend.toFile()).redirectOutput(report.toFile()).redirectError(ProcessBuilder.Redirect.INHERIT);
        builder.environment().put("MEDICAL_API_URL", "http://127.0.0.1:" + port);
        builder.environment().put("MEDICAL_FRONTEND_TEST_PORT", Integer.toString(frontendPort));
        var process = builder.start();
        boolean finished = process.waitFor(Duration.ofMinutes(4).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
        if (!finished) { process.toHandle().descendants().forEach(ProcessHandle::destroyForcibly); process.destroyForcibly(); }
        assertThat(finished).as("Browser tests complete within four minutes").isTrue();
        if (process.exitValue() != 0 && java.nio.file.Files.exists(report)) {
            var results = new com.fasterxml.jackson.databind.ObjectMapper().readTree(report.toFile());
            for (var specs : results.findValues("specs")) for (var spec : specs) {
                if (spec.path("ok").asBoolean()) continue;
                System.err.println("[browser-failure] " + spec.path("title").asText());
                for (var test : spec.path("tests")) for (var result : test.path("results"))
                    for (var error : result.path("errors")) System.err.println(error.path("message").asText());
            }
        }
        assertThat(process.exitValue()).as("Real browser acceptance exit code").isZero();
    }
}
