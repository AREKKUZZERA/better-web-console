package dev.webconsole.auth;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks failed login attempts and enforces IP-based lockouts.
 * Also provides per-session command rate limiting.
 */
public class RateLimiter {

    private final int maxAttempts;
    private final long lockoutMs;
    private final int commandsPerMinute;

    // IP -> FailRecord
    private final Map<String, FailRecord> loginFailures = new ConcurrentHashMap<>();
    // sessionToken -> CommandRecord
    private final Map<String, CommandRecord> commandRates = new ConcurrentHashMap<>();

    public RateLimiter(int maxAttempts, int lockoutDurationMinutes, int commandsPerMinute) {
        this.maxAttempts = maxAttempts;
        this.lockoutMs = (long) lockoutDurationMinutes * 60 * 1000;
        this.commandsPerMinute = commandsPerMinute;
    }

    // ─── Login rate limiting ────────────────────────────────────────────────

    public boolean isIpLocked(String ip) {
        FailRecord record = loginFailures.get(ip);
        if (record == null) return false;
        if (record.lockedUntil.get() > System.currentTimeMillis()) return true;
        // Lock expired
        loginFailures.remove(ip);
        return false;
    }

    public long getLockoutRemainingSeconds(String ip) {
        FailRecord record = loginFailures.get(ip);
        if (record == null) return 0;
        long remaining = record.lockedUntil.get() - System.currentTimeMillis();
        return remaining > 0 ? remaining / 1000 : 0;
    }

    public void recordFailedLogin(String ip) {
        FailRecord record = loginFailures.computeIfAbsent(ip, k -> new FailRecord());
        int attempts = record.count.incrementAndGet();
        if (attempts >= maxAttempts) {
            record.lockedUntil.set(System.currentTimeMillis() + lockoutMs);
            record.count.set(0); // reset counter for next window
        }
    }

    public void resetLoginFailures(String ip) {
        loginFailures.remove(ip);
    }

    // ─── Command rate limiting ──────────────────────────────────────────────

    public boolean allowCommand(String sessionToken) {
        CommandRecord record = commandRates.computeIfAbsent(sessionToken, k -> new CommandRecord());
        long now = System.currentTimeMillis();

        // Reset window every minute
        if (now - record.windowStart.get() > 60_000) {
            record.windowStart.set(now);
            record.count.set(0);
        }

        return record.count.incrementAndGet() <= commandsPerMinute;
    }

    public void removeSession(String sessionToken) {
        commandRates.remove(sessionToken);
    }

    // ─── Inner records ──────────────────────────────────────────────────────

    private static class FailRecord {
        final AtomicInteger count = new AtomicInteger(0);
        final AtomicLong lockedUntil = new AtomicLong(0);
    }

    private static class CommandRecord {
        final AtomicInteger count = new AtomicInteger(0);
        final AtomicLong windowStart = new AtomicLong(System.currentTimeMillis());
    }
}
