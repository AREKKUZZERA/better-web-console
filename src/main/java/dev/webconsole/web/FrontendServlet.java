package dev.webconsole.web;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.InputStream;

/**
 * Serves the built-in single-page web console interface.
 */
public class FrontendServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
        String path = req.getPathInfo();
        if (path == null) path = "/";

        if (path.startsWith("/assets/")) {
            serveResource(path, res);
            return;
        }

        if (path.equals("/") || path.equals("/index.html") || path.equals("/react") || path.equals("/react/") || path.equals("/react/index.html")) {
            serveResource("/index.html", res);
            return;
        }

        res.sendError(404);
    }

    private void serveResource(String path, HttpServletResponse res) throws IOException {
        String normalized = path.startsWith("/") ? path : "/" + path;
        try (InputStream is = getClass().getResourceAsStream(normalized)) {
            if (is == null) {
                res.sendError(404);
                return;
            }
            if (normalized.endsWith(".html")) {
                res.setContentType("text/html;charset=UTF-8");
                res.setHeader("Cache-Control", "no-cache");
            } else if (normalized.endsWith(".js")) {
                res.setContentType("application/javascript;charset=UTF-8");
                res.setHeader("Cache-Control", "public, max-age=31536000, immutable");
            } else if (normalized.endsWith(".css")) {
                res.setContentType("text/css;charset=UTF-8");
                res.setHeader("Cache-Control", "public, max-age=31536000, immutable");
            } else {
                res.setContentType("application/octet-stream");
                res.setHeader("Cache-Control", "public, max-age=31536000, immutable");
            }
            res.setStatus(200);
            is.transferTo(res.getOutputStream());
        }
    }

}
