package de.wasessenwir.setup;

import de.wasessenwir.security.AttemptLimiter;
import de.wasessenwir.security.SecurityProperties;
import de.wasessenwir.user.Passwords;
import de.wasessenwir.user.Role;
import de.wasessenwir.user.StoredUser;
import de.wasessenwir.user.UserRepository;
import de.wasessenwir.user.UserResponse;
import de.wasessenwir.user.Usernames;
import de.wasessenwir.web.ApiException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class SetupService {

    private final SetupRepository setups;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate transactions;
    private final AttemptLimiter limiter;
    private final SecurityProperties securityProperties;
    private final Clock clock;

    public SetupService(
            SetupRepository setups,
            UserRepository users,
            PasswordEncoder passwordEncoder,
            TransactionTemplate transactions,
            AttemptLimiter limiter,
            SecurityProperties securityProperties,
            Clock clock) {
        this.setups = setups;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.transactions = transactions;
        this.limiter = limiter;
        this.securityProperties = securityProperties;
        this.clock = clock;
    }

    public boolean isRequired() {
        return setups.find().map(state -> !state.completed()).orElse(true);
    }

    public UserResponse complete(SetupRequest request, String remoteAddress) {
        String key = "setup:" + (remoteAddress == null || remoteAddress.isBlank() ? "unknown" : remoteAddress);
        if (!limiter.tryAcquire(key, securityProperties.setupMaxAttempts(), securityProperties.setupWindow())) {
            throw new ApiException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Zu viele Einrichtungsversuche. Bitte später erneut versuchen.",
                    "SETUP_RATE_LIMITED");
        }
        if (request == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Die Eingabe ist ungültig.", "VALIDATION");
        }
        String username = Usernames.canonicalize(request.username());
        String displayName = Usernames.displayName(request.displayName());
        Passwords.confirm(request.password(), request.passwordConfirmation());
        String token = request.setupToken();
        Outcome outcome = transactions.execute(status -> createAdministrator(username, displayName, request.password(), token));
        if (outcome == null || outcome.finished()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Die Einrichtung ist bereits abgeschlossen.",
                    "SETUP_ALREADY_COMPLETED");
        }
        return outcome.user();
    }

    private Outcome createAdministrator(String username, String displayName, String password, String token) {
        setups.lockInstallation();
        SetupState state = setups.lock();
        if (state.completed()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Die Einrichtung ist bereits abgeschlossen.",
                    "SETUP_ALREADY_COMPLETED");
        }
        boolean tokenValid = TokenHasher.matches(state.tokenHash(), token);
        boolean expired = state.expiresAt() == null || !state.expiresAt().isAfter(OffsetDateTime.now(clock));
        if (!tokenValid || expired) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    "Das Setup-Token ist ungültig oder abgelaufen.",
                    "SETUP_TOKEN_INVALID");
        }
        if (users.count() > 0) {
            setups.markCompleted(OffsetDateTime.now(clock));
            return Outcome.done();
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        StoredUser admin = new StoredUser(
                UUID.randomUUID(),
                username,
                Usernames.normalize(username),
                displayName,
                passwordEncoder.encode(password),
                Role.ADMIN,
                true,
                false,
                now,
                now);
        users.insert(admin);
        if (!setups.markCompleted(now)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Die Einrichtung ist bereits abgeschlossen.",
                    "SETUP_ALREADY_COMPLETED");
        }
        return Outcome.created(UserResponse.from(admin));
    }

    private record Outcome(UserResponse user, boolean finished) {
        private static Outcome created(UserResponse user) {
            return new Outcome(user, false);
        }

        private static Outcome done() {
            return new Outcome(null, true);
        }
    }
}
