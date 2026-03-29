package dev.webconsole.config;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;
import java.util.UUID;

public class PluginConfig {

    private final int port;
    private final String bindAddress;
    private final String basePath;
    private final int logBufferSize;

    private final int sessionTimeout;
    private final int maxLoginAttempts;
    private final int lockoutDuration;
    private final int commandRateLimit;
    private final List<String> ipWhitelist;
    private final String csrfSecret;

    private final boolean logCommands;
    private final boolean logAuth;

    public PluginConfig(FileConfiguration config) {
        this.port = config.getInt("web.port", 4242);
        this.bindAddress = config.getString("web.bind-address", "0.0.0.0");
        String bp = config.getString("web.base-path", "/");
        this.basePath = bp == null || bp.isBlank() ? "/" : bp;
        this.logBufferSize = Math.max(10, Math.min(5000, config.getInt("web.log-buffer-size", 500)));

        this.sessionTimeout = config.getInt("security.session-timeout", 60);
        this.maxLoginAttempts = config.getInt("security.max-login-attempts", 5);
        this.lockoutDuration = config.getInt("security.lockout-duration", 15);
        this.commandRateLimit = config.getInt("security.command-rate-limit", 30);
        this.ipWhitelist = config.getStringList("security.ip-whitelist");
        String secret = config.getString("security.csrf-secret", "");
        this.csrfSecret = (secret == null || secret.isBlank()) ? UUID.randomUUID().toString() : secret;

        this.logCommands = config.getBoolean("logging.log-commands", true);
        this.logAuth = config.getBoolean("logging.log-auth", true);
    }

    public int getPort() { return port; }
    public String getBindAddress() { return bindAddress; }
    public String getBasePath() { return basePath; }
    public int getLogBufferSize() { return logBufferSize; }
    public int getSessionTimeout() { return sessionTimeout; }
    public int getMaxLoginAttempts() { return maxLoginAttempts; }
    public int getLockoutDuration() { return lockoutDuration; }
    public int getCommandRateLimit() { return commandRateLimit; }
    public List<String> getIpWhitelist() { return ipWhitelist; }
    public String getCsrfSecret() { return csrfSecret; }
    public boolean isLogCommands() { return logCommands; }
    public boolean isLogAuth() { return logAuth; }
}
