package dev.webconsole.web;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.webconsole.BetterWebConsolePlugin;
import dev.webconsole.auth.RateLimiter;
import dev.webconsole.auth.SessionManager;
import org.bukkit.Bukkit;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WriteCallback;
import org.eclipse.jetty.websocket.api.annotations.*;

import java.util.List;

/**
 * Handles a single authenticated WebSocket connection.
 * Receives commands from the client and sends log lines back.
 */
@WebSocket
public class ConsoleWebSocket {

    private final BetterWebConsolePlugin plugin;
    private final SessionManager sessionManager;
    private final RateLimiter rateLimiter;
    private final WebSocketHandler wsHandler;
    private final String sessionToken;
    private final SessionManager.Session authSession;

    private Session wsSession;

    public ConsoleWebSocket(BetterWebConsolePlugin plugin, SessionManager sessionManager,
                            RateLimiter rateLimiter, WebSocketHandler wsHandler,
                            String sessionToken, SessionManager.Session authSession) {
        this.plugin = plugin;
        this.sessionManager = sessionManager;
        this.rateLimiter = rateLimiter;
        this.wsHandler = wsHandler;
        this.sessionToken = sessionToken;
        this.authSession = authSession;
    }

    @OnWebSocketConnect
    public void onConnect(Session session) {
        this.wsSession = session;
        wsHandler.register(this);

        // Send buffered log lines to new connection
        List<String> buffer = plugin.getConsoleLogHandler().getBufferedLines();
        for (String line : buffer) {
            sendLine(line);
        }

        plugin.getLogger().info("[Better-WebConsole] WebSocket connected: user='"
                + authSession.getUsername() + "' ip=" + authSession.getRemoteIp());
    }

    @OnWebSocketClose
    public void onClose(int statusCode, String reason) {
        wsHandler.unregister(this);
        rateLimiter.removeSession(sessionToken);
        plugin.getLogger().info("[Better-WebConsole] WebSocket disconnected: user='"
                + authSession.getUsername() + "'");
    }

    @OnWebSocketError
    public void onError(Throwable cause) {
        wsHandler.unregister(this);
    }

    @OnWebSocketMessage
    public void onMessage(String rawMessage) {
        // Re-validate session on every message
        if (sessionManager.validateSession(sessionToken) == null) {
            sendControl("SESSION_EXPIRED");
            try { wsSession.close(1008, "Session expired"); } catch (Exception ignored) {}
            return;
        }

        // Rate limit commands
        if (!rateLimiter.allowCommand(sessionToken)) {
            sendControl("RATE_LIMITED");
            return;
        }

        // Parse command message
        String command;
        try {
            JsonObject json = JsonParser.parseString(rawMessage).getAsJsonObject();
            if (!"command".equals(json.get("type").getAsString())) return;
            command = json.get("command").getAsString().trim();
        } catch (Exception e) {
            return;
        }

        if (command.isEmpty() || command.length() > 1024) return;

        // Block obviously dangerous patterns
        if (isBlockedCommand(command)) {
            sendControl("BLOCKED");
            return;
        }

        String loggedCommand = command;
        String username = authSession.getUsername();

        if (plugin.getPluginConfig().isLogCommands()) {
            plugin.getLogger().info("[Better-WebConsole] Command by '" + username + "': " + command);
        }

        // Dispatch on main thread
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), loggedCommand);
            } catch (Exception e) {
                plugin.getLogger().warning("[Better-WebConsole] Command error: " + e.getMessage());
            }
        });
    }

    /**
     * Sends a log line to this WebSocket client.
     */
    public void sendLine(String line) {
        if (wsSession == null || !wsSession.isOpen()) return;

        JsonObject msg = new JsonObject();
        msg.addProperty("type", "log");
        msg.addProperty("line", line);

        wsSession.getRemote().sendString(msg.toString(), WriteCallback.NOOP);
    }

    private void sendControl(String event) {
        if (wsSession == null || !wsSession.isOpen()) return;
        JsonObject msg = new JsonObject();
        msg.addProperty("type", "control");
        msg.addProperty("event", event);
        wsSession.getRemote().sendString(msg.toString(), WriteCallback.NOOP);
    }

    /**
     * Simple blocklist of commands that should not be executed via web console.
     * You can extend this list as needed.
     */
    private boolean isBlockedCommand(String cmd) {
        String lower = cmd.toLowerCase().trim();
        // Prevent stopping/restarting the server from web (should be done intentionally)
        // Remove these if you want to allow them
        return lower.equals("stop") || lower.equals("restart") ||
               lower.startsWith("stop ") || lower.startsWith("restart ");
    }
}
