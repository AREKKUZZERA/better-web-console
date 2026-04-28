package dev.webconsole;

import dev.webconsole.audit.AuditLog;
import dev.webconsole.auth.UserManager;
import dev.webconsole.config.PluginConfig;
import dev.webconsole.console.ConsoleLogHandler;
import dev.webconsole.stats.ServerStats;
import dev.webconsole.web.WebServer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

public class BetterWebConsolePlugin extends JavaPlugin {

    private static BetterWebConsolePlugin instance;
    private PluginConfig pluginConfig;
    private UserManager userManager;
    private WebServer webServer;
    private ConsoleLogHandler consoleLogHandler;
    private AuditLog auditLog;
    private ServerStats serverStats;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        this.pluginConfig = new PluginConfig(getConfig());
        this.userManager = new UserManager(getDataFolder());
        this.auditLog = new AuditLog(getDataFolder());

        this.consoleLogHandler = new ConsoleLogHandler(pluginConfig.getLogBufferSize());
        consoleLogHandler.hookSystemStreams();
        consoleLogHandler.hookLog4j();

        this.serverStats = new ServerStats(this);
        this.serverStats.start();

        var cmd = getCommand("betterwebconsole");
        if (cmd != null) {
            cmd.setExecutor(this);
            cmd.setTabCompleter(this);
        }

        this.webServer = new WebServer(this);
        try {
            webServer.start();
            getLogger().info("Better-WebConsole started on " + pluginConfig.getBindAddress() + ":" + pluginConfig.getPort());
        } catch (Exception e) {
            getLogger().severe("Failed to start web server: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (serverStats != null) serverStats.stop();
        if (webServer != null) try { webServer.stop(); } catch (Exception ignored) {}
        if (auditLog != null) auditLog.shutdown();

        if (consoleLogHandler != null) {
            Logger.getLogger("").removeHandler(consoleLogHandler);
            consoleLogHandler.close();
        }

        getLogger().info("Better-WebConsole disabled.");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!command.getName().equalsIgnoreCase("betterwebconsole")) return false;
        if (!sender.hasPermission("betterwebconsole.admin")) {
            sender.sendMessage(color("c", "No permission."));
            return true;
        }
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        return switch (args[0].toLowerCase()) {
            case "reload" -> handleReload(sender);
            case "status" -> handleStatus(sender);
            case "adduser", "useradd", "createuser" -> handleAddUser(sender, args);
            case "removeuser", "deluser", "deleteuser" -> handleRemoveUser(sender, args);
            case "listusers", "users" -> handleListUsers(sender);
            case "setpassword", "passwd", "password" -> handleSetPassword(sender, args);
            case "logoutall", "killsessions" -> handleLogoutAll(sender, args);
            default -> {
                sendHelp(sender);
                yield true;
            }
        };
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (!command.getName().equalsIgnoreCase("betterwebconsole")) return Collections.emptyList();
        if (!sender.hasPermission("betterwebconsole.admin")) return Collections.emptyList();

        if (args.length == 1) {
            return List.of("reload", "status", "adduser", "removeuser", "listusers", "setpassword", "logoutall").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .toList();
        }

        if (args.length == 2 && List.of("removeuser", "deluser", "setpassword", "passwd", "logoutall").contains(args[0].toLowerCase())) {
            return userManager.listUsers().stream()
                    .filter(u -> u.toLowerCase().startsWith(args[1].toLowerCase()))
                    .toList();
        }

        return Collections.emptyList();
    }

    private boolean handleReload(CommandSender s) {
        reloadConfig();
        pluginConfig = new PluginConfig(getConfig());
        s.sendMessage(color("a", "Config reloaded. Restart the server to apply web port, bind, IP whitelist, session timeout and rate-limit changes."));
        return true;
    }

    private boolean handleStatus(CommandSender s) {
        s.sendMessage(color("6", "=== Better-WebConsole Status ==="));
        s.sendMessage(color("7", "Status: " + (webServer != null && webServer.isRunning() ? color("a", "Running") : color("c", "Stopped"))));
        s.sendMessage(color("7", "Bind: ") + color("f", pluginConfig.getBindAddress() + ":" + pluginConfig.getPort()));
        s.sendMessage(color("7", "Sessions: ") + color("f", String.valueOf(webServer != null ? webServer.getActiveSessionCount() : 0))
                + color("7", "  Users: ") + color("f", String.valueOf(userManager.getUserCount())));
        s.sendMessage(color("7", "Aliases: ") + color("f", String.valueOf(pluginConfig.getAliases().size()))
                + color("7", "  Blocked commands: ") + color("f", String.valueOf(pluginConfig.getBlockedCommands().size())));
        return true;
    }

