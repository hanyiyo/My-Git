package cn.medical.identity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdentityService {
    public record Profile(String id, String identifier, List<String> roles, boolean enabled, String credentialStatus) {}
    public record Doctor(String id, String identifier, String displayName, String title, String biography, String credentialStatus, boolean enabled) {}
    public record Tokens(String accessToken, String refreshToken, long expiresIn, Profile account) {}
    private final AccountMapper accounts;
    private final PasswordEncoder passwords;
    private final IdentitySettings settings;
    private final JwtEncoder encoder;
    private final Clock clock;
    private final String dummyPasswordHash;
    private final SecureRandom random = new SecureRandom();

    public IdentityService(AccountMapper accounts, PasswordEncoder passwords, IdentitySettings settings, JwtEncoder encoder, Clock clock) {
        this.accounts = accounts; this.passwords = passwords; this.settings = settings; this.encoder = encoder; this.clock = clock;
        this.dummyPasswordHash = passwords.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public Profile register(String identifier, String password, String role, boolean privileged) {
        String normalized = normalize(identifier);
        if (!Set.of("PATIENT", "DOCTOR", "ADMIN").contains(role) || (role.equals("ADMIN") && !privileged))
            throw new ApiFailure(403, "ROLE_NOT_ALLOWED", "管理员账户需要授权创建");
        if (password == null || password.length() < 12 || password.getBytes(StandardCharsets.UTF_8).length > 72) throw new ApiFailure(400, "INVALID_INPUT", "密码需至少12个字符，UTF-8编码不超过72字节");
        String id = UUID.randomUUID().toString();
        try { accounts.insertAccount(id, normalized, passwords.encode(password), clock.millis()); }
        catch (DuplicateKeyException error) { throw new ApiFailure(409, "ACCOUNT_EXISTS", "账户已存在"); }
        accounts.insertRole(id, role);
        if (role.equals("DOCTOR")) accounts.insertDoctor(id);
        return profile(id);
    }

    @Transactional
    public Tokens login(String identifier, String password) {
        var account = accounts.byIdentifier(normalize(identifier));
        boolean correct = passwords.matches(password, account == null ? dummyPasswordHash : account.passwordHash());
        if (account == null || !correct || !account.enabled()) throw new ApiFailure(401, "INVALID_CREDENTIALS", "账号或密码错误");
        return createSession(account.id());
    }

    @Transactional
    public Tokens refresh(String refresh) {
        if (refresh == null || refresh.isBlank()) throw new ApiFailure(401, "INVALID_REFRESH", "刷新会话已失效，请重新登录");
        var session = accounts.lockRefresh(hash(refresh));
        if (session == null || session.revoked() || session.expiresAt() <= clock.millis()) throw new ApiFailure(401, "INVALID_REFRESH", "刷新会话已失效，请重新登录");
        var account = accounts.byId(session.accountId());
        if (account == null || !account.enabled()) throw new ApiFailure(401, "INVALID_REFRESH", "刷新会话已失效，请重新登录");
        accounts.revokeSession(session.id());
        return createSession(session.accountId());
    }

    @Transactional
    public void logout(String refresh, String activeSession) {
        if (refresh != null) { var session = accounts.lockRefresh(hash(refresh)); if (session != null) accounts.revokeSession(session.id()); }
        if (activeSession != null) accounts.revokeSession(activeSession);
    }

    public Profile profile(String id) {
        var account = accounts.byId(id);
        if (account == null) throw new ApiFailure(404, "NOT_FOUND", "账户不存在");
        return new Profile(account.id(), account.identifier(), accounts.roles(id), account.enabled(), accounts.credentialStatus(id));
    }

    public List<Doctor> doctors() { return accounts.doctors(); }

    public Profile administrator(String identifier) {
        var account = accounts.byIdentifier(normalize(identifier));
        if (account == null || !account.enabled() || !accounts.roles(account.id()).contains("ADMIN"))
            throw new ApiFailure(404, "NOT_FOUND", "有效管理员账户不存在");
        return profile(account.id());
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public Doctor lockDoctor(String id) {
        var doctor = accounts.lockDoctor(id);
        if (doctor == null) throw new ApiFailure(404, "NOT_FOUND", "医生不存在");
        return doctor;
    }

    @Transactional
    public void updateDoctor(String id, String displayName, String title, String biography, String credentialStatus) {
        var current = lockDoctor(id);
        if (!Set.of("PENDING", "CERTIFIED", "REJECTED").contains(credentialStatus)) throw new ApiFailure(400, "INVALID_INPUT", "认证状态无效");
        accounts.updateDoctor(new Doctor(id, current.identifier(), displayName.trim(), title.trim(), biography.trim(), credentialStatus, current.enabled()));
    }

    public boolean active(String accountId, String sessionId) {
        var account = accounts.byId(accountId); var session = accounts.sessionById(sessionId);
        return account != null && account.enabled() && session != null && session.accountId().equals(accountId)
                && !session.revoked() && session.expiresAt() > clock.millis();
    }

    @Transactional
    public void setEnabled(String id, boolean enabled) {
        if (accounts.setEnabled(id, enabled) != 1) throw new ApiFailure(404, "NOT_FOUND", "账户不存在");
    }

    private Tokens createSession(String id) {
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        String refresh = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String sid = UUID.randomUUID().toString();
        accounts.insertSession(new AccountMapper.RefreshSession(sid, id, hash(refresh), clock.millis() + settings.refreshTtl().toMillis(), false));
        var now = clock.instant();
        var claims = JwtClaimsSet.builder().issuer("medical-mvp").subject(id).issuedAt(now).expiresAt(now.plus(settings.accessTtl())).claim("sid", sid).build();
        String access = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new Tokens(access, refresh, settings.accessTtl().toSeconds(), profile(id));
    }

    public static String normalize(String identifier) {
        String normalized = identifier == null ? "" : identifier.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9][a-z0-9@._+\\-]{2,189}")) throw new ApiFailure(400, "INVALID_INPUT", "账号需为3至190位字母、数字或邮箱格式");
        return normalized;
    }
    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception error) { throw new IllegalStateException(error); }
    }
}
