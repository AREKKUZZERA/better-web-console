package dev.webconsole;

import dev.webconsole.auth.UserManager;
import dev.webconsole.config.PluginConfig;
import dev.webconsole.console.ConsoleLogHandler;
import dev.webconsole.web.WebServer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.logging.Logger;

public class BetterWebConsolePlugin extends JavaPlugin {

    private static BetterWebConsolePlugin instance;
    private PluginConfig pluginConfig;
    private UserManager userManager;
    private WebServer webServer;
    private ConsoleLogHandler consoleLogHandler;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        this.pluginConfig = new PluginConfig(getConfig());

        this.userManager = new UserManager(getDataFolder());

        this.consoleLogHandler = new ConsoleLogHandler();
        installLogHandler();

        this.webServer = new WebServer(this);
        try {
            webServer.start();
            getLogger().info("Better-WebConsole started on port " + pluginConfig.getPort());
        } catch (Exception e) {
            getLogger().severe("Failed to start Better-WebConsole web server: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (webServer != null) {
            try {
                webServer.stop();
            } catch (Exception e) {
                getLogger().warning("Error stopping web server: " + e.getMessage());
            }
        }
        uninstallLogHandler();
        getLogger().info("Better-WebConsole disabled.");
    }

    private void installLogHandler() {
        Logger rootLogger = Logger.getLogger("");
        rootLogger.addHandler(consoleLogHandler);
    }

    private void uninstallLogHandler() {
        if (consoleLogHandler != null) {
            Logger rootLogger = Logger.getLogger("");
            rootLogger.removeHandler(consoleLogHandler);
            consoleLogHandler.close();
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!command.getName().equalsIgnoreCase("better-webconsole")) return false;

        if (!sender.hasPermission("webconsole.admin")) {
            sender.sendMessage("§cYou don't have permission to use this command.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        return switch (args[0].toLowerCase()) {
            case "reload" -> handleReload(sender);
            case "status" -> handleStatus(sender);
            case "adduser" -> handleAddUser(sender, args);
            case "removeuser" -> handleRemoveUser(sender, args);
            case "listusers" -> handleListUsers(sender);
            default -> {
                sendHelp(sender);
                yield true;
            }
        };
    }

    private boolean handleReload(CommandSender sender) {
        reloadConfig();
        this.pluginConfig = new PluginConfig(getConfig());
        sender.sendMessage("§aBetter-WebConsole configuration reloaded.");
        sender.sendMessage("§eNote: Restart the server to apply port/address changes.");
        return true;
    }

    private boolean handleStatus(CommandSender sender) {
        sender.sendMessage("§6=== Better-WebConsole Status ===");
        sender.sendMessage("§7Web Server: " + (webServer != null && webServer.isRunning() ? "§aRunning" : "§cStopped"));
        sender.sendMessage("§7Port: §f" + pluginConfig.getPort());
        sender.sendMessage("§7Bind: §f" + pluginConfig.getBindAddress());
        sender.sendMessage("§7Active sessions: §f" + (webServer != null ? webServer.getActiveSessionCount() : 0));
        sender.sendMessage("§7Registered users: §f" + userManager.getUserCount());
        return true;
    }

    private boolean handleAddUser(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /better-webconsole adduser <username> <password>");
            return true;
        }
        String username = args[1];
        String password = args[2];

        if (username.length() < 3 || username.length() > 32) {
            sender.sendMessage("§cUsername must be 3-32 characters.");
            return true;
        }
        if (!username.matches("[a-zA-Z0-9_-]+")) {
            sender.sendMessage("§cUsername can only contain letters, numbers, underscores and hyphens.");
            return true;
        }
        if (password.length() < 8) {
            sender.sendMessage("§cPassword must be at least 8 characters.");
            return true;
        }

        if (userManager.userExists(username)) {
            sender.sendMessage("§cUser '" + username + "' already exists.");
            return true;
        }

        userManager.addUser(username, password);
        sender.sendMessage("§aUser '" + username + "' added successfully.");
        if (pluginConfig.isLogAuth()) {
            getLogger().info("[AUTH] User '" + username + "' created by " + sender.getName());
        }
        return true;
    }

    private boolean handleRemoveUser(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /better-webconsole removeuser <username>");
            return true;
        }
        String username = args[1];
        if (userManager.removeUser(username)) {
            sender.sendMessage("§aUser '" + username + "' removed.");
            if (pluginConfig.isLogAuth()) {
                getLogger().info("[AUTH] User '" + username + "' removed by " + sender.getName());
            }
        } else {
            sender.sendMessage("§cUser '" + username + "' not found.");
        }
        return true;
    }

    private boolean handleListUsers(CommandSender sender) {
        List<String> users = userManager.listUsers();
        if (users.isEmpty()) {
            sender.sendMessage("§eNo users registered. Add one with /better-webconsole adduser.");
        } else {
            sender.sendMessage("§6Registered Better-WebConsole users:");
            users.forEach(u -> sender.sendMessage("§7 - §f" + u));
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6=== Better-WebConsole Commands ===");
        sender.sendMessage("§e/better-webconsole status §7- Show server status");
        sender.sendMessage("§e/better-webconsole reload §7- Reload configuration");
        sender.sendMessage("§e/better-webconsole adduser <user> <pass> §7- Add a web user");
        sender.sendMessage("§e/better-webconsole removeuser <user> §7- Remove a web user");
        sender.sendMessage("§e/better-webconsole listusers §7- List all web users");
    }

    public static BetterWebConsolePlugin getInstance() { return instance; }
    public PluginConfig getPluginConfig() { return pluginConfig; }
    public UserManager getUserManager() { return userManager; }
    public ConsoleLogHandler getConsoleLogHandler() { return consoleLogHandler; }
}
