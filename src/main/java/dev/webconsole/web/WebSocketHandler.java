package dev.webconsole.web;

import com.google.gson.JsonObject;
import dev.webconsole.BetterWebConsolePlugin;
import dev.webconsole.auth.RateLimiter;
import dev.webconsole.auth.SessionManager;
import dev.webconsole.util.IpWhitelistChecker;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class WebSocketHandler {

    private final Set<ConsoleWebSocket> sockets = ConcurrentHashMap.newKeySet();

    public WebSocketHandler(BetterWebConsolePlugin plugin, SessionManager sessionManager,
                            RateLimiter rateLimiter, IpWhitelistChecker ipChecker) {}

    public void register(ConsoleWebSocket socket)   { sockets.add(socket); }
    public void unregister(ConsoleWebSocket socket) { sockets.remove(socket); }

    /** Broadcast a raw log line to all connected clients. */
    public void broadcast(String line) {
        JsonObject msg = new JsonObject();
        msg.addProperty("type", "log");
        msg.addProperty("line", line);
        String payload = msg.toString();

        for (ConsoleWebSocket s : sockets) {
            try { s.sendPayload(payload); } catch (Exception ignored) {}
        }
    }

    /** Push updated stats to all connected clients. */
    public void broadcastStats(JsonObject stats) {
        stats.addProperty("type", "stats");
        String payload = stats.toString();

        for (ConsoleWebSocket s : sockets) {
            try { s.sendPayload(payload); } catch (Exception ignored) {}
        }
    }

    public int getConnectionCount() { return sockets.size(); }
}
