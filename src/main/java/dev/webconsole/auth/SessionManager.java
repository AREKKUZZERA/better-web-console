package dev.webconsole.auth;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Manages cryptographically secure session tokens.
 * Tokens are 256-bit random values, stored in memory only.
 */
public class SessionManager {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32; // 256 bits

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final int sessionTimeoutMinutes;
    private final ScheduledExecutorService cleaner;

    public SessionManager(int sessionTimeoutMinutes) {
        this.sessionTimeoutMinutes = sessionTimeoutMinutes;
        this.cleaner = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Better-WebConsole-SessionCleaner");
            t.setDaemon(true);
            return t;
        });
        // Clean expired sessions every 5 minutes
        cleaner.scheduleAtFixedRate(this::purgeExpired, 5, 5, TimeUnit.MINUTES);
    }

    /**
     * Creates and returns a new session token for the given username.
     */
    public String createSession(String username, String remoteIp) {
        byte[] tokenBytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        sessions.put(token, new Session(username, remoteIp, System.currentTimeMillis()));
        return token;
    }

    /**
     * Validates a token and returns the session, or null if invalid/expired.
     * Also refreshes the session's last activity time.
     */
    public Session validateSession(String token) {
        if (token == null || token.isBlank()) return null;
        Session session = sessions.get(token);
        if (session == null) return null;

        long expiryMs = (long) sessionTimeoutMinutes * 60 * 1000;
        if (System.currentTimeMillis() - session.getLastActivity() > expiryMs) {
            sessions.remove(token);
            return null;
        }

        session.refreshActivity();
        return session;
    }

    /**
     * Invalidates (logs out) a specific session token.
     */
    public void invalidateSession(String token) {
        sessions.remove(token);
    }

    /**
     * Invalidates all sessions for a given username.
     */
    public void invalidateAllSessions(String username) {
        sessions.entrySet().removeIf(e -> e.getValue().getUsername().equalsIgnoreCase(username));
    }

    public int getActiveSessionCount() {
        purgeExpired();
        return sessions.size();
    }

    private void purgeExpired() {
        long now = System.currentTimeMillis();
        long expiryMs = (long) sessionTimeoutMinutes * 60 * 1000;
        sessions.entrySet().removeIf(e -> now - e.getValue().getLastActivity() > expiryMs);
    }

    public void shutdown() {
        cleaner.shutdownNow();
        sessions.clear();
    }

    public static class Session {
        private final String username;
        private final String remoteIp;
        private final long createdAt;
        private volatile long lastActivity;

        public Session(String username, String remoteIp, long createdAt) {
            this.username = username;
            this.remoteIp = remoteIp;
            this.createdAt = createdAt;
            this.lastActivity = createdAt;
        }

        public void refreshActivity() {
            this.lastActivity = System.currentTimeMillis();
        }

        public String getUsername() { return username; }
        public String getRemoteIp() { return remoteIp; }
        public long getCreatedAt() { return createdAt; }
        public long getLastActivity() { return lastActivity; }
    }
}
