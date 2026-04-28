package dev.webconsole.config;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import java.util.regex.Pattern;

public class PluginConfig {

    private static final Pattern ALIAS_NAME = Pattern.compile("[a-z0-9_-]{1,32}");

    private final int port;
    private final String bindAddress;
    private final int logBufferSize;
    private final int sessionTimeout;
    private final int maxLoginAttempts;
    private final int lockoutDuration;
    private final int commandRateLimit;
    private final List<String> ipWhitelist;
    private final String csrfSecret;
    private final boolean logCommands;
    private final boolean logAuth;
    private final boolean auditLog;
    private final Map<String, String> aliases;
    private final Set<String> blockedCommands;
    private final boolean secureCookies;

    public PluginConfig(FileConfiguration config) {
        this.port          = validPort(config.getInt("web.port", 4242));
        this.bindAddress   = config.getString("web.bind-address", "0.0.0.0");
        this.logBufferSize = Math.max(10, Math.min(5000, config.getInt("web.log-buffer-size", 1000)));
        this.sessionTimeout    = positiveInt(config, "security.session-timeout-minutes", "security.session-timeout", 60);
        this.maxLoginAttempts  = positiveInt(config, "security.max-login-attempts", null, 5);
        this.lockoutDuration   = positiveInt(config, "security.lockout-duration-minutes", "security.lockout-duration", 15);
        this.commandRateLimit  = positiveInt(config, "security.command-rate-limit-per-minute", "security.command-rate-limit", 30);
        this.ipWhitelist = config.getStringList("security.ip-whitelist");
        String secret = config.getString("security.csrf-secret", "");
        this.csrfSecret  = (secret == null || secret.isBlank()) ? UUID.randomUUID().toString() : secret;
        this.secureCookies = config.getBoolean("security.secure-cookies", false);
        this.logCommands = config.getBoolean("logging.log-commands", true);
        this.logAuth     = config.getBoolean("logging.log-auth", true);
        this.auditLog    = config.getBoolean("logging.audit-log", true);

        this.aliases = new LinkedHashMap<>();
        loadAliases(config, "commands.aliases");
        loadAliases(config, "aliases");

        this.blockedCommands = new HashSet<>();
        for (String command : config.getStringList("commands.blocked")) {
            String label = normalizeCommandLabel(command);
            if (!label.isBlank()) blockedCommands.add(label);
        }
    }

    private static int validPort(int value) {
        return value >= 1 && value <= 65535 ? value : 4242;
    }

    private static int positiveInt(FileConfiguration config, String path, String legacyPath, int fallback) {
        int value = config.contains(path) ? config.getInt(path)
                : legacyPath != null ? config.getInt(legacyPath, fallback)
                : config.getInt(path, fallback);
        return value > 0 ? value : fallback;
    }

    private void loadAliases(FileConfiguration config, String path) {
        var aliasSection = config.getConfigurationSection(path);
        if (aliasSection == null) return;

        for (String key : aliasSection.getKeys(false)) {
            String normalizedKey = key.toLowerCase();
            String value = aliasSection.getString(key);
            if (!ALIAS_NAME.matcher(normalizedKey).matches() || value == null || value.isBlank()) continue;
            aliases.put(normalizedKey, value.trim());
        }
    }

    public boolean isCommandBlocked(String commandLine) {
        return blockedCommands.contains(normalizeCommandLabel(commandLine));
    }

    private static String normalizeCommandLabel(String commandLine) {
        if (commandLine == null) return "";
        String trimmed = commandLine.trim();
        if (trimmed.startsWith("/")) trimmed = trimmed.substring(1).trim();
        if (trimmed.isBlank()) return "";
        int space = trimmed.indexOf(' ');
        return (space >= 0 ? trimmed.substring(0, space) : trimmed).toLowerCase();
    }

    public int getPort()               { return port; }
    public String getBindAddress()     { return bindAddress; }
    public int getLogBufferSize()      { return logBufferSize; }
    public int getSessionTimeout()     { return sessionTimeout; }
    public int getMaxLoginAttempts()   { return maxLoginAttempts; }
    public int getLockoutDuration()    { return lockoutDuration; }
    public int getCommandRateLimit()   { return commandRateLimit; }
    public List<String> getIpWhitelist(){ return ipWhitelist; }
    public String getCsrfSecret()      { return csrfSecret; }
    public boolean isSecureCookies()   { return secureCookies; }
    public boolean isLogCommands()     { return logCommands; }
    public boolean isLogAuth()         { return logAuth; }
    public boolean isAuditLog()        { return auditLog; }
    public Map<String, String> getAliases() { return aliases; }
    public Set<String> getBlockedCommands() { return blockedCommands; }
}
