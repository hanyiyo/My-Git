package cn.medical.identity;

import java.time.Duration;
import java.nio.charset.StandardCharsets;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("medical")
public record IdentitySettings(String jwtSecret, String captchaSecret, boolean cookieSecure,
        Duration captchaTtl, Duration accessTtl, Duration refreshTtl, Duration rateWindow,
        int captchaLimit, int loginLimit, String bootstrapIdentifier, String bootstrapPassword) {
    public IdentitySettings {
        if (jwtSecret == null || jwtSecret.getBytes(StandardCharsets.UTF_8).length < 32
                || captchaSecret == null || captchaSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("Configure separate JWT and captcha secrets of at least 32 bytes.");
        }
    }
}
