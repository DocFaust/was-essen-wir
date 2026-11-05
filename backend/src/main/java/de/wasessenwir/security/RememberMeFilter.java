package de.wasessenwir.security;

import de.wasessenwir.user.AppUserPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class RememberMeFilter extends OncePerRequestFilter {

    private final RememberMeService rememberMe;
    private final AuthenticatedSession sessions;

    public RememberMeFilter(RememberMeService rememberMe, AuthenticatedSession sessions) {
        this.rememberMe = rememberMe;
        this.sessions = sessions;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (authenticated() || login(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        AppUserPrincipal principal = rememberMe.restore(request, response);
        if (principal != null) {
            sessions.open(principal, request, response);
        }
        filterChain.doFilter(request, response);
    }

    private static boolean authenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)
                && authentication.getPrincipal() instanceof AppUserPrincipal;
    }

    private static boolean login(HttpServletRequest request) {
        return HttpMethod.POST.matches(request.getMethod()) && "/api/auth/login".equals(request.getRequestURI());
    }
}
