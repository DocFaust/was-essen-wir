package de.wasessenwir.user;

import de.wasessenwir.security.CurrentUser;
import de.wasessenwir.security.RememberMeService;
import de.wasessenwir.security.UserSessions;
import de.wasessenwir.web.ApiException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final UserSessions sessions;
    private final RememberMeService rememberMe;
    private final Clock clock;

    public UserService(
            UserRepository users,
            PasswordEncoder passwordEncoder,
            UserSessions sessions,
            RememberMeService rememberMe,
            Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.sessions = sessions;
        this.rememberMe = rememberMe;
        this.clock = clock;
    }

    public List<UserResponse> list() {
        return users.findAll().stream().map(UserResponse::from).toList();
    }

    public List<MemberResponse> activeMembers() {
        CurrentUser.requireId();
        return users.findActive().stream()
                .map(user -> new MemberResponse(user.id(), user.displayName()))
                .toList();
    }

    public UserResponse current(UUID id) {
        return UserResponse.from(require(id));
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        String username = Usernames.canonicalize(request == null ? null : request.username());
        String displayName = Usernames.displayName(request.displayName());
        Passwords.check(request.password());
        Role role = parseRole(request.role());
        OffsetDateTime now = now();
        StoredUser user = new StoredUser(
                UUID.randomUUID(),
                username,
                Usernames.normalize(username),
                displayName,
                passwordEncoder.encode(request.password()),
                role,
                true,
                true,
                now,
                now);
        insertUnique(user);
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse update(UUID id, UpdateUserRequest request) {
        if (request == null || request.active() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Der Aktivierungsstatus ist erforderlich.", "VALIDATION");
        }
        String displayName = Usernames.displayName(request.displayName());
        Role role = parseRole(request.role());
        users.lockAccounts();
        StoredUser current = users.lockById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Die Person wurde nicht gefunden.", "NOT_FOUND"));
        boolean removesActiveAdmin = current.role() == Role.ADMIN
                && current.active()
                && (role != Role.ADMIN || !request.active());
        if (removesActiveAdmin && users.countActiveAdmins() <= 1) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Der letzte aktive Administrator kann nicht deaktiviert oder herabgestuft werden.",
                    "LAST_ACTIVE_ADMIN");
        }
        users.updateAccount(id, displayName, role, request.active(), now());
        if (!request.active()) {
            rememberMe.revokeUser(id);
            sessions.invalidateUser(id);
        }
        return UserResponse.from(require(id));
    }

    @Transactional
    public UserResponse resetPassword(UUID id, PasswordResetRequest request) {
        Passwords.check(request == null ? null : request.password());
        require(id);
        users.updatePassword(id, passwordEncoder.encode(request.password()), true, now());
        rememberMe.revokeUser(id);
        sessions.invalidateUser(id);
        return UserResponse.from(require(id));
    }

    @Transactional
    public UserResponse updateProfile(UUID id, ProfileUpdateRequest request) {
        Usernames.displayName(request == null ? null : request.displayName());
        require(id);
        users.updateProfile(id, Usernames.displayName(request.displayName()), now());
        return UserResponse.from(require(id));
    }

    @Transactional
    public UserResponse changePassword(UUID id, PasswordChangeRequest request, String currentSessionId) {
        if (request == null || request.currentPassword() == null || request.currentPassword().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Das bisherige Passwort ist erforderlich.", "VALIDATION");
        }
        Passwords.confirm(request.newPassword(), request.passwordConfirmation());
        StoredUser current = require(id);
        if (!passwordEncoder.matches(request.currentPassword(), current.passwordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Das bisherige Passwort ist falsch.", "VALIDATION");
        }
        users.updatePassword(id, passwordEncoder.encode(request.newPassword()), false, now());
        rememberMe.revokeUser(id);
        sessions.invalidateOthers(id, currentSessionId);
        return UserResponse.from(require(id));
    }

    public void insertInitialAdministrator(StoredUser user) {
        insertUnique(user);
    }

    private void insertUnique(StoredUser user) {
        try {
            users.insert(user);
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "Der Benutzername ist bereits vergeben.", "USERNAME_TAKEN");
        }
    }

    private StoredUser require(UUID id) {
        return users.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Die Person wurde nicht gefunden.", "NOT_FOUND"));
    }

    private static Role parseRole(String role) {
        if (role == null || role.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Die Rolle ist erforderlich.", "VALIDATION");
        }
        try {
            return Role.valueOf(role.trim());
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Die Rolle ist ungültig.", "VALIDATION");
        }
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }
}
