package de.wasessenwir.security;

import de.wasessenwir.user.AppUserPrincipal;
import de.wasessenwir.web.ApiException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class CurrentUser {

    private CurrentUser() {}

    public static UUID requireId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken
                || !(authentication.getPrincipal() instanceof AppUserPrincipal principal)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Bitte melde dich an.", "UNAUTHENTICATED");
        }
        return principal.id();
    }
}
