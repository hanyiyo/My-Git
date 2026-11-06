package cn.medical;

import java.net.ServerSocket;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {"medical.captcha-ttl=2m", "spring.data.redis.database=2"})
@Import(TestAnswers.class)
class BrowserAcceptanceTest {
    @LocalServerPort int port;
    @Test void vueIdentityFlowsUseRealHttpMySqlAndRedis() throws Exception {
        Path frontend = Path.of("..").toAbsolutePath().normalize().resolve("frontend");
        assertThat(frontend.resolve("node_modules/@playwright/test/cli.js")).exists();
        int frontendPort;
        try (var socket = new ServerSocket(0)) { frontendPort = socket.getLocalPort(); }
        var builder = new ProcessBuilder("node", "node_modules/@playwright/test/cli.js", "test", "--reporter=line").directory(frontend.toFile()).inheritIO();
        builder.environment().put("MEDICAL_API_URL", "http://127.0.0.1:" + port);
        builder.environment().put("MEDICAL_FRONTEND_TEST_PORT", Integer.toString(frontendPort));
        var process = builder.start();
        boolean finished = process.waitFor(Duration.ofMinutes(4).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
        if (!finished) { process.toHandle().descendants().forEach(ProcessHandle::destroyForcibly); process.destroyForcibly(); }
        assertThat(finished).as("Browser tests complete within four minutes").isTrue();
        assertThat(process.exitValue()).as("Real browser acceptance exit code").isZero();
    }
}
