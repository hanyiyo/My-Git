package cn.medical.identity;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import javax.crypto.spec.SecretKeySpec;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.*;

@Configuration
@EnableConfigurationProperties(IdentitySettings.class)
public class IdentityConfiguration {
    @Bean Clock clock() { return Clock.systemUTC(); }
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
    @Bean JwtEncoder jwtEncoder(IdentitySettings settings) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(settings.jwtSecret().getBytes(StandardCharsets.UTF_8)));
    }
    @Bean JwtDecoder jwtDecoder(IdentitySettings settings) {
        var decoder = NimbusJwtDecoder.withSecretKey(new SecretKeySpec(settings.jwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256")).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer("medical-mvp"));
        return decoder;
    }
    @Bean CaptchaAnswerSource captchaAnswerSource() {
        var random = new SecureRandom(); String alphabet = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
        return () -> {
            StringBuilder answer = new StringBuilder();
            for (int index = 0; index < 4; index++) answer.append(alphabet.charAt(random.nextInt(alphabet.length())));
            return answer.toString();
        };
    }
    @Bean ApplicationRunner bootstrapAdministrator(IdentitySettings settings, AccountMapper mapper, IdentityService identity) {
        return args -> {
            if (settings.bootstrapIdentifier() == null || settings.bootstrapIdentifier().isBlank()) return;
            String identifier = IdentityService.normalize(settings.bootstrapIdentifier());
            if (mapper.byIdentifier(identifier) == null) identity.register(identifier, settings.bootstrapPassword(), "ADMIN", true);
        };
    }
}
