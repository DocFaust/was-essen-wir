package de.wasessenwir.security;

import de.wasessenwir.user.AppUserPrincipal;
import de.wasessenwir.user.PasswordChangeRequest;
import de.wasessenwir.user.ProfileUpdateRequest;
import de.wasessenwir.user.UserResponse;
import de.wasessenwir.user.UserService;
import de.wasessenwir.user.Usernames;
import de.wasessenwir.web.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final CsrfTokenRepository csrfTokenRepository;
    private final UserService users;
    private final UserSessions sessions;
    private final AttemptLimiter limiter;
    private final SecurityProperties properties;

    public AuthService(
            AuthenticationManager authenticationManager,
            SecurityContextRepository securityContextRepository,
            CsrfTokenRepository csrfTokenRepository,
            UserService users,
            UserSessions sessions,
            AttemptLimiter limiter,
            SecurityProperties properties) {
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.csrfTokenRepository = csrfTokenRepository;
        this.users = users;
        this.sessions = sessions;
        this.limiter = limiter;
        this.properties = properties;
    }

    public UserResponse login(LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        String username = request == null || request.username() == null ? "" : request.username();
        String password = request == null ? null : request.password();
        String key = "login:" + Usernames.normalize(username) + "|" + remoteAddress(httpRequest);
        if (!limiter.tryAcquire(key, properties.loginMaxAttempts(), properties.loginWindow())) {
            throw new ApiException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Zu viele Anmeldeversuche. Bitte später erneut versuchen.",
                    "LOGIN_RATE_LIMITED");
        }
        if (username.isBlank() || password == null || password.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Benutzername oder Passwort ist falsch.", "LOGIN_FAILED");
        }
        try {
            Authentication authenticated = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(username.trim(), password));
            AppUserPrincipal loaded = (AppUserPrincipal) authenticated.getPrincipal();
            AppUserPrincipal principal = loaded.withoutPassword();
            var sessionAuthentication = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(sessionAuthentication);
            SecurityContextHolder.setContext(context);

            HttpSession existing = httpRequest.getSession(false);
            String previousId = existing == null ? null : existing.getId();
            HttpSession session = httpRequest.getSession(true);
            if (previousId == null) {
                previousId = session.getId();
            }
            httpRequest.changeSessionId();
            securityContextRepository.saveContext(context, httpRequest, httpResponse);
            sessions.adoptNewId(principal.id(), previousId, httpRequest.getSession(false));
            rotateCsrf(httpRequest, httpResponse);
            limiter.reset(key);
            return users.current(principal.id());
        } catch (AuthenticationException exception) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Benutzername oder Passwort ist falsch.", "LOGIN_FAILED");
        }
    }

    public void logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        HttpSession session = httpRequest.getSession(false);
        if (session != null) {
            sessions.unbind(session);
            try {
                session.invalidate();
            } catch (IllegalStateException ignored) {
                // Die Sitzung wurde bereits beendet.
            }
        }
        SecurityContextHolder.clearContext();
        rotateCsrf(httpRequest, httpResponse);
    }

    public UserResponse current(AppUserPrincipal principal) {
        return users.current(principal.id());
    }

    public UserResponse updateProfile(AppUserPrincipal principal, ProfileUpdateRequest request) {
        return users.updateProfile(principal.id(), request);
    }

    public UserResponse changePassword(
            AppUserPrincipal principal, PasswordChangeRequest request, HttpServletRequest httpRequest) {
        String key = "password:" + principal.id();
        if (!limiter.tryAcquire(key, properties.loginMaxAttempts(), properties.loginWindow())) {
            throw new ApiException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Zu viele Versuche. Bitte später erneut versuchen.",
                    "PASSWORD_RATE_LIMITED");
        }
        HttpSession session = httpRequest.getSession(false);
        String sessionId = session == null ? null : session.getId();
        try {
            UserResponse updated = users.changePassword(principal.id(), request, sessionId);
            limiter.reset(key);
            return updated;
        } catch (ApiException exception) {
            if (!"VALIDATION".equals(exception.code())) {
                limiter.reset(key);
            }
            throw exception;
        }
    }

    private void rotateCsrf(HttpServletRequest request, HttpServletResponse response) {
        CsrfToken token = csrfTokenRepository.generateToken(request);
        csrfTokenRepository.saveToken(token, request, response);
        request.setAttribute(CsrfToken.class.getName(), token);
    }

    private static String remoteAddress(HttpServletRequest request) {
        String address = request.getRemoteAddr();
        return address == null || address.isBlank() ? "unknown" : address;
    }
}
