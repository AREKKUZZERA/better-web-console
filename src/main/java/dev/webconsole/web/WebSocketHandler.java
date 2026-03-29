package dev.webconsole.web;

import dev.webconsole.BetterWebConsolePlugin;
import dev.webconsole.auth.RateLimiter;
import dev.webconsole.auth.SessionManager;
import dev.webconsole.util.IpWhitelistChecker;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of all active WebSocket connections.
 * Used by ConsoleLogHandler to broadcast log lines.
 */
public class WebSocketHandler {

    private final Set<ConsoleWebSocket> sockets = ConcurrentHashMap.newKeySet();

    // Constructor kept for possible future use
    public WebSocketHandler(BetterWebConsolePlugin plugin, SessionManager sessionManager,
                            RateLimiter rateLimiter, IpWhitelistChecker ipChecker) {}

    public void register(ConsoleWebSocket socket) {
        sockets.add(socket);
    }

    public void unregister(ConsoleWebSocket socket) {
        sockets.remove(socket);
    }

    /**
     * Broadcasts a log line to all connected clients.
     * Called from the logging thread — must be thread-safe.
     */
    public void broadcast(String line) {
        for (ConsoleWebSocket socket : sockets) {
            try {
                socket.sendLine(line);
            } catch (Exception ignored) {}
        }
    }

    public int getConnectionCount() {
        return sockets.size();
    }
}
