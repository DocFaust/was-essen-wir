package de.wasessenwir.security;

import de.wasessenwir.user.AppUserPrincipal;
import de.wasessenwir.user.StoredUser;
import de.wasessenwir.user.UserRepository;
import de.wasessenwir.web.ApiProblems;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class ActiveUserFilter extends OncePerRequestFilter {

    private final UserRepository users;
    private final UserSessions sessions;

    public ActiveUserFilter(UserRepository users, UserSessions sessions) {
        this.users = users;
        this.sessions = sessions;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken
                || !(authentication.getPrincipal() instanceof AppUserPrincipal principal)) {
            filterChain.doFilter(request, response);
            return;
        }
        StoredUser user = users.findById(principal.id()).orElse(null);
        if (user == null || !user.active()) {
            sessions.invalidateUser(principal.id());
            SecurityContextHolder.clearContext();
            HttpSession session = request.getSession(false);
            if (session != null) {
                try {
                    session.invalidate();
                } catch (IllegalStateException ignored) {
                    // Die Sitzung wurde bereits beendet.
                }
            }
            ApiProblems.write(
                    response,
                    ApiProblems.of(
                            HttpStatus.UNAUTHORIZED,
                            "Nicht angemeldet",
                            "Die Sitzung ist nicht mehr gültig.",
                            "SESSION_INVALID"));
            return;
        }
        AppUserPrincipal fresh = AppUserPrincipal.forSession(user);
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(fresh, null, fresh.getAuthorities()));
        filterChain.doFilter(request, response);
    }
}
