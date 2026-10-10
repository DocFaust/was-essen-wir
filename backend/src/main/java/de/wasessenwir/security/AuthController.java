package de.wasessenwir.security;

import de.wasessenwir.user.AppUserPrincipal;
import de.wasessenwir.user.PasswordChangeRequest;
import de.wasessenwir.user.ProfileUpdateRequest;
import de.wasessenwir.user.UserResponse;
import de.wasessenwir.web.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/login")
    UserResponse login(
            @RequestBody LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        return auth.login(request, httpRequest, httpResponse);
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        auth.logout(httpRequest, httpResponse);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    UserResponse me(Authentication authentication) {
        return auth.current(require(authentication));
    }

    @PutMapping("/profile")
    UserResponse profile(@RequestBody ProfileUpdateRequest request, Authentication authentication) {
        return auth.updateProfile(require(authentication), request);
    }

    @PutMapping("/password")
    UserResponse password(
            @RequestBody PasswordChangeRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        return auth.changePassword(require(authentication), request, httpRequest);
    }

    private static AppUserPrincipal require(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AppUserPrincipal principal)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Bitte melde dich an.", "UNAUTHENTICATED");
        }
        return principal;
    }
}
