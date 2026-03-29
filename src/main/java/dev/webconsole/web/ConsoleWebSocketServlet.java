package dev.webconsole.web;

import dev.webconsole.BetterWebConsolePlugin;
import dev.webconsole.auth.RateLimiter;
import dev.webconsole.auth.SessionManager;
import dev.webconsole.util.IpWhitelistChecker;
import org.eclipse.jetty.websocket.server.JettyWebSocketServlet;
import org.eclipse.jetty.websocket.server.JettyWebSocketServletFactory;

import java.time.Duration;

public class ConsoleWebSocketServlet extends JettyWebSocketServlet {

    private final BetterWebConsolePlugin plugin;
    private final SessionManager sessionManager;
    private final RateLimiter rateLimiter;
    private final IpWhitelistChecker ipChecker;
    private final WebSocketHandler wsHandler;

    public ConsoleWebSocketServlet(BetterWebConsolePlugin plugin, SessionManager sessionManager,
                                   RateLimiter rateLimiter, IpWhitelistChecker ipChecker,
                                   WebSocketHandler wsHandler) {
        this.plugin = plugin;
        this.sessionManager = sessionManager;
        this.rateLimiter = rateLimiter;
        this.ipChecker = ipChecker;
        this.wsHandler = wsHandler;
    }

    @Override
    protected void configure(JettyWebSocketServletFactory factory) {
        factory.setIdleTimeout(Duration.ofMinutes(plugin.getPluginConfig().getSessionTimeout()));
        factory.setMaxTextMessageSize(64 * 1024);
        factory.setCreator((req, resp) -> {
            // JettyServerUpgradeRequest exposes the underlying HttpServletRequest
            String ip = req.getHttpServletRequest().getRemoteAddr();

            // IP whitelist check
            if (!ipChecker.isAllowed(ip)) {
                resp.setStatusCode(403);
                return null;
            }

            // Session validation via cookie
            String sessionToken = null;
            String cookieHeader = req.getHeader("Cookie");
            if (cookieHeader != null) {
                for (String part : cookieHeader.split(";")) {
                    part = part.trim();
                    if (part.startsWith("session=")) {
                        sessionToken = part.substring("session=".length());
                        break;
                    }
                }
            }

            SessionManager.Session session = sessionManager.validateSession(sessionToken);
            if (session == null) {
                resp.setStatusCode(401);
                return null;
            }

            return new ConsoleWebSocket(plugin, sessionManager, rateLimiter, wsHandler, sessionToken, session);
        });
    }
}
