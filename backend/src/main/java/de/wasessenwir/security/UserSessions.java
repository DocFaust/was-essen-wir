package de.wasessenwir.security;

import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class UserSessions implements HttpSessionListener {

    private final ConcurrentHashMap<String, UUID> userBySession = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, ConcurrentHashMap<String, HttpSession>> sessionsByUser =
            new ConcurrentHashMap<>();

    public void bind(UUID userId, HttpSession session) {
        unbind(session.getId());
        userBySession.put(session.getId(), userId);
        sessionsByUser.computeIfAbsent(userId, ignored -> new ConcurrentHashMap<>()).put(session.getId(), session);
    }

    public void adoptNewId(UUID userId, String previousId, HttpSession session) {
        if (previousId != null) {
            unbind(previousId);
        }
        bind(userId, session);
    }

    public void invalidateUser(UUID userId) {
        Map<String, HttpSession> sessions = sessionsByUser.remove(userId);
        if (sessions == null) {
            return;
        }
        for (Map.Entry<String, HttpSession> entry : new ArrayList<>(sessions.entrySet())) {
            userBySession.remove(entry.getKey(), userId);
            invalidateQuietly(entry.getValue());
        }
    }

    public void invalidateOthers(UUID userId, String currentSessionId) {
        Map<String, HttpSession> sessions = sessionsByUser.get(userId);
        if (sessions == null) {
            return;
        }
        for (Map.Entry<String, HttpSession> entry : new ArrayList<>(sessions.entrySet())) {
            if (entry.getKey().equals(currentSessionId)) {
                continue;
            }
            sessions.remove(entry.getKey());
            userBySession.remove(entry.getKey(), userId);
            invalidateQuietly(entry.getValue());
        }
    }

    @Override
    public void sessionDestroyed(HttpSessionEvent event) {
        unbind(event.getSession().getId());
    }

    private void unbind(String sessionId) {
        UUID userId = userBySession.remove(sessionId);
        if (userId == null) {
            return;
        }
        Map<String, HttpSession> sessions = sessionsByUser.get(userId);
        if (sessions == null) {
            return;
        }
        sessions.remove(sessionId);
        if (sessions.isEmpty()) {
            sessionsByUser.remove(userId, sessions);
        }
    }

    public void unbind(HttpSession session) {
        unbind(session.getId());
    }

    private static void invalidateQuietly(HttpSession session) {
        try {
            session.invalidate();
        } catch (IllegalStateException ignored) {
            // Die Sitzung wurde bereits beendet.
        }
    }
}
