package de.wasessenwir.security;

import de.wasessenwir.user.AppUserPrincipal;
import de.wasessenwir.web.ApiProblems;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class PasswordChangeFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken
                || !(authentication.getPrincipal() instanceof AppUserPrincipal principal)
                || !principal.passwordChangeRequired()
                || allowed(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        ApiProblems.write(
                response,
                ApiProblems.of(
                        HttpStatus.FORBIDDEN,
                        "Passwortänderung erforderlich",
                        "Bitte ändere zuerst das temporäre Passwort.",
                        "PASSWORD_CHANGE_REQUIRED"));
    }

    private static boolean allowed(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();
        if (HttpMethod.GET.matches(method) && ("/api/auth/me".equals(path) || "/api/setup/status".equals(path) || "/api/status".equals(path))) {
            return true;
        }
        if (HttpMethod.PUT.matches(method) && "/api/auth/password".equals(path)) {
            return true;
        }
        if (HttpMethod.POST.matches(method) && "/api/auth/logout".equals(path)) {
            return true;
        }
        return HttpMethod.GET.matches(method) && (path.equals("/actuator/health") || path.startsWith("/actuator/health/"));
    }
}
