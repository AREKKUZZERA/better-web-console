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
        this.userManager  = new UserManager(getDataFolder());
        this.auditLog     = new AuditLog(getDataFolder());

        this.consoleLogHandler = new ConsoleLogHandler(pluginConfig.getLogBufferSize());
        Logger.getLogger("").addHandler(consoleLogHandler);

        this.serverStats = new ServerStats(this);
        this.serverStats.start();

        var cmd = getCommand("betterwebconsole");
        if (cmd != null) { cmd.setExecutor(this); cmd.setTabCompleter(this); }

        this.webServer = new WebServer(this);
        try {
            webServer.start();
            getLogger().info("Better-WebConsole v2 started on port " + pluginConfig.getPort());
        } catch (Exception e) {
            getLogger().severe("Failed to start web server: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (serverStats != null) serverStats.stop();
        if (webServer   != null) try { webServer.stop(); } catch (Exception ignored) {}
        if (auditLog    != null) auditLog.shutdown();
        Logger.getLogger("").removeHandler(consoleLogHandler);
        if (consoleLogHandler != null) consoleLogHandler.close();
        getLogger().info("Better-WebConsole disabled.");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!command.getName().equalsIgnoreCase("betterwebconsole")) return false;
        if (!sender.hasPermission("betterwebconsole.admin")) {
            sender.sendMessage("§cNo permission."); return true;
        }
        if (args.length == 0) { sendHelp(sender); return true; }
        return switch (args[0].toLowerCase()) {
            case "reload"     -> handleReload(sender);
            case "status"     -> handleStatus(sender);
            case "adduser"    -> handleAddUser(sender, args);
            case "removeuser" -> handleRemoveUser(sender, args);
            case "listusers"  -> handleListUsers(sender);
            default           -> { sendHelp(sender); yield true; }
        };
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (!command.getName().equalsIgnoreCase("betterwebconsole")) return Collections.emptyList();
        if (!sender.hasPermission("betterwebconsole.admin"))         return Collections.emptyList();
        if (args.length == 1) {
            return List.of("reload","status","adduser","removeuser","listusers").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("removeuser")) {
            return userManager.listUsers().stream()
                    .filter(u -> u.toLowerCase().startsWith(args[1].toLowerCase())).toList();
        }
        return Collections.emptyList();
    }

    private boolean handleReload(CommandSender s) {
        reloadConfig();
        pluginConfig = new PluginConfig(getConfig());
        s.sendMessage("§aBetter-WebConsole config reloaded. Restart to apply port changes.");
        return true;
    }

    private boolean handleStatus(CommandSender s) {
        s.sendMessage("§6=== Better-WebConsole Status ===");
        s.sendMessage("§7Status: " + (webServer != null && webServer.isRunning() ? "§aRunning" : "§cStopped"));
        s.sendMessage("§7Port: §f" + pluginConfig.getPort() + "  §7Bind: §f" + pluginConfig.getBindAddress());
        s.sendMessage("§7Sessions: §f" + (webServer != null ? webServer.getActiveSessionCount() : 0)
                + "  §7Users: §f" + userManager.getUserCount());
        return true;
    }

    private boolean handleAddUser(CommandSender s, String[] args) {
        if (args.length < 3) { s.sendMessage("§cUsage: /bwc adduser <user> <pass>"); return true; }
        String u = args[1], p = args[2];
        if (u.length() < 3 || u.length() > 32 || !u.matches("[a-zA-Z0-9_-]+")) {
            s.sendMessage("§cInvalid username (3-32 chars, a-z 0-9 _ -)"); return true;
        }
        if (p.length() < 8) { s.sendMessage("§cPassword must be ≥8 chars."); return true; }
        if (userManager.userExists(u)) { s.sendMessage("§cUser already exists."); return true; }
        userManager.addUser(u, p);
        s.sendMessage("§aUser '" + u + "' added.");
        if (pluginConfig.isLogAuth()) getLogger().info("[AUTH] User '" + u + "' created by " + s.getName());
        return true;
    }

    private boolean handleRemoveUser(CommandSender s, String[] args) {
        if (args.length < 2) { s.sendMessage("§cUsage: /bwc removeuser <user>"); return true; }
        if (userManager.removeUser(args[1])) {
            s.sendMessage("§aUser '" + args[1] + "' removed.");
            if (pluginConfig.isLogAuth()) getLogger().info("[AUTH] User '" + args[1] + "' removed by " + s.getName());
        } else {
            s.sendMessage("§cUser not found.");
        }
        return true;
    }

    private boolean handleListUsers(CommandSender s) {
        List<String> users = userManager.listUsers();
        if (users.isEmpty()) s.sendMessage("§eNo users. Add with /bwc adduser.");
        else { s.sendMessage("§6Users:"); users.forEach(u -> s.sendMessage("§7 - §f" + u)); }
        return true;
    }

    private void sendHelp(CommandSender s) {
        s.sendMessage("§6=== Better-WebConsole ===");
        s.sendMessage("§e/bwc status | reload | adduser <u> <p> | removeuser <u> | listusers");
    }

    public static BetterWebConsolePlugin getInstance() { return instance; }
    public PluginConfig getPluginConfig()          { return pluginConfig; }
    public UserManager getUserManager()            { return userManager; }
    public ConsoleLogHandler getConsoleLogHandler(){ return consoleLogHandler; }
    public AuditLog getAuditLog()                  { return auditLog; }
    public ServerStats getServerStats()            { return serverStats; }
}
