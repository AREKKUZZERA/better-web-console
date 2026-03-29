package dev.webconsole.web;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

/**
 * Serves the built-in single-page web console interface.
 */
public class FrontendServlet extends HttpServlet {

    private volatile String cachedHtml;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
        String path = req.getPathInfo();
        if (path == null) path = "/";

        // Only serve index for root path
        if (!path.equals("/") && !path.equals("/index.html")) {
            res.sendError(404);
            return;
        }

        res.setContentType("text/html;charset=UTF-8");
        res.setStatus(200);

        String html = getHtml();
        PrintWriter writer = res.getWriter();
        writer.print(html);
        writer.flush();
    }

    private String getHtml() {
        if (cachedHtml != null) return cachedHtml;
        try (InputStream is = getClass().getResourceAsStream("/webconsole.html")) {
            if (is != null) {
                cachedHtml = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                return cachedHtml;
            }
        } catch (IOException ignored) {}
        // Fallback if resource not found
        return "<html><body><h1>Better-WebConsole: webconsole.html resource missing!</h1></body></html>";
    }
}
