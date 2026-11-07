package cn.medical.governance;

import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class AuditSignerTest {
    @TempDir Path directory;
    @Test void asymmetricProofAndSeparateMonotonicCheckpointSurviveRestart() {
        var signer = new AuditSigner(directory.toString()); byte[] body = "审计原文".getBytes(StandardCharsets.UTF_8);
        String signature = signer.sign(body); assertThat(signer.verify(body,signature)).isTrue();
        assertThat(signer.verify("篡改".getBytes(StandardCharsets.UTF_8),signature)).isFalse();
        signer.checkpoint(2,signature); signer.checkpoint(1,"older");
        var restarted = new AuditSigner(directory.toString());
        assertThat(restarted.publicKey()).isEqualTo(signer.publicKey()); assertThat(restarted.verify(body,signature)).isTrue();
        assertThat(restarted.checkpoint()).isEqualTo(new AuditSigner.Checkpoint(2,signature));
    }
}
