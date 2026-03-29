package dev.webconsole.web;

import dev.webconsole.BetterWebConsolePlugin;
import dev.webconsole.auth.RateLimiter;
import dev.webconsole.auth.SessionManager;
import dev.webconsole.config.PluginConfig;
import dev.webconsole.util.CsrfUtil;
import dev.webconsole.util.IpWhitelistChecker;
import jakarta.servlet.DispatcherType;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.websocket.server.config.JettyWebSocketServletContainerInitializer;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.EnumSet;

/**
 * Manages the embedded Jetty HTTP/WebSocket server.
 */
public class WebServer {

    private final BetterWebConsolePlugin plugin;
    private final PluginConfig config;

    private Server server;
    private SessionManager sessionManager;
    private RateLimiter rateLimiter;
    private IpWhitelistChecker ipWhitelistChecker;
    private CsrfUtil csrfUtil;
    private WebSocketHandler wsHandler;

    public WebServer(BetterWebConsolePlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getPluginConfig();
    }

    public void start() throws Exception {
        sessionManager = new SessionManager(config.getSessionTimeout());
        rateLimiter = new RateLimiter(
                config.getMaxLoginAttempts(),
                config.getLockoutDuration(),
                config.getCommandRateLimit());
        ipWhitelistChecker = new IpWhitelistChecker(config.getIpWhitelist());
        csrfUtil = new CsrfUtil(config.getCsrfSecret());

        wsHandler = new WebSocketHandler(plugin, sessionManager, rateLimiter, ipWhitelistChecker);
        plugin.getConsoleLogHandler().setWebSocketHandler(wsHandler);

        server = new Server(new InetSocketAddress(config.getBindAddress(), config.getPort()));

        // Disable server version header
        for (var connector : server.getConnectors()) {
            if (connector instanceof ServerConnector sc) {
                sc.getConnectionFactories().forEach(cf -> {
                    if (cf instanceof org.eclipse.jetty.server.HttpConnectionFactory hcf) {
                        hcf.getHttpConfiguration().setSendServerVersion(false);
                        hcf.getHttpConfiguration().setSendDateHeader(false);
                    }
                });
            }
        }

        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");

        // Security headers filter (all requests)
        context.addFilter(SecurityHeadersFilter.class, "/*", EnumSet.of(DispatcherType.REQUEST));

        // IP whitelist filter (all requests, before any servlet)
        context.addFilter(
                new FilterHolder(new IpWhitelistFilter(ipWhitelistChecker)),
                "/*",
                EnumSet.of(DispatcherType.REQUEST));

        // WebSocket support
        JettyWebSocketServletContainerInitializer.configure(context, (servletContext, wsContainer) -> {
            wsContainer.setMaxTextMessageSize(64 * 1024);
            wsContainer.setIdleTimeout(Duration.ofMinutes(config.getSessionTimeout()));
        });

        // REST API servlet
        var apiServlet = new ServletHolder(new ApiServlet(plugin, sessionManager, rateLimiter,
                csrfUtil, ipWhitelistChecker, wsHandler));
        context.addServlet(apiServlet, "/api/*");

        // WebSocket servlet
        var wsServlet = new ServletHolder(new ConsoleWebSocketServlet(
                plugin, sessionManager, rateLimiter, ipWhitelistChecker, wsHandler));
        context.addServlet(wsServlet, "/ws");

        // Static frontend servlet (serves built-in HTML)
        var staticServlet = new ServletHolder(new FrontendServlet());
        context.addServlet(staticServlet, "/*");

        server.setHandler(context);
        server.start();
    }

    public void stop() throws Exception {
        if (server != null) server.stop();
        if (sessionManager != null) sessionManager.shutdown();
        plugin.getConsoleLogHandler().setWebSocketHandler(null);
    }

    public boolean isRunning() {
        return server != null && server.isRunning();
    }

    public int getActiveSessionCount() {
        return sessionManager != null ? sessionManager.getActiveSessionCount() : 0;
    }
}
