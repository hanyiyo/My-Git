package cn.medical.identity;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class IdentityController {
    private final IdentityService identity;
    private final CaptchaService captchas;
    private final IdentitySettings settings;
    public IdentityController(IdentityService identity, CaptchaService captchas, IdentitySettings settings) {
        this.identity = identity; this.captchas = captchas; this.settings = settings;
    }
    public record Registration(@NotBlank @Size(max=190) String identifier, @NotBlank @Size(min=12,max=72) String password, @NotBlank String role) {}
    public record Login(@NotBlank @Size(max=190) String identifier, @NotBlank @Size(max=72) String password,
                        @NotBlank @Size(max=36) String challengeId, @NotBlank @Size(max=8) String captcha) {}
    public record Enabled(boolean enabled) {}

    @GetMapping("/health") Map<String, String> health() { return Map.of("status", "ok"); }
    @GetMapping("/auth/csrf") Map<String, String> csrf(CsrfToken token) { return Map.of("token", token.getToken(), "headerName", token.getHeaderName()); }
    @GetMapping("/auth/captcha") CaptchaService.Challenge challenge(HttpServletRequest request, HttpServletResponse response,
            @CookieValue(name="MEDICAL_BROWSER", required=false) String browser) {
        if (browser == null || !browser.matches("[a-f0-9\\-]{36}")) browser = UUID.randomUUID().toString();
        cookie(response, "MEDICAL_BROWSER", browser, "/api/v1/auth", Duration.ofDays(30), true);
        response.setHeader("Cache-Control", "no-store");
        return captchas.issue(browser, request.getRemoteAddr());
    }
    @PostMapping("/auth/register") ResponseEntity<?> register(@Valid @RequestBody Registration registration) {
        var profile = identity.register(registration.identifier(), registration.password(), registration.role().toUpperCase(Locale.ROOT), false);
        return ResponseEntity.status(201).body(Map.of("account", profile));
    }
    @PostMapping("/auth/login") Map<String, Object> login(@Valid @RequestBody Login login, HttpServletRequest request, HttpServletResponse response,
            @CookieValue(name="MEDICAL_BROWSER", defaultValue="missing") String browser) {
        String identifier = IdentityService.normalize(login.identifier());
        captchas.consume(browser, login.challengeId(), login.captcha(), identifier, request.getRemoteAddr());
        return tokens(identity.login(identifier, login.password()), response);
    }
    @PostMapping("/auth/refresh") Map<String, Object> refresh(@CookieValue(name="MEDICAL_REFRESH", required=false) String refresh, HttpServletResponse response) {
        return tokens(identity.refresh(refresh), response);
    }
    @PostMapping("/auth/logout") ResponseEntity<Void> logout(@CookieValue(name="MEDICAL_REFRESH", required=false) String refresh, JwtAuthenticationToken authentication, HttpServletResponse response) {
        identity.logout(refresh, authentication == null ? null : authentication.getToken().getClaimAsString("sid"));
        cookie(response, "MEDICAL_REFRESH", "", "/api/v1/auth", Duration.ZERO, true);
        return ResponseEntity.noContent().build();
    }
    @GetMapping("/profile") IdentityService.Profile profile(JwtAuthenticationToken authentication) { return identity.profile(authentication.getName()); }
    @GetMapping("/accounts/{id}") IdentityService.Profile account(@PathVariable String id, JwtAuthenticationToken authentication) {
        if (!id.equals(authentication.getName()) && authentication.getAuthorities().stream().noneMatch(role -> role.getAuthority().equals("ROLE_ADMIN"))) throw new ApiFailure(403, "FORBIDDEN", "无权查看其他账户");
        return identity.profile(id);
    }
    @GetMapping({"/patient/home", "/doctor/home", "/admin/home"}) IdentityService.Profile home(JwtAuthenticationToken authentication) { return identity.profile(authentication.getName()); }
    @PostMapping("/admin/accounts") ResponseEntity<?> createAccount(@Valid @RequestBody Registration registration) {
        return ResponseEntity.status(201).body(Map.of("account", identity.register(registration.identifier(), registration.password(), registration.role().toUpperCase(Locale.ROOT), true)));
    }
    @PatchMapping("/admin/accounts/{id}/enabled") ResponseEntity<Void> enabled(@PathVariable String id, @RequestBody Enabled enabled) {
        identity.setEnabled(id, enabled.enabled()); return ResponseEntity.noContent().build();
    }
    private Map<String, Object> tokens(IdentityService.Tokens tokens, HttpServletResponse response) {
        cookie(response, "MEDICAL_REFRESH", tokens.refreshToken(), "/api/v1/auth", settings.refreshTtl(), true);
        response.setHeader("Cache-Control", "no-store");
        return Map.of("accessToken", tokens.accessToken(), "expiresIn", tokens.expiresIn(), "account", tokens.account());
    }
    private void cookie(HttpServletResponse response, String name, String value, String path, Duration age, boolean httpOnly) {
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(name, value).path(path).maxAge(age).sameSite("Strict").httpOnly(httpOnly).secure(settings.cookieSecure()).build().toString());
    }
}
