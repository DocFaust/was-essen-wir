package de.wasessenwir.user;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public final class AppUserPrincipal implements UserDetails {

    private final UUID id;
    private final String username;
    private final String displayName;
    private final Role role;
    private final boolean active;
    private final boolean passwordChangeRequired;
    private final transient String passwordHash;

    private AppUserPrincipal(
            UUID id,
            String username,
            String displayName,
            Role role,
            boolean active,
            boolean passwordChangeRequired,
            String passwordHash) {
        this.id = id;
        this.username = username;
        this.displayName = displayName;
        this.role = role;
        this.active = active;
        this.passwordChangeRequired = passwordChangeRequired;
        this.passwordHash = passwordHash;
    }

    public static AppUserPrincipal forLogin(StoredUser user) {
        return new AppUserPrincipal(
                user.id(),
                user.username(),
                user.displayName(),
                user.role(),
                user.active(),
                user.passwordChangeRequired(),
                user.passwordHash());
    }

    public static AppUserPrincipal forSession(StoredUser user) {
        return new AppUserPrincipal(
                user.id(),
                user.username(),
                user.displayName(),
                user.role(),
                user.active(),
                user.passwordChangeRequired(),
                null);
    }

    public UUID id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public Role role() {
        return role;
    }

    public boolean passwordChangeRequired() {
        return passwordChangeRequired;
    }

    public AppUserPrincipal withoutPassword() {
        return new AppUserPrincipal(id, username, displayName, role, active, passwordChangeRequired, null);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash == null ? "" : passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }

    @Override
    public String toString() {
        return "AppUserPrincipal[" + id + "," + username + "," + role + "]";
    }
}
