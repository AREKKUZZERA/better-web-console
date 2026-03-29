package dev.webconsole.auth;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Manages cryptographically secure session tokens.
 * Tokens are 256-bit random values and are persisted so reconnects survive a server restart.
 */
public class SessionManager {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32; // 256 bits

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final int sessionTimeoutMinutes;
    private final ScheduledExecutorService cleaner;
    private final Path storageFile;

    public SessionManager(int sessionTimeoutMinutes, Path storageFile) {
        this.sessionTimeoutMinutes = sessionTimeoutMinutes;
        this.storageFile = storageFile;
        loadFromDisk();
        this.cleaner = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Better-WebConsole-SessionCleaner");
            t.setDaemon(true);
            return t;
        });
        cleaner.scheduleAtFixedRate(this::purgeExpired, 5, 5, TimeUnit.MINUTES);
    }

    public String createSession(String username, String remoteIp) {
        byte[] tokenBytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        sessions.put(token, new Session(username, remoteIp, System.currentTimeMillis()));
        persistQuietly();
        return token;
    }

    public Session validateSession(String token) {
        if (token == null || token.isBlank()) return null;
        Session session = sessions.get(token);
        if (session == null) return null;

        long expiryMs = (long) sessionTimeoutMinutes * 60 * 1000;
        if (System.currentTimeMillis() - session.getLastActivity() > expiryMs) {
            sessions.remove(token);
            persistQuietly();
            return null;
        }

        session.refreshActivity();
        return session;
    }

    public void invalidateSession(String token) {
        if (token == null) return;
        sessions.remove(token);
        persistQuietly();
    }

    public void invalidateAllSessions(String username) {
        if (username == null || username.isBlank()) return;
        boolean changed = sessions.entrySet().removeIf(e -> e.getValue().getUsername().equalsIgnoreCase(username));
        if (changed) persistQuietly();
    }

    public int getActiveSessionCount() {
        purgeExpired();
        return sessions.size();
    }

    private void purgeExpired() {
        long now = System.currentTimeMillis();
        long expiryMs = (long) sessionTimeoutMinutes * 60 * 1000;
        boolean changed = sessions.entrySet().removeIf(e -> now - e.getValue().getLastActivity() > expiryMs);
        if (changed) persistQuietly();
    }

    public void shutdown() {
        cleaner.shutdownNow();
        persistQuietly();
        sessions.clear();
    }

    private void loadFromDisk() {
        if (storageFile == null || !Files.exists(storageFile)) return;
        try {
            long now = System.currentTimeMillis();
            long expiryMs = (long) sessionTimeoutMinutes * 60 * 1000;
            for (String line : Files.readAllLines(storageFile, StandardCharsets.UTF_8)) {
                if (line.isBlank()) continue;
                String[] parts = line.split("\\t", 5);
                if (parts.length != 5) continue;
                String token = parts[0];
                String username = unescape(parts[1]);
                String remoteIp = unescape(parts[2]);
                long createdAt = parseLong(parts[3], 0L);
                long lastActivity = parseLong(parts[4], createdAt);
                if (token.isBlank() || username.isBlank()) continue;
                if (now - lastActivity > expiryMs) continue;
                sessions.put(token, new Session(username, remoteIp, createdAt, lastActivity));
            }
        } catch (Exception ignored) {
        }
    }

    private void persistQuietly() {
        if (storageFile == null) return;
        try {
            Files.createDirectories(storageFile.getParent());
            Path tempFile = storageFile.resolveSibling(storageFile.getFileName() + ".tmp");
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, Session> entry : sessions.entrySet()) {
                Session s = entry.getValue();
                sb.append(entry.getKey()).append('\t')
                  .append(escape(s.getUsername())).append('\t')
                  .append(escape(s.getRemoteIp())).append('\t')
                  .append(s.getCreatedAt()).append('\t')
                  .append(s.getLastActivity()).append('\n');
            }
            Files.writeString(tempFile, sb.toString(), StandardCharsets.UTF_8);
            Files.move(tempFile, storageFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ignored) {
        }
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n");
    }

    private static String unescape(String value) {
        if (value == null || value.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        boolean escaping = false;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (escaping) {
                if (ch == 't') sb.append('\t');
                else if (ch == 'n') sb.append('\n');
                else sb.append(ch);
                escaping = false;
            } else if (ch == '\\') {
                escaping = true;
            } else {
                sb.append(ch);
            }
        }
        if (escaping) sb.append('\\');
        return sb.toString();
    }

    private static long parseLong(String value, long fallback) {
        try {
            return Long.parseLong(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    public static class Session {
        private final String username;
        private final String remoteIp;
        private final long createdAt;
        private volatile long lastActivity;

        public Session(String username, String remoteIp, long createdAt) {
            this(username, remoteIp, createdAt, createdAt);
        }

        public Session(String username, String remoteIp, long createdAt, long lastActivity) {
            this.username = username;
            this.remoteIp = remoteIp == null ? "" : remoteIp;
            this.createdAt = createdAt;
            this.lastActivity = lastActivity;
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
