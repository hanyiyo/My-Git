package cn.medical.identity;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.imageio.ImageIO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
public class CaptchaService {
    // Both refresh and consume serialize on the browser key in Redis, including across processes.
    private static final DefaultRedisScript<Long> ISSUE = new DefaultRedisScript<>("""
        redis.call('HSET', KEYS[1], 'id', ARGV[1], 'answer', ARGV[2])
        redis.call('PEXPIRE', KEYS[1], ARGV[3])
        return 1
        """, Long.class);
    private static final DefaultRedisScript<Long> CONSUME = new DefaultRedisScript<>("""
        local current = redis.call('HGET', KEYS[1], 'id')
        if not current or current ~= ARGV[1] then return 0 end
        local answer = redis.call('HGET', KEYS[1], 'answer')
        redis.call('DEL', KEYS[1])
        if answer == ARGV[2] then return 1 else return 0 end
        """, Long.class);
    private static final DefaultRedisScript<Long> LIMIT = new DefaultRedisScript<>("""
        local n = redis.call('INCR', KEYS[1])
        if n == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end
        return n
        """, Long.class);
    private final StringRedisTemplate redis;
    private final IdentitySettings settings;
    private final CaptchaAnswerSource answers;

    public CaptchaService(StringRedisTemplate redis, IdentitySettings settings, CaptchaAnswerSource answers) {
        this.redis = redis; this.settings = settings; this.answers = answers;
    }

    public record Challenge(String challengeId, String image, long expiresInSeconds) {}

    public Challenge issue(String browser, String source) {
        rate("captcha-source:" + source, settings.captchaLimit() * 5);
        rate("captcha-browser:" + browser, settings.captchaLimit());
        String id = UUID.randomUUID().toString();
        String answer = answers.next();
        redis.execute(ISSUE, List.of("medical:captcha:" + browser), id, hmac(answer), Long.toString(settings.captchaTtl().toMillis()));
        return new Challenge(id, image(answer), settings.captchaTtl().toSeconds());
    }

    public void consume(String browser, String challengeId, String answer, String identifier, String source) {
        rate("login-source:" + source, settings.loginLimit() * 10);
        rate("login-account:" + identifier, settings.loginLimit());
        rate("login-browser:" + browser, settings.loginLimit());
        Long consumed = redis.execute(CONSUME, List.of("medical:captcha:" + browser), challengeId, hmac(answer.toUpperCase(Locale.ROOT)));
        if (!Long.valueOf(1).equals(consumed)) throw new ApiFailure(401, "INVALID_CAPTCHA", "验证码无效或已过期，请刷新后重试");
    }

    private void rate(String key, int max) {
        Long count = redis.execute(LIMIT, List.of("medical:limit:" + hmac(key)), Long.toString(settings.rateWindow().toMillis()));
        if (count == null || count > max) throw new ApiFailure(429, "RATE_LIMITED", "操作过于频繁，请稍后重试");
    }

    private String hmac(String input) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(settings.captchaSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) { throw new IllegalStateException("Captcha signing unavailable", error); }
    }

    private String image(String answer) {
        try {
            BufferedImage image = new BufferedImage(160, 52, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = image.createGraphics();
            try {
                graphics.setColor(new Color(242, 246, 250)); graphics.fillRect(0, 0, 160, 52);
                graphics.setFont(new Font(Font.MONOSPACED, Font.BOLD, 29));
                graphics.setColor(new Color(28, 58, 81)); graphics.drawString(answer, 22, 36);
            } finally { graphics.dispose(); }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(image, "png", output);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(output.toByteArray());
        } catch (Exception error) { throw new IllegalStateException("Captcha rendering unavailable", error); }
    }
}
