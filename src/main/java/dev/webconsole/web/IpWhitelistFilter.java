package dev.webconsole.web;

import dev.webconsole.util.IpWhitelistChecker;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Servlet Filter that blocks requests from IPs not in the whitelist.
 * Applied to all routes so neither HTTP nor WebSocket upgrade requests bypass it.
 */
public class IpWhitelistFilter implements Filter {

    private final IpWhitelistChecker checker;

    public IpWhitelistFilter(IpWhitelistChecker checker) {
        this.checker = checker;
    }

    @Override
    public void init(FilterConfig filterConfig) {}

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (!checker.isAllowed(request.getRemoteAddr())) {
            if (response instanceof HttpServletResponse res) {
                res.sendError(HttpServletResponse.SC_FORBIDDEN, "IP not allowed");
            }
            return;
        }
        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {}
}
