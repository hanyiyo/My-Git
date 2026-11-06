package cn.medical.identity;

import org.springframework.context.annotation.*;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.*;

@Configuration
public class SecurityConfiguration {
    @Bean SecurityFilterChain security(HttpSecurity http, IdentityService identity, IdentitySettings settings) throws Exception {
        var csrf = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrf.setCookieCustomizer(cookie -> cookie.secure(settings.cookieSecure()).sameSite("Strict").path("/"));
        // The SPA submits the raw cookie token in a header, not the masked form token.
        var handler = new CsrfTokenRequestAttributeHandler();
        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .csrf(config -> config.csrfTokenRepository(csrf).csrfTokenRequestHandler(handler))
            .requestCache(cache -> cache.disable())
            .headers(headers -> headers.cacheControl(cache -> {}))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/auth/**", "/api/v1/health").permitAll()
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/v1/doctor/**").hasRole("DOCTOR")
                .requestMatchers("/api/v1/patient/**").hasRole("PATIENT")
                .requestMatchers("/api/**").authenticated()
                .anyRequest().denyAll())
            .oauth2ResourceServer(resource -> resource.jwt(jwt -> jwt.jwtAuthenticationConverter(token -> {
                String sessionId = token.getClaimAsString("sid");
                if (sessionId == null || !identity.active(token.getSubject(), sessionId)) throw new BadCredentialsException("Session revoked");
                var authorities = identity.profile(token.getSubject()).roles().stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toList();
                return new JwtAuthenticationToken(token, authorities, token.getSubject());
            })).authenticationEntryPoint((req, res, error) -> ApiErrors.write(res, 401, "UNAUTHENTICATED", "请先登录")))
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((req, res, error) -> ApiErrors.write(res, 401, "UNAUTHENTICATED", "请先登录"))
                .accessDeniedHandler((req, res, error) -> ApiErrors.write(res, 403, error instanceof CsrfException ? "CSRF_REQUIRED" : "FORBIDDEN", "无权执行该操作")));
        return http.build();
    }
}
