package dev.webconsole.web;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.webconsole.BetterWebConsolePlugin;
import dev.webconsole.auth.RateLimiter;
import dev.webconsole.auth.SessionManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandMap;
import org.bukkit.entity.Player;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WriteCallback;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketError;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;

import java.util.List;

@WebSocket
public class ConsoleWebSocket {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final String PLAYER_NAME_PATTERN = "[A-Za-z0-9_]{3,16}";
    private static final int MAX_ALIAS_CHAIN = 10;

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
        for (String line : plugin.getConsoleLogHandler().getBufferedLines()) sendLine(line);
        pushStats();
        plugin.getLogger().info("[BWC] WS connected: " + authSession.getUsername() + "@" + authSession.getRemoteIp());
    }

    @OnWebSocketClose
    public void onClose(int code, String reason) {
        wsHandler.unregister(this);
        rateLimiter.removeSession(sessionToken);
        plugin.getLogger().info("[BWC] WS disconnected: " + authSession.getUsername());
    }

    @OnWebSocketError
    public void onError(Throwable cause) {
        wsHandler.unregister(this);
    }

    @OnWebSocketMessage
    public void onMessage(String raw) {
        if (sessionManager.validateSession(sessionToken) == null) {
            sendControl("SESSION_EXPIRED");
            try { wsSession.close(1008, "Session expired"); } catch (Exception ignored) {}
            return;
        }

        JsonObject json;
        try { json = JsonParser.parseString(raw).getAsJsonObject(); }
        catch (Exception e) { return; }

        String type = json.has("type") ? json.get("type").getAsString() : "";
        switch (type) {
            case "command" -> handleCommand(json);
            case "complete" -> handleTabComplete(json);
            case "kick" -> handleKick(json);
            case "ban" -> handleBan(json);
            case "ping_stats" -> pushStats();
            default -> { }
        }
    }

    private void handleCommand(JsonObject json) {
        if (!rateLimiter.allowCommand(sessionToken)) { sendControl("RATE_LIMITED"); return; }

        String cmd = json.has("command") ? json.get("command").getAsString().trim() : "";
        if (cmd.isEmpty() || cmd.length() > 1024) return;

        if (cmd.startsWith("!")) {
            String aliasKey = cmd.substring(1).toLowerCase();
            String expanded = plugin.getPluginConfig().getAliases().get(aliasKey);
            if (expanded == null) { sendLine("[BWC] Unknown alias: " + aliasKey); return; }

            String[] parts = expanded.split("&&", MAX_ALIAS_CHAIN + 1);
            if (parts.length > MAX_ALIAS_CHAIN) {
                sendLine("[BWC] Alias has too many chained commands: " + aliasKey);
                return;
            }
            for (String part : parts) executeCommand(part.trim());

            if (plugin.getPluginConfig().isAuditLog()) {
                plugin.getAuditLog().logCommand(authSession.getUsername(), authSession.getRemoteIp(),
                        "!" + aliasKey + " -> " + expanded);
            }
            return;
        }

        executeCommand(cmd);
        if (plugin.getPluginConfig().isAuditLog()) {
            plugin.getAuditLog().logCommand(authSession.getUsername(), authSession.getRemoteIp(), cmd);
        }
    }

    private void executeCommand(String cmd) {
        if (cmd.isEmpty()) return;
        String toRun = cmd.startsWith("/") ? cmd.substring(1) : cmd;

        if (plugin.getPluginConfig().isCommandBlocked(toRun)) {
            sendControl("BLOCKED");
            sendLine("[BWC] Command blocked by config: " + firstWord(toRun));
            return;
        }

        if (plugin.getPluginConfig().isLogCommands()) {
            plugin.getLogger().info("[BWC] Command by '" + authSession.getUsername() + "': " + toRun);
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                var sender = Bukkit.createCommandSender(component -> {
                    String text = PLAIN.serialize(component);
                    if (!text.isBlank()) sendLine("[>] " + stripColor(text));
                });
                boolean ok = Bukkit.dispatchCommand(sender, toRun);
                if (!ok) sendLine("[BWC] Unknown command: " + toRun);
            } catch (Exception e) {
                sendLine("[BWC] Error: " + e.getMessage());
                plugin.getLogger().warning("[BWC] Command error: " + e.getMessage());
            }
        });
    }

    private void handleTabComplete(JsonObject json) {
        String partial = json.has("partial") ? json.get("partial").getAsString() : "";
        int reqId = json.has("reqId") ? json.get("reqId").getAsInt() : 0;

        Bukkit.getScheduler().runTask(plugin, () -> {
            List<String> suggestions;
            try {
                CommandMap commandMap = Bukkit.getCommandMap();
                String toComplete = partial.startsWith("/") ? partial.substring(1) : partial;
                suggestions = commandMap.tabComplete(Bukkit.getConsoleSender(), toComplete);
                if (suggestions == null) suggestions = List.of();
            } catch (Exception e) {
                suggestions = List.of();
            }

            JsonObject resp = new JsonObject();
            resp.addProperty("type", "completions");
            resp.addProperty("reqId", reqId);
            JsonArray arr = new JsonArray();
            suggestions.forEach(arr::add);
            resp.add("suggestions", arr);
            send(resp.toString());
        });
    }

    private void handleKick(JsonObject json) {
        if (!rateLimiter.allowCommand(sessionToken)) { sendControl("RATE_LIMITED"); return; }
        String target = json.has("player") ? json.get("player").getAsString().trim() : "";
        String reason = cleanReason(json.has("reason") ? json.get("reason").getAsString() : "Kicked by admin");
        if (!isValidPlayerName(target)) { sendLine("[BWC] Invalid player name: " + target); return; }

        Bukkit.getScheduler().runTask(plugin, () -> {
            Player p = Bukkit.getPlayerExact(target);
            if (p == null) { sendLine("[BWC] Player not found: " + target); return; }
            p.kick(Component.text(reason));
            sendLine("[BWC] Kicked " + target + ": " + reason);
            if (plugin.getPluginConfig().isAuditLog()) {
                plugin.getAuditLog().logKick(authSession.getUsername(), authSession.getRemoteIp(), target, reason);
            }
        });
    }

    private void handleBan(JsonObject json) {
        if (!rateLimiter.allowCommand(sessionToken)) { sendControl("RATE_LIMITED"); return; }
        String target = json.has("player") ? json.get("player").getAsString().trim() : "";
        String reason = cleanReason(json.has("reason") ? json.get("reason").getAsString() : "Banned by admin");
        if (!isValidPlayerName(target)) { sendLine("[BWC] Invalid player name: " + target); return; }

        Bukkit.getScheduler().runTask(plugin, () -> {
            executeCommand("ban " + target + " " + reason);
            sendLine("[BWC] Banned " + target + ": " + reason);
            if (plugin.getPluginConfig().isAuditLog()) {
                plugin.getAuditLog().logBan(authSession.getUsername(), authSession.getRemoteIp(), target, reason);
            }
        });
    }

    public void pushStats() {
        Bukkit.getScheduler().runTask(plugin, () -> {
            JsonObject stats = plugin.getServerStats().toJson();
            stats.addProperty("type", "stats");
            send(stats.toString());
        });
    }

    public void sendLine(String line) {
        JsonObject msg = new JsonObject();
        msg.addProperty("type", "log");
        msg.addProperty("line", line);
        send(msg.toString());
    }

    private void sendControl(String event) {
        JsonObject msg = new JsonObject();
        msg.addProperty("type", "control");
        msg.addProperty("event", event);
        send(msg.toString());
    }

    private void send(String text) {
        if (wsSession == null || !wsSession.isOpen()) return;
        wsSession.getRemote().sendString(text, WriteCallback.NOOP);
    }

    private String stripColor(String s) {
        return s.replaceAll("\\u00A7[0-9A-FK-ORa-fk-or]", "");
    }

    private boolean isValidPlayerName(String playerName) {
        return playerName != null && playerName.matches(PLAYER_NAME_PATTERN);
    }

    private String cleanReason(String reason) {
        String cleaned = reason == null ? "" : reason.replaceAll("[\\p{Cntrl}&&[^\t]]", " ").trim();
        if (cleaned.isBlank()) return "No reason given";
        return cleaned.length() > 120 ? cleaned.substring(0, 120) : cleaned;
    }

    private String firstWord(String command) {
        int space = command.indexOf(' ');
        return space >= 0 ? command.substring(0, space) : command;
    }
}
