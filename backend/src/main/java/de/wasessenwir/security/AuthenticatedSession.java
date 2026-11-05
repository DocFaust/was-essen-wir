package de.wasessenwir.security;

import de.wasessenwir.user.AppUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.stereotype.Component;

@Component
public class AuthenticatedSession {

    private final SecurityContextRepository securityContextRepository;
    private final CsrfTokenRepository csrfTokenRepository;
    private final UserSessions sessions;

    public AuthenticatedSession(
            SecurityContextRepository securityContextRepository,
            CsrfTokenRepository csrfTokenRepository,
            UserSessions sessions) {
        this.securityContextRepository = securityContextRepository;
        this.csrfTokenRepository = csrfTokenRepository;
        this.sessions = sessions;
    }

    public void open(AppUserPrincipal principal, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        AppUserPrincipal sessionPrincipal = principal.withoutPassword();
        var sessionAuthentication =
                new UsernamePasswordAuthenticationToken(sessionPrincipal, null, sessionPrincipal.getAuthorities());
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
        sessions.adoptNewId(sessionPrincipal.id(), previousId, httpRequest.getSession(false));
        rotateCsrf(httpRequest, httpResponse);
    }

    public void rotateCsrf(HttpServletRequest request, HttpServletResponse response) {
        CsrfToken token = csrfTokenRepository.generateToken(request);
        csrfTokenRepository.saveToken(token, request, response);
        request.setAttribute(CsrfToken.class.getName(), token);
    }
}
