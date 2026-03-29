package dev.webconsole.web;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.webconsole.BetterWebConsolePlugin;
import dev.webconsole.auth.RateLimiter;
import dev.webconsole.auth.SessionManager;
import dev.webconsole.config.PluginConfig;
import dev.webconsole.util.CsrfUtil;
import dev.webconsole.util.IpWhitelistChecker;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST API endpoints:
 *   GET  /api/csrf        — CSRF token
 *   GET  /api/status      — session check
 *   GET  /api/stats       — server stats JSON (auth required)
 *   GET  /api/aliases     — command alias list (auth required)
 *   GET  /api/logs/export — download console log as text file (auth required)
 *   POST /api/login       — authenticate
 *   POST /api/logout      — invalidate session
 */
public class ApiServlet extends HttpServlet {

    private static final Gson GSON = new Gson();

    private final BetterWebConsolePlugin plugin;
    private final PluginConfig config;
    private final SessionManager sessionManager;
    private final RateLimiter rateLimiter;
    private final CsrfUtil csrfUtil;
    private final IpWhitelistChecker ipChecker;

    public ApiServlet(BetterWebConsolePlugin plugin, SessionManager sessionManager,
                      RateLimiter rateLimiter, CsrfUtil csrfUtil,
                      IpWhitelistChecker ipChecker, WebSocketHandler wsHandler) {
        this.plugin         = plugin;
        this.config         = plugin.getPluginConfig();
        this.sessionManager = sessionManager;
        this.rateLimiter    = rateLimiter;
        this.csrfUtil       = csrfUtil;
        this.ipChecker      = ipChecker;
        // wsHandler reserved for future API endpoints (e.g. broadcast message)
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
            case "/logs/export" -> handleLogExport(req, res);
            default             -> sendError(res, 404, "Not found");
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {
        if (!ipChecker.isAllowed(req.getRemoteAddr())) { sendError(res, 403, "IP not allowed"); return; }
        String path = req.getPathInfo() == null ? "/" : req.getPathInfo();
        switch (path) {
            case "/login"  -> handleLogin(req, res);
            case "/logout" -> handleLogout(req, res);
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
        for (Map.Entry<String, String> e : config.getAliases().entrySet()) {
            JsonObject obj = new JsonObject();
            obj.addProperty("name", e.getKey());
            obj.addProperty("command", e.getValue());
            arr.add(obj);
        }
        JsonObject resp = new JsonObject();
        resp.add("aliases", arr);
        writeJson(res, 200, resp);
    }

    private void handleLogExport(HttpServletRequest req, HttpServletResponse res) throws IOException {
        if (!isAuthenticated(req)) { sendError(res, 401, "Unauthorized"); return; }

        List<String> lines = plugin.getConsoleLogHandler().getBufferedLines();

        // Optional text filter via ?q=... query param
        String q = req.getParameter("q");
        if (q != null && !q.isBlank()) {
            final String lq = q.toLowerCase();
            lines = lines.stream().filter(l -> l.toLowerCase().contains(lq)).collect(Collectors.toList());
        }

        res.setContentType("text/plain;charset=UTF-8");
        res.setHeader("Content-Disposition", "attachment; filename=\"console-export.log\"");
        PrintWriter w = res.getWriter();
        lines.forEach(w::println);
        w.flush();

        plugin.getAuditLog().log(getSessionUsername(req), req.getRemoteAddr(), "EXPORT", "log export");
    }

    private void handleLogin(HttpServletRequest req, HttpServletResponse res) throws IOException {
        res.setContentType("application/json;charset=UTF-8");
        String ip = req.getRemoteAddr();

        if (rateLimiter.isIpLocked(ip)) {
            long rem = rateLimiter.getLockoutRemainingSeconds(ip);
            if (config.isLogAuth()) plugin.getLogger().warning("[AUTH] Locked IP: " + ip);
            sendError(res, 429, "Too many attempts. Try in " + rem + "s.");
            return;
        }

        String body;
        try { body = req.getReader().lines().collect(Collectors.joining()); }
        catch (Exception e) { sendError(res, 400, "Invalid body"); return; }

        String username, password, csrf;
        try {
            JsonObject j = JsonParser.parseString(body).getAsJsonObject();
            username = j.get("username").getAsString().trim();
            password = j.get("password").getAsString();
            csrf     = j.has("csrf") ? j.get("csrf").getAsString() : "";
        } catch (Exception e) { sendError(res, 400, "Invalid JSON"); return; }

        if (!csrfUtil.validateToken(csrf)) { sendError(res, 403, "Invalid CSRF token"); return; }

        if (!plugin.getUserManager().verifyPassword(username, password)) {
            rateLimiter.recordFailedLogin(ip);
            if (config.isLogAuth()) plugin.getLogger().warning("[AUTH] Failed: " + username + "@" + ip);
            plugin.getAuditLog().logFailed(username, ip);
            try { Thread.sleep(600); } catch (InterruptedException ignored) {}
            sendError(res, 401, "Invalid credentials");
            return;
        }

        rateLimiter.resetLoginFailures(ip);
        String token = sessionManager.createSession(username, ip);
        res.addHeader("Set-Cookie",
                "session=" + token + "; Path=/; HttpOnly; SameSite=Strict; Max-Age=" + (config.getSessionTimeout() * 60));

        if (config.isLogAuth()) plugin.getLogger().info("[AUTH] Login: " + username + "@" + ip);
        plugin.getAuditLog().logLogin(username, ip);

        JsonObject obj = new JsonObject();
        obj.addProperty("success", true);
        obj.addProperty("username", username);
        writeJson(res, 200, obj);
    }

    private void handleLogout(HttpServletRequest req, HttpServletResponse res) throws IOException {
        res.setContentType("application/json;charset=UTF-8");
        String token = getSessionCookie(req);
        if (token != null) {
            SessionManager.Session s = sessionManager.validateSession(token);
            if (s != null) {
                if (config.isLogAuth()) plugin.getLogger().info("[AUTH] Logout: " + s.getUsername());
                plugin.getAuditLog().logLogout(s.getUsername(), s.getRemoteIp());
            }
            sessionManager.invalidateSession(token);
            rateLimiter.removeSession(token);
        }
        res.addHeader("Set-Cookie", "session=; Path=/; HttpOnly; SameSite=Strict; Max-Age=0");
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
