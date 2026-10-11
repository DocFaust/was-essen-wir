package de.wasessenwir.security;

import de.wasessenwir.setup.TokenHasher;
import de.wasessenwir.user.AppUserPrincipal;
import de.wasessenwir.user.StoredUser;
import de.wasessenwir.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persistente Anmeldung pro Browser. Der Cookie enthält Selektor und Geheimnis.
 * In PostgreSQL liegt nur der SHA-256 des Geheimnisses. Jede erfolgreiche Verwendung
 * rotiert das Geheimnis. Eine parallele Anfrage darf das gerade ersetzte Geheimnis
 * noch {@link #REUSE_GRACE} lang benutzen. Danach gilt dieselbe Serie als kompromittiert
 * und alle dauerhaften Anmeldungen der Person werden widerrufen. Eine Anmeldung setzt
 * eine bestehende Serie nur fort, wenn das vorgelegte Geheimnis zum gespeicherten Hash
 * passt und die Zeile derselben Person gehört. Sonst entsteht eine neue Serie.
 */
@Service
public class RememberMeService {

    static final int MAX_TOKENS_PER_USER = 8;
    static final Duration REUSE_GRACE = Duration.ofSeconds(30);

    private static final Logger log = LoggerFactory.getLogger(RememberMeService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RememberMeRepository tokens;
    private final UserRepository users;
    private final RememberMeProperties properties;
    private final Clock clock;
    private final boolean secureCookie;

    public RememberMeService(
            RememberMeRepository tokens,
            UserRepository users,
            RememberMeProperties properties,
            Clock clock,
            @Value("${server.servlet.session.cookie.secure:false}") boolean secureCookie) {
        this.tokens = tokens;
        this.users = users;
        this.properties = properties;
        this.clock = clock;
        this.secureCookie = secureCookie;
    }

    @Transactional
    public void issue(UUID userId, HttpServletRequest request, HttpServletResponse response) {
        String presented = RememberMeCookies.read(request);
        if (!properties.enabled()) {
            forget(presented, response);
            return;
        }
        OffsetDateTime now = now();
        tokens.deleteExpired(now);
        users.lockById(userId);
        String secret = TokenHasher.generate();
        byte[] hash = TokenHasher.sha256(secret);
        OffsetDateTime expiresAt = now.plus(properties.duration());
        RememberMeCookies.Parsed parsed = RememberMeCookies.parse(presented);
        String selector = null;
        if (parsed != null) {
            Optional<RememberMeToken> existing = tokens.lock(parsed.selector());
            if (existing.isPresent() && ownsSeries(existing.get(), userId, parsed.secret())) {
                selector = parsed.selector();
                tokens.replace(selector, userId, hash, expiresAt, now);
            }
        }
        if (selector == null) {
            selector = insertFresh(userId, hash, expiresAt, now);
        }
        tokens.trimTo(userId, MAX_TOKENS_PER_USER);
        RememberMeCookies.write(response, selector + "." + secret, properties.duration(), secureCookie);
    }

    @Transactional
    public void forget(HttpServletRequest request, HttpServletResponse response) {
        forget(RememberMeCookies.read(request), response);
    }

    @Transactional
    public void revokeUser(UUID userId) {
        tokens.deleteByUser(userId);
    }

    /**
     * @return die Person, für die eine neue Sitzung erzeugt werden soll
     */
    @Transactional
    public AppUserPrincipal restore(HttpServletRequest request, HttpServletResponse response) {
        String presented = RememberMeCookies.read(request);
        if (presented == null) {
            return null;
        }
        if (!properties.enabled()) {
            forget(presented, response);
            return null;
        }
        RememberMeCookies.Parsed parsed = RememberMeCookies.parse(presented);
        if (parsed == null) {
            RememberMeCookies.clear(response, secureCookie);
            return null;
        }
        OffsetDateTime now = now();
        tokens.deleteExpired(now);
        Optional<RememberMeToken> locked = tokens.lock(parsed.selector());
        if (locked.isEmpty()) {
            RememberMeCookies.clear(response, secureCookie);
            return null;
        }
        RememberMeToken token = locked.get();
        if (!token.expiresAt().isAfter(now)) {
            tokens.deleteBySelector(token.selector());
            RememberMeCookies.clear(response, secureCookie);
            return null;
        }
        StoredUser user = users.findById(token.userId()).orElse(null);
        if (user == null || !user.active()) {
            tokens.deleteByUser(token.userId());
            RememberMeCookies.clear(response, secureCookie);
            return null;
        }
        if (TokenHasher.matches(token.tokenHash(), parsed.secret())) {
            String secret = TokenHasher.generate();
            tokens.rotate(token.selector(), TokenHasher.sha256(secret), now);
            Duration remaining = Duration.between(now, token.expiresAt());
            if (remaining.compareTo(Duration.ofSeconds(1)) < 0) {
                remaining = Duration.ofSeconds(1);
            }
            RememberMeCookies.write(response, token.selector() + "." + secret, remaining, secureCookie);
            return AppUserPrincipal.forSession(user);
        }
        if (token.previousTokenHash() != null && TokenHasher.matches(token.previousTokenHash(), parsed.secret())) {
            if (withinGrace(token.rotatedAt(), now)) {
                return AppUserPrincipal.forSession(user);
            }
            tokens.deleteByUser(token.userId());
            RememberMeCookies.clear(response, secureCookie);
            log.warn(
                    "Ersetztes Remember-me-Token wurde erneut verwendet. Alle dauerhaften Anmeldungen von {} wurden widerrufen.",
                    token.userId());
            return null;
        }
        tokens.deleteBySelector(token.selector());
        RememberMeCookies.clear(response, secureCookie);
        return null;
    }

    private void forget(String presented, HttpServletResponse response) {
        if (presented == null || presented.isBlank()) {
            return;
        }
        RememberMeCookies.Parsed parsed = RememberMeCookies.parse(presented);
        if (parsed != null) {
            tokens.deleteBySelector(parsed.selector());
        }
        RememberMeCookies.clear(response, secureCookie);
    }

    private String insertFresh(UUID userId, byte[] hash, OffsetDateTime expiresAt, OffsetDateTime now) {
        String selector = newSelector();
        try {
            tokens.insert(selector, userId, hash, expiresAt, now);
            return selector;
        } catch (DataIntegrityViolationException first) {
            String retry = newSelector();
            tokens.insert(retry, userId, hash, expiresAt, now);
            return retry;
        }
    }

    private static boolean ownsSeries(RememberMeToken token, UUID userId, String secret) {
        return userId.equals(token.userId()) && TokenHasher.matches(token.tokenHash(), secret);
    }

    private static boolean withinGrace(OffsetDateTime rotatedAt, OffsetDateTime now) {
        return rotatedAt != null && !rotatedAt.plus(REUSE_GRACE).isBefore(now);
    }

    private static String newSelector() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }
}
