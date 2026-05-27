package dev.webconsole.web;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.webconsole.BetterWebConsolePlugin;
import dev.webconsole.auth.RateLimiter;
import dev.webconsole.auth.SessionManager;
import dev.webconsole.util.CsrfUtil;
import dev.webconsole.util.IpWhitelistChecker;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import io.papermc.paper.plugin.configuration.PluginMeta;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * REST API endpoints:
 *   GET  /api/csrf        - CSRF token
 *   GET  /api/status      - session check
 *   GET  /api/stats       - server stats JSON (auth required)
 *   GET  /api/aliases     - command alias list (auth required)
 *   GET  /api/sessions    - active web sessions (auth required)
 *   GET  /api/audit       - recent audit log entries (auth required)
 *   GET  /api/logs/export - download console log as text file (auth required)
 *   POST /api/login       - authenticate
 *   POST /api/logout      - invalidate session
 */
public class ApiServlet extends HttpServlet {

    private static final Gson GSON = new Gson();
    private static final int MAX_BODY_CHARS = 16 * 1024;
    private static final Pattern USERNAME = Pattern.compile("[a-zA-Z0-9_-]{3,32}");
    private static final Pattern ALIAS_NAME = Pattern.compile("[a-z0-9_-]{1,32}");

    private final BetterWebConsolePlugin plugin;
    private final SessionManager sessionManager;
    private final RateLimiter rateLimiter;
    private final CsrfUtil csrfUtil;
    private final IpWhitelistChecker ipChecker;

