package dev.webconsole.web;

import java.net.URI;
import java.util.Locale;

final class SameOriginGuard {

    private SameOriginGuard() {}

    static boolean isAllowed(String origin, String referer, String host) {
        if ((origin == null || origin.isBlank()) && (referer == null || referer.isBlank())) {
            return true;
        }
        if (host == null || host.isBlank()) {
            return false;
        }

        String expectedHost = normalizeHost(host);
        if (origin != null && !origin.isBlank()) {
            return expectedHost.equals(originHost(origin));
        }
        return expectedHost.equals(originHost(referer));
    }

    static boolean isAllowed(String origin, String host) {
        return isAllowed(origin, null, host);
    }

    private static String originHost(String value) {
        try {
            URI uri = URI.create(value);
            String host = uri.getHost();
            if (host == null || host.isBlank()) return "";
            int port = uri.getPort();
            return normalizeHost(port >= 0 ? host + ":" + port : host);
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    private static String normalizeHost(String value) {
        String host = value.trim().toLowerCase(Locale.ROOT);
        int comma = host.indexOf(',');
        if (comma >= 0) host = host.substring(0, comma).trim();
        return host;
    }
}