    private boolean handleAddUser(CommandSender s, String[] args) {
        if (args.length < 3) {
            s.sendMessage(color("c", "Usage: /bwc adduser <user> <password>"));
            return true;
        }

        String username = args[1];
        String password = args[2];
        if (!isValidUsername(username)) {
            s.sendMessage(color("c", "Invalid username. Use 3-32 chars: a-z, A-Z, 0-9, _ or -."));
            return true;
        }
        if (!isValidPassword(password)) {
            s.sendMessage(color("c", "Password must be at least 8 characters."));
            return true;
        }
        if (userManager.userExists(username)) {
            s.sendMessage(color("c", "User already exists."));
            return true;
        }

        userManager.addUser(username, password);
        s.sendMessage(color("a", "User '" + username + "' added."));
        if (pluginConfig.isLogAuth()) getLogger().info("[AUTH] User '" + username + "' created by " + s.getName());
        return true;
    }

    private boolean handleRemoveUser(CommandSender s, String[] args) {
        if (args.length < 2) {
            s.sendMessage(color("c", "Usage: /bwc removeuser <user>"));
            return true;
        }

        String username = args[1];
        if (userManager.removeUser(username)) {
            if (webServer != null) webServer.invalidateSessions(username);
            s.sendMessage(color("a", "User '" + username + "' removed and sessions invalidated."));
            if (pluginConfig.isLogAuth()) getLogger().info("[AUTH] User '" + username + "' removed by " + s.getName());
        } else {
            s.sendMessage(color("c", "User not found."));
        }
        return true;
    }

    private boolean handleSetPassword(CommandSender s, String[] args) {
        if (args.length < 3) {
            s.sendMessage(color("c", "Usage: /bwc setpassword <user> <new-password>"));
            return true;
        }
        if (!isValidPassword(args[2])) {
            s.sendMessage(color("c", "Password must be at least 8 characters."));
            return true;
        }
        if (!userManager.setPassword(args[1], args[2])) {
            s.sendMessage(color("c", "User not found."));
            return true;
        }
        if (webServer != null) webServer.invalidateSessions(args[1]);
        s.sendMessage(color("a", "Password changed for '" + args[1] + "'. Existing sessions invalidated."));
        if (pluginConfig.isLogAuth()) getLogger().info("[AUTH] Password changed for '" + args[1] + "' by " + s.getName());
        return true;
    }

    private boolean handleLogoutAll(CommandSender s, String[] args) {
        if (args.length < 2) {
            s.sendMessage(color("c", "Usage: /bwc logoutall <user>"));
            return true;
        }
        if (!userManager.userExists(args[1])) {
            s.sendMessage(color("c", "User not found."));
            return true;
        }
        if (webServer != null) webServer.invalidateSessions(args[1]);
        s.sendMessage(color("a", "Sessions invalidated for '" + args[1] + "'."));
        return true;
    }

    private boolean handleListUsers(CommandSender s) {
        List<String> users = userManager.listUsers();
        if (users.isEmpty()) {
            s.sendMessage(color("e", "No users. Add one with /bwc adduser <user> <password>."));
        } else {
            s.sendMessage(color("6", "Users:"));
            users.forEach(u -> s.sendMessage(color("7", " - ") + color("f", u)));
        }
        return true;
    }

    private void sendHelp(CommandSender s) {
        s.sendMessage(color("6", "=== Better-WebConsole ==="));
        s.sendMessage(color("e", "/bwc status | reload | adduser <u> <p> | removeuser <u> | listusers"));
        s.sendMessage(color("e", "/bwc setpassword <u> <p> | logoutall <u>"));
        s.sendMessage(color("7", "Aliases: /bwc, /webconsole, /bwconsole, /betterconsole"));
    }

    private boolean isValidUsername(String username) {
        return username != null && username.length() >= 3 && username.length() <= 32 && username.matches("[a-zA-Z0-9_-]+");
    }

    private boolean isValidPassword(String password) {
        return password != null && password.length() >= 8;
    }

    private String color(String code, String text) {
        return "\u00A7" + code + text;
    }

    public static BetterWebConsolePlugin getInstance() { return instance; }
    public PluginConfig getPluginConfig() { return pluginConfig; }
    public UserManager getUserManager() { return userManager; }
    public ConsoleLogHandler getConsoleLogHandler() { return consoleLogHandler; }
    public AuditLog getAuditLog() { return auditLog; }
    public ServerStats getServerStats() { return serverStats; }
}