    public ApiServlet(BetterWebConsolePlugin plugin, SessionManager sessionManager,
                      RateLimiter rateLimiter, CsrfUtil csrfUtil,
                      IpWhitelistChecker ipChecker) {
        this.plugin         = plugin;
        this.sessionManager = sessionManager;
        this.rateLimiter    = rateLimiter;
        this.csrfUtil       = csrfUtil;
        this.ipChecker      = ipChecker;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
        if (!ipChecker.isAllowed(req.getRemoteAddr())) { sendError(res, 403, "IP not allowed"); return; }
        String path = req.getPathInfo() == null ? "/" : req.getPathInfo();
        switch (path) {
            case "/csrf"        -> handleCsrf(res);
            case "/status"      -> handleStatus(req, res);
            case "/stats"       -> handleStats(req, res);
            case "/aliases"     -> handleAliases(req, res);
            case "/sessions"    -> handleSessions(req, res);
            case "/audit"       -> handleAudit(req, res);
            case "/audit/export" -> handleAuditExport(req, res);
            case "/compat"      -> handleCompatibility(req, res);
            case "/config"      -> handleConfig(req, res);
            case "/errors"      -> handleErrors(req, res);
            case "/player"      -> handlePlayer(req, res);
            case "/logs/export" -> handleLogExport(req, res);
            default             -> sendError(res, 404, "Not found");
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {
        if (!ipChecker.isAllowed(req.getRemoteAddr())) { sendError(res, 403, "IP not allowed"); return; }
        if (!SameOriginGuard.isAllowed(req.getHeader("Origin"), req.getHeader("Referer"), req.getHeader("Host"))) {
            sendError(res, 403, "Cross-origin request blocked");
            return;
        }
        String path = req.getPathInfo() == null ? "/" : req.getPathInfo();
        switch (path) {
            case "/login"  -> handleLogin(req, res);
            case "/logout" -> handleLogout(req, res);
            case "/config" -> handleConfigSave(req, res);
            default        -> sendError(res, 404, "Not found");
        }
    }

    // ── Handlers ──────────────────────────────────────────────────────────────

    private void handleCsrf(HttpServletResponse res) throws IOException {
        res.setContentType("application/json;charset=UTF-8");
        JsonObject obj = new JsonObject();
        obj.addProperty("token", csrfUtil.generateToken());
        writeJson(res, 200, obj);
    }

    private void handleStatus(HttpServletRequest req, HttpServletResponse res) throws IOException {
        res.setContentType("application/json;charset=UTF-8");
        SessionManager.Session s = sessionManager.validateSession(getSessionCookie(req));
        JsonObject obj = new JsonObject();
        obj.addProperty("authenticated", s != null);
        if (s != null) obj.addProperty("username", s.getUsername());
        writeJson(res, 200, obj);
    }

    private void handleStats(HttpServletRequest req, HttpServletResponse res) throws IOException {
        res.setContentType("application/json;charset=UTF-8");
        if (!isAuthenticated(req)) { sendError(res, 401, "Unauthorized"); return; }
        writeJson(res, 200, plugin.getServerStats().toJson());
    }

    private void handleAliases(HttpServletRequest req, HttpServletResponse res) throws IOException {
        res.setContentType("application/json;charset=UTF-8");
        if (!isAuthenticated(req)) { sendError(res, 401, "Unauthorized"); return; }
        JsonArray arr = new JsonArray();
        for (Map.Entry<String, String> e : plugin.getPluginConfig().getAliases().entrySet()) {
            JsonObject obj = new JsonObject();
            obj.addProperty("name", e.getKey());
            obj.addProperty("command", e.getValue());
            arr.add(obj);
        }
        JsonObject resp = new JsonObject();
        resp.add("aliases", arr);
        writeJson(res, 200, resp);
    }

    private void handleSessions(HttpServletRequest req, HttpServletResponse res) throws IOException {
        res.setContentType("application/json;charset=UTF-8");
        if (!isAuthenticated(req)) { sendError(res, 401, "Unauthorized"); return; }
        JsonObject resp = new JsonObject();
        resp.add("sessions", sessionManager.sessionsJson());
        writeJson(res, 200, resp);
    }

    private void handleAudit(HttpServletRequest req, HttpServletResponse res) throws IOException {
        res.setContentType("application/json;charset=UTF-8");
        if (!isAuthenticated(req)) { sendError(res, 401, "Unauthorized"); return; }
        int limit = parseLimit(req.getParameter("limit"), 200);
        JsonObject resp = new JsonObject();
        resp.add("entries", plugin.getAuditLog().searchJson(limit, req.getParameter("q"), req.getParameter("action"),
                req.getParameter("username"), req.getParameter("ip")));
        writeJson(res, 200, resp);
    }

    private void handleAuditExport(HttpServletRequest req, HttpServletResponse res) throws IOException {
        if (!isAuthenticated(req)) { sendError(res, 401, "Unauthorized"); return; }
        int limit = parseLimit(req.getParameter("limit"), 500);
        String format = req.getParameter("format") == null ? "json" : req.getParameter("format").toLowerCase(Locale.ROOT);
        List<JsonObject> entries = plugin.getAuditLog().searchEntries(limit, req.getParameter("q"), req.getParameter("action"),
                req.getParameter("username"), req.getParameter("ip"));

        if ("csv".equals(format)) {
            res.setContentType("text/csv;charset=UTF-8");
            res.setHeader("Content-Disposition", "attachment; filename=\"audit-export.csv\"");
            PrintWriter w = res.getWriter();
            w.println("timestamp,action,username,ip,detail,raw");
            for (JsonObject entry : entries) {
                w.println(csv(entry, "timestamp") + "," + csv(entry, "action") + "," + csv(entry, "username") + ","
                        + csv(entry, "ip") + "," + csv(entry, "detail") + "," + csv(entry, "raw"));
            }
            w.flush();
            return;
        }

        res.setContentType("application/json;charset=UTF-8");
        res.setHeader("Content-Disposition", "attachment; filename=\"audit-export.json\"");
        JsonObject resp = new JsonObject();
        JsonArray arr = new JsonArray();
        entries.forEach(arr::add);
        resp.add("entries", arr);
        writeJson(res, 200, resp);
    }

    private void handleCompatibility(HttpServletRequest req, HttpServletResponse res) throws IOException {
        res.setContentType("application/json;charset=UTF-8");
        if (!isAuthenticated(req)) { sendError(res, 401, "Unauthorized"); return; }
        JsonObject obj = new JsonObject();
        String bukkitVersion = Bukkit.getBukkitVersion();
        PluginMeta pluginMeta = plugin.getPluginMeta();
        String pluginApi = pluginMeta.getAPIVersion();
        String detectedLine = detectLine(bukkitVersion);
        String jarLine = detectLine(pluginApi);
        obj.addProperty("pluginVersion", pluginMeta.getVersion());
        obj.addProperty("pluginApiVersion", pluginApi);
        obj.addProperty("serverName", Bukkit.getName());
        obj.addProperty("serverVersion", Bukkit.getVersion());
        obj.addProperty("bukkitVersion", bukkitVersion);
        obj.addProperty("javaVersion", System.getProperty("java.version"));
        obj.addProperty("javaVendor", System.getProperty("java.vendor"));
        obj.addProperty("detectedServerLine", detectedLine);
        obj.addProperty("jarLine", jarLine);
        obj.addProperty("compatible", jarLine.equals("unknown") || detectedLine.equals("unknown") || jarLine.equals(detectedLine));
        writeJson(res, 200, obj);
    }

    private void handleConfig(HttpServletRequest req, HttpServletResponse res) throws IOException {
        res.setContentType("application/json;charset=UTF-8");
        if (!isAuthenticated(req)) { sendError(res, 401, "Unauthorized"); return; }
        JsonObject obj = new JsonObject();
        JsonObject logging = new JsonObject();
        logging.addProperty("logCommands", plugin.getPluginConfig().isLogCommands());
        logging.addProperty("logAuth", plugin.getPluginConfig().isLogAuth());
        logging.addProperty("auditLog", plugin.getPluginConfig().isAuditLog());
        obj.add("logging", logging);

        JsonObject systemStats = new JsonObject();
        systemStats.addProperty("enabled", plugin.getPluginConfig().isSystemStatsEnabled());
        systemStats.addProperty("updateIntervalSeconds", plugin.getPluginConfig().getSystemStatsUpdateIntervalSeconds());
        systemStats.addProperty("showDisk", plugin.getPluginConfig().isShowDiskStats());
        obj.add("systemStats", systemStats);

        JsonArray blocked = new JsonArray();
        plugin.getPluginConfig().getBlockedCommands().stream().sorted().forEach(blocked::add);
        obj.add("blockedCommands", blocked);

        JsonArray aliases = new JsonArray();
        for (Map.Entry<String, String> entry : plugin.getPluginConfig().getAliases().entrySet()) {
            JsonObject alias = new JsonObject();
            alias.addProperty("name", entry.getKey());
            alias.addProperty("command", entry.getValue());
            aliases.add(alias);
        }
        obj.add("aliases", aliases);
        writeJson(res, 200, obj);
    }

    private void handleErrors(HttpServletRequest req, HttpServletResponse res) throws IOException {
        res.setContentType("application/json;charset=UTF-8");
        if (!isAuthenticated(req)) { sendError(res, 401, "Unauthorized"); return; }
        JsonObject obj = new JsonObject();
        obj.add("groups", plugin.getConsoleLogHandler().errorGroupsJson());
        writeJson(res, 200, obj);
    }

    private void handlePlayer(HttpServletRequest req, HttpServletResponse res) throws IOException {
        res.setContentType("application/json;charset=UTF-8");
        if (!isAuthenticated(req)) { sendError(res, 401, "Unauthorized"); return; }
        JsonObject profile = plugin.getServerStats().playerProfileJson(req.getParameter("id"));
        if (!profile.has("online") || !profile.get("online").getAsBoolean()) {
            sendError(res, 404, "Player not online");
            return;
        }
        writeJson(res, 200, profile);
    }

    private void handleConfigSave(HttpServletRequest req, HttpServletResponse res) throws IOException {
        res.setContentType("application/json;charset=UTF-8");
        if (!isAuthenticated(req)) { sendError(res, 401, "Unauthorized"); return; }
        String body = readBody(req);
        if (body == null) { sendError(res, 400, "Invalid body"); return; }

        JsonObject input;
        try {
            input = JsonParser.parseString(body).getAsJsonObject();
        } catch (Exception e) {
            sendError(res, 400, "Invalid JSON");
            return;
        }

        Map<String, String> aliases;
        List<String> blocked;
        try {
            aliases = parseAliases(input.getAsJsonArray("aliases"));
            blocked = parseStringList(input.getAsJsonArray("blockedCommands"), 64, 80);
        } catch (IOException e) {
            sendError(res, 400, e.getMessage());
            return;
        }
        JsonObject logging = input.has("logging") && input.get("logging").isJsonObject() ? input.getAsJsonObject("logging") : new JsonObject();
        JsonObject systemStats = input.has("systemStats") && input.get("systemStats").isJsonObject() ? input.getAsJsonObject("systemStats") : new JsonObject();

        FileConfiguration cfg = plugin.getConfig();
        cfg.set("commands.aliases", null);
        for (Map.Entry<String, String> entry : aliases.entrySet()) cfg.set("commands.aliases." + entry.getKey(), entry.getValue());
        cfg.set("commands.blocked", blocked);
        cfg.set("logging.log-commands", bool(logging, "logCommands", plugin.getPluginConfig().isLogCommands()));
        cfg.set("logging.log-auth", bool(logging, "logAuth", plugin.getPluginConfig().isLogAuth()));
        cfg.set("logging.audit-log", bool(logging, "auditLog", plugin.getPluginConfig().isAuditLog()));
        cfg.set("system-stats.enabled", bool(systemStats, "enabled", plugin.getPluginConfig().isSystemStatsEnabled()));
        cfg.set("system-stats.update-interval-seconds", Math.max(2, Math.min(60,
                integer(systemStats, "updateIntervalSeconds", plugin.getPluginConfig().getSystemStatsUpdateIntervalSeconds()))));
        cfg.set("system-stats.show-disk", bool(systemStats, "showDisk", plugin.getPluginConfig().isShowDiskStats()));
        plugin.saveConfig();
        plugin.reloadPluginConfig();

        if (plugin.getPluginConfig().isAuditLog()) {
            plugin.getAuditLog().log(getSessionUsername(req), req.getRemoteAddr(), "CONFIG", "web config saved");
        }

        JsonObject resp = new JsonObject();
        resp.addProperty("success", true);
        writeJson(res, 200, resp);
    }

    private void handleLogExport(HttpServletRequest req, HttpServletResponse res) throws IOException {
        if (!isAuthenticated(req)) { sendError(res, 401, "Unauthorized"); return; }

        List<String> lines = plugin.getConsoleLogHandler().getBufferedLines();

        // Optional text filter via ?q=... query param
        String q = req.getParameter("q");
        if (q != null && !q.isBlank()) {
            final String lq = q.toLowerCase();
            List<String> filtered = new ArrayList<>();
            for (String line : lines) {
                if (line.toLowerCase().contains(lq)) filtered.add(line);
            }
            lines = filtered;
        }

        res.setContentType("text/plain;charset=UTF-8");
        res.setHeader("Content-Disposition", "attachment; filename=\"console-export.log\"");
        PrintWriter w = res.getWriter();
        lines.forEach(w::println);
        w.flush();

        if (plugin.getPluginConfig().isAuditLog()) {
            plugin.getAuditLog().log(getSessionUsername(req), req.getRemoteAddr(), "EXPORT", "log export");
        }
    }

    private void handleLogin(HttpServletRequest req, HttpServletResponse res) throws IOException {
        res.setContentType("application/json;charset=UTF-8");
        String ip = req.getRemoteAddr();

        if (rateLimiter.isIpLocked(ip)) {
            long rem = rateLimiter.getLockoutRemainingSeconds(ip);
            if (plugin.getPluginConfig().isLogAuth()) plugin.getLogger().warning("[AUTH] Locked IP: " + ip);
            sendError(res, 429, "Too many attempts. Try in " + rem + "s.");
            return;
        }

        String body = readBody(req);
        if (body == null) { sendError(res, 400, "Invalid body"); return; }

        String username, password, csrf;
        try {
            JsonObject j = JsonParser.parseString(body).getAsJsonObject();
            username = j.get("username").getAsString().trim();
            password = j.get("password").getAsString();
            csrf     = j.has("csrf") ? j.get("csrf").getAsString() : "";
        } catch (Exception e) { sendError(res, 400, "Invalid JSON"); return; }

        if (!csrfUtil.validateToken(csrf)) { sendError(res, 403, "Invalid CSRF token"); return; }

        if (!USERNAME.matcher(username).matches()) {
            handleFailedLogin(ip, "invalid");
            sendError(res, 401, "Invalid credentials");
            return;
        }

        if (!plugin.getUserManager().verifyPassword(username, password)) {
            handleFailedLogin(ip, username);
            sendError(res, 401, "Invalid credentials");
            return;
        }

        rateLimiter.resetLoginFailures(ip);
        String token = sessionManager.createSession(username, ip);
        String secure = plugin.getPluginConfig().isSecureCookies() ? "; Secure" : "";
        res.addHeader("Set-Cookie",
                "session=" + token + "; Path=/; HttpOnly; SameSite=Strict" + secure + "; Max-Age=" + (plugin.getPluginConfig().getSessionTimeout() * 60));

        if (plugin.getPluginConfig().isLogAuth()) plugin.getLogger().info("[AUTH] Login: " + username + "@" + ip);
        if (plugin.getPluginConfig().isAuditLog()) plugin.getAuditLog().logLogin(username, ip);

        JsonObject obj = new JsonObject();
        obj.addProperty("success", true);
        obj.addProperty("username", username);
        writeJson(res, 200, obj);
    }

    private void handleFailedLogin(String ip, String username) {
        rateLimiter.recordFailedLogin(ip);
        if (plugin.getPluginConfig().isLogAuth()) {
            plugin.getLogger().warning("[AUTH] Failed: " + username + "@" + ip);
        }
        if (plugin.getPluginConfig().isAuditLog()) {
            plugin.getAuditLog().logFailed(username, ip);
        }
        try {
            Thread.sleep(600);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void handleLogout(HttpServletRequest req, HttpServletResponse res) throws IOException {
        res.setContentType("application/json;charset=UTF-8");
        String token = getSessionCookie(req);
        if (token != null) {
            SessionManager.Session s = sessionManager.validateSession(token);
            if (s != null) {
                if (plugin.getPluginConfig().isLogAuth()) plugin.getLogger().info("[AUTH] Logout: " + s.getUsername());
                if (plugin.getPluginConfig().isAuditLog()) plugin.getAuditLog().logLogout(s.getUsername(), s.getRemoteIp());
            }
            sessionManager.invalidateSession(token);
            rateLimiter.removeSession(token);
        }
        String secure = plugin.getPluginConfig().isSecureCookies() ? "; Secure" : "";
        res.addHeader("Set-Cookie", "session=; Path=/; HttpOnly; SameSite=Strict" + secure + "; Max-Age=0");
        JsonObject obj = new JsonObject();
        obj.addProperty("success", true);
        writeJson(res, 200, obj);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean isAuthenticated(HttpServletRequest req) {
        return sessionManager.validateSession(getSessionCookie(req)) != null;
    }

    private String getSessionUsername(HttpServletRequest req) {
        SessionManager.Session s = sessionManager.validateSession(getSessionCookie(req));
        return s != null ? s.getUsername() : "unknown";
    }

    private String getSessionCookie(HttpServletRequest req) {
        Cookie[] cookies = req.getCookies();
        if (cookies == null) return null;
        for (Cookie c : cookies) if ("session".equals(c.getName())) return c.getValue();
        return null;
    }

    private String readBody(HttpServletRequest req) {
        StringBuilder body = new StringBuilder(256);
        try (var reader = req.getReader()) {
            char[] buffer = new char[1024];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                if (body.length() + read > MAX_BODY_CHARS) return null;
                body.append(buffer, 0, read);
            }
            return body.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private int parseLimit(String raw, int fallback) {
        try {
            return Math.max(1, Math.min(500, Integer.parseInt(raw)));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private Map<String, String> parseAliases(JsonArray raw) throws IOException {
        Map<String, String> aliases = new LinkedHashMap<>();
        if (raw == null) return aliases;
        if (raw.size() > 100) throw new IOException("Too many aliases");
        for (JsonElement item : raw) {
            if (!item.isJsonObject()) continue;
            JsonObject obj = item.getAsJsonObject();
            String name = obj.has("name") ? obj.get("name").getAsString().trim().toLowerCase(Locale.ROOT) : "";
            String command = obj.has("command") ? obj.get("command").getAsString().trim() : "";
            if (name.isBlank() && command.isBlank()) continue;
            if (!ALIAS_NAME.matcher(name).matches()) throw new IOException("Invalid alias name: " + name);
            if (command.isBlank() || command.length() > 300) throw new IOException("Invalid alias command: " + name);
            aliases.put(name, command);
        }
        return aliases;
    }

    private List<String> parseStringList(JsonArray raw, int maxItems, int maxLength) throws IOException {
        List<String> values = new ArrayList<>();
        if (raw == null) return values;
        if (raw.size() > maxItems) throw new IOException("Too many values");
        for (JsonElement item : raw) {
            if (!item.isJsonPrimitive()) continue;
            String value = item.getAsString().trim();
            if (value.isBlank()) continue;
            if (value.length() > maxLength) throw new IOException("Value too long");
            values.add(value);
        }
        return values;
    }

    private boolean bool(JsonObject obj, String key, boolean fallback) {
        return obj.has(key) && obj.get(key).isJsonPrimitive() ? obj.get(key).getAsBoolean() : fallback;
    }

    private int integer(JsonObject obj, String key, int fallback) {
        try {
            return obj.has(key) ? obj.get(key).getAsInt() : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String detectLine(String version) {
        if (version == null) return "unknown";
        String v = version.toLowerCase(Locale.ROOT);
        if (v.startsWith("26.1") || v.contains("mc: 26.1") || v.contains("minecraft version 26.1")) return "26.1.X";
        if (v.startsWith("1.21") || v.contains("mc: 1.21") || v.contains("minecraft version 1.21")) return "1.21.X";
        return "unknown";
    }

    private String csv(JsonObject obj, String key) {
        String value = obj.has(key) ? obj.get(key).getAsString() : "";
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private void sendError(HttpServletResponse res, int code, String msg) throws IOException {
        res.setContentType("application/json;charset=UTF-8");
        res.setStatus(code);
        JsonObject obj = new JsonObject();
        obj.addProperty("error", msg);
        res.getWriter().print(GSON.toJson(obj));
        res.getWriter().flush();
    }

    private void writeJson(HttpServletResponse res, int code, JsonObject obj) throws IOException {
        res.setStatus(code);
        res.getWriter().print(GSON.toJson(obj));
        res.getWriter().flush();
    }
}
