package dev.webconsole.web;

import com.google.gson.Gson;
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
import java.util.stream.Collectors;

/**
 * Handles REST API endpoints:
 * POST /api/login  - authenticate and receive session cookie
 * POST /api/logout - invalidate session
 * GET  /api/csrf   - get a CSRF token
 * GET  /api/status - check session validity
 */
public class ApiServlet extends HttpServlet {

    private static final Gson GSON = new Gson();

    private final BetterWebConsolePlugin plugin;
    private final PluginConfig config;
    private final SessionManager sessionManager;
    private final RateLimiter rateLimiter;
    private final CsrfUtil csrfUtil;
    private final IpWhitelistChecker ipChecker;

    public ApiServlet(BetterWebConsolePlugin plugin, SessionManager sessionManager, RateLimiter rateLimiter,
                      CsrfUtil csrfUtil, IpWhitelistChecker ipChecker, WebSocketHandler wsHandler) {
        this.plugin = plugin;
        this.config = plugin.getPluginConfig();
        this.sessionManager = sessionManager;
        this.rateLimiter = rateLimiter;
        this.csrfUtil = csrfUtil;
        this.ipChecker = ipChecker;
        // wsHandler reserved for future API endpoints
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
        String path = req.getPathInfo();
        if (path == null) path = "/";

        res.setContentType("application/json;charset=UTF-8");

        if (!ipChecker.isAllowed(req.getRemoteAddr())) {
            sendError(res, 403, "IP not allowed");
            return;
        }

        switch (path) {
            case "/csrf" -> handleCsrf(req, res);
            case "/status" -> handleStatus(req, res);
            default -> sendError(res, 404, "Not found");
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {
        String path = req.getPathInfo();
        if (path == null) path = "/";

        res.setContentType("application/json;charset=UTF-8");

        if (!ipChecker.isAllowed(req.getRemoteAddr())) {
            sendError(res, 403, "IP not allowed");
            return;
        }

        switch (path) {
            case "/login" -> handleLogin(req, res);
            case "/logout" -> handleLogout(req, res);
            default -> sendError(res, 404, "Not found");
        }
    }

    private void handleCsrf(HttpServletRequest req, HttpServletResponse res) throws IOException {
        JsonObject obj = new JsonObject();
        obj.addProperty("token", csrfUtil.generateToken());
        writeJson(res, 200, obj);
    }

    private void handleStatus(HttpServletRequest req, HttpServletResponse res) throws IOException {
        String token = getSessionCookie(req);
        SessionManager.Session session = sessionManager.validateSession(token);

        JsonObject obj = new JsonObject();
        if (session != null) {
            obj.addProperty("authenticated", true);
            obj.addProperty("username", session.getUsername());
        } else {
            obj.addProperty("authenticated", false);
        }
        writeJson(res, 200, obj);
    }

    private void handleLogin(HttpServletRequest req, HttpServletResponse res) throws IOException {
        String ip = req.getRemoteAddr();

        // IP lockout check
        if (rateLimiter.isIpLocked(ip)) {
            long remaining = rateLimiter.getLockoutRemainingSeconds(ip);
            if (config.isLogAuth()) {
                plugin.getLogger().warning("[AUTH] Blocked login attempt from locked IP: " + ip);
            }
            sendError(res, 429, "Too many failed attempts. Try again in " + remaining + " seconds.");
            return;
        }

        // Parse body
        String body;
        try {
            body = req.getReader().lines().collect(Collectors.joining());
        } catch (Exception e) {
            sendError(res, 400, "Invalid request body");
            return;
        }

        String username, password, csrfToken;
        try {
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            username = json.get("username").getAsString().trim();
            password = json.get("password").getAsString();
            csrfToken = json.has("csrf") ? json.get("csrf").getAsString() : "";
        } catch (Exception e) {
            sendError(res, 400, "Invalid JSON");
            return;
        }

        // CSRF validation
        if (!csrfUtil.validateToken(csrfToken)) {
            sendError(res, 403, "Invalid CSRF token");
            return;
        }

        // Validate credentials
        boolean valid = plugin.getUserManager().verifyPassword(username, password);
        if (!valid) {
            rateLimiter.recordFailedLogin(ip);
            if (config.isLogAuth()) {
                plugin.getLogger().warning("[AUTH] Failed login for user '" + username + "' from " + ip);
            }
            // Delay response slightly to slow brute force
            try { Thread.sleep(500); } catch (InterruptedException ignored) {}
            sendError(res, 401, "Invalid credentials");
            return;
        }

        rateLimiter.resetLoginFailures(ip);
        String sessionToken = sessionManager.createSession(username, ip);

        // HttpOnly, SameSite=Strict cookie
        Cookie cookie = new Cookie("session", sessionToken);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(config.getSessionTimeout() * 60);
        // SameSite=Strict must be added manually as Jetty 11 doesn't have setSameSite
        res.addHeader("Set-Cookie", buildCookieHeader(sessionToken, config.getSessionTimeout() * 60));

        if (config.isLogAuth()) {
            plugin.getLogger().info("[AUTH] User '" + username + "' logged in from " + ip);
        }

        JsonObject obj = new JsonObject();
        obj.addProperty("success", true);
        obj.addProperty("username", username);
        writeJson(res, 200, obj);
    }

    private void handleLogout(HttpServletRequest req, HttpServletResponse res) throws IOException {
        String token = getSessionCookie(req);
        if (token != null) {
            SessionManager.Session session = sessionManager.validateSession(token);
            if (session != null && config.isLogAuth()) {
                plugin.getLogger().info("[AUTH] User '" + session.getUsername() + "' logged out.");
            }
            sessionManager.invalidateSession(token);
            rateLimiter.removeSession(token);
        }

        // Clear cookie
        res.addHeader("Set-Cookie", "session=; Path=/; HttpOnly; SameSite=Strict; Max-Age=0");

        JsonObject obj = new JsonObject();
        obj.addProperty("success", true);
        writeJson(res, 200, obj);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private String getSessionCookie(HttpServletRequest req) {
        Cookie[] cookies = req.getCookies();
        if (cookies == null) return null;
        for (Cookie c : cookies) {
            if ("session".equals(c.getName())) return c.getValue();
        }
        return null;
    }

    private String buildCookieHeader(String token, int maxAge) {
        return "session=" + token + "; Path=/; HttpOnly; SameSite=Strict; Max-Age=" + maxAge;
    }

    private void sendError(HttpServletResponse res, int status, String message) throws IOException {
        res.setStatus(status);
        JsonObject obj = new JsonObject();
        obj.addProperty("error", message);
        PrintWriter writer = res.getWriter();
        writer.print(GSON.toJson(obj));
        writer.flush();
    }

    private void writeJson(HttpServletResponse res, int status, JsonObject obj) throws IOException {
        res.setStatus(status);
        PrintWriter writer = res.getWriter();
        writer.print(GSON.toJson(obj));
        writer.flush();
    }
}
