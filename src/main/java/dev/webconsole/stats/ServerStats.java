package dev.webconsole.stats;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.webconsole.BetterWebConsolePlugin;
import dev.webconsole.config.PluginConfig;
import dev.webconsole.web.WebSocketHandler;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadMXBean;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.List;

/**
 * Collects TPS, RAM and player data every second.
 * Pushes updates only when something materially changed to keep the UI snappy without spamming the server.
 */
public class ServerStats {

    private static final int HISTORY_POINTS = 300; // 300 x 1s = 5 min
    private static final long WORLD_STATS_INTERVAL_MS = 3000L;

    private final BetterWebConsolePlugin plugin;
    private final MemoryMXBean memBean = ManagementFactory.getMemoryMXBean();
    private final ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();

    private final Deque<Double> tpsHistory = new ArrayDeque<>(HISTORY_POINTS);
    private final Deque<Long> ramHistory = new ArrayDeque<>(HISTORY_POINTS);
    private final Deque<Integer> playersHistory = new ArrayDeque<>(HISTORY_POINTS);
    private final Deque<Double> cpuHistory = new ArrayDeque<>(HISTORY_POINTS);

    private volatile double lastTps = 20.0;
    private volatile long lastRamUsed = 0;
    private volatile long maxRam = 0;
    private volatile JsonObject lastSystemStats = new JsonObject();
    private volatile int lastPlayers = 0;
    private volatile int lastWorlds = 0;
    private volatile int lastTotalChunks = 0;
    private volatile int lastTotalEntities = 0;
    private volatile JsonArray lastWorldStats = new JsonArray();
    private volatile JsonObject lastSnapshot = new JsonObject();
    private volatile WebSocketHandler wsHandler;
    private final long startTimeMs = System.currentTimeMillis();

    private BukkitTask task;
    private String lastBroadcastSignature = "";
    private long lastBroadcastAt = 0L;
    private long lastSystemStatsAt = 0L;
    private long lastWorldStatsAt = 0L;
    private long lastProcessCpuTimeNs = -1L;
    private long lastProcessCpuWallNs = -1L;

    public ServerStats(BetterWebConsolePlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        collect();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::collect, 20L, 20L);
    }

    public void stop() {
        if (task != null) task.cancel();
    }

    public void setWebSocketHandler(WebSocketHandler wsHandler) {
        this.wsHandler = wsHandler;
    }

    public JsonObject playerProfileJson(String nameOrUuid) {
        Player player = findPlayer(nameOrUuid);
        JsonObject obj = new JsonObject();
        obj.addProperty("online", player != null);
        if (player == null) return obj;

        Location loc = player.getLocation();
        obj.addProperty("name", player.getName());
        obj.addProperty("uuid", player.getUniqueId().toString());
        obj.addProperty("world", player.getWorld().getName());
        obj.addProperty("ping", player.getPing());
        obj.addProperty("op", player.isOp());
        obj.addProperty("gamemode", player.getGameMode().name());
        obj.addProperty("health", Math.round(player.getHealth() * 10.0) / 10.0);
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        obj.addProperty("maxHealth", Math.round((maxHealth != null ? maxHealth.getValue() : player.getHealth()) * 10.0) / 10.0);
        obj.addProperty("food", player.getFoodLevel());
        obj.addProperty("level", player.getLevel());
        obj.addProperty("x", Math.round(loc.getX() * 10.0) / 10.0);
        obj.addProperty("y", Math.round(loc.getY() * 10.0) / 10.0);
        obj.addProperty("z", Math.round(loc.getZ() * 10.0) / 10.0);
        if (plugin.getPlayerActivityStore() != null) {
            obj.add("history", plugin.getPlayerActivityStore().playerHistoryJson(player.getUniqueId().toString(), player.getName(), 80));
        } else {
            obj.add("history", new JsonArray());
        }
        return obj;
    }

    private Player findPlayer(String nameOrUuid) {
        if (nameOrUuid == null || nameOrUuid.isBlank()) return null;
        String needle = nameOrUuid.trim();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().equalsIgnoreCase(needle) || player.getUniqueId().toString().equalsIgnoreCase(needle)) {
                return player;
            }
        }
        return null;
    }

    private void collect() {
        double[] tpsArr = Bukkit.getTPS();
        lastTps = tpsArr.length > 0 ? Math.min(20.0, tpsArr[0]) : 20.0;

        long used = memBean.getHeapMemoryUsage().getUsed() / (1024 * 1024);
        long max = memBean.getHeapMemoryUsage().getMax() / (1024 * 1024);
        lastRamUsed = used;
        maxRam = max;

        lastPlayers = Bukkit.getOnlinePlayers().size();
        collectWorldStatsIfDue();

        addToHistory(tpsHistory, lastTps);
        addToHistory(ramHistory, lastRamUsed);
        addToHistory(playersHistory, lastPlayers);
        collectSystemStatsIfDue();

        JsonObject snapshot = buildSnapshotJson();
        lastSnapshot = snapshot;
        maybeBroadcast(snapshot);
    }

    private void collectWorldStatsIfDue() {
        long now = System.currentTimeMillis();
        if (now - lastWorldStatsAt < WORLD_STATS_INTERVAL_MS) return;
        lastWorldStatsAt = now;

        List<World> worlds = Bukkit.getWorlds();
        int totalChunks = 0;
        int totalEntities = 0;
        JsonArray worldStats = new JsonArray();
        for (World world : worlds) {
            int chunks = world.getLoadedChunks().length;
            int entities = world.getEntities().size();
            totalChunks += chunks;
            totalEntities += entities;

            JsonObject wobj = new JsonObject();
            wobj.addProperty("name", world.getName());
            wobj.addProperty("entities", entities);
            wobj.addProperty("chunks", chunks);
            wobj.addProperty("environment", world.getEnvironment().name());
            worldStats.add(wobj);
        }

        lastWorlds = worlds.size();
        lastTotalChunks = totalChunks;
        lastTotalEntities = totalEntities;
        lastWorldStats = worldStats;
    }

    private void collectSystemStatsIfDue() {
        PluginConfig config = plugin.getPluginConfig();
        if (!config.isSystemStatsEnabled()) {
            lastSystemStats = new JsonObject();
            return;
        }

        long now = System.currentTimeMillis();
        long intervalMs = config.getSystemStatsUpdateIntervalSeconds() * 1000L;
        if (now - lastSystemStatsAt < intervalMs) return;
        lastSystemStatsAt = now;

        try {
            lastSystemStats = buildSystemStats(config);
            if (lastSystemStats.has("cpu")) {
                JsonObject cpu = lastSystemStats.getAsJsonObject("cpu");
                if (cpu.has("systemLoadPercent")) addToHistory(cpuHistory, cpu.get("systemLoadPercent").getAsDouble());
            }
        } catch (Throwable e) {
            JsonObject error = new JsonObject();
            error.addProperty("enabled", false);
            error.addProperty("error", e.getMessage());
            lastSystemStats = error;
        }
    }

    private JsonObject buildSystemStats(PluginConfig config) {
        JsonObject system = new JsonObject();
        system.addProperty("enabled", true);

        java.lang.management.OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        int processors = Math.max(1, osBean.getAvailableProcessors());
        double systemCpuLoad = systemCpuLoad(osBean);
        double processCpuLoad = processCpuLoad(osBean, processors);

        JsonObject cpu = new JsonObject();
        cpu.addProperty("model", System.getenv().getOrDefault("PROCESSOR_IDENTIFIER", System.getProperty("os.arch", "unknown")));
        cpu.addProperty("physicalCores", processors);
        cpu.addProperty("logicalCores", processors);
        cpu.addProperty("systemLoadPercent", systemCpuLoad);
        cpu.addProperty("processLoadPercent", processCpuLoad);
        system.add("cpu", cpu);

        long totalMemory = totalPhysicalMemory(osBean);
        long availableMemory = freePhysicalMemory(osBean);
        JsonObject memoryJson = new JsonObject();
        memoryJson.addProperty("totalBytes", totalMemory);
        memoryJson.addProperty("availableBytes", availableMemory);
        memoryJson.addProperty("usedBytes", Math.max(0L, totalMemory - availableMemory));
        memoryJson.addProperty("usedPercent", percent(totalMemory <= 0 ? 0.0 : (double) (totalMemory - availableMemory) / totalMemory));
        system.add("memory", memoryJson);

        MemoryUsage heap = memBean.getHeapMemoryUsage();
        MemoryUsage nonHeap = memBean.getNonHeapMemoryUsage();
        JsonObject jvm = new JsonObject();
        jvm.addProperty("heapUsedBytes", heap.getUsed());
        jvm.addProperty("heapMaxBytes", heap.getMax());
        jvm.addProperty("nonHeapUsedBytes", nonHeap.getUsed());
        jvm.addProperty("uptimeSeconds", ManagementFactory.getRuntimeMXBean().getUptime() / 1000L);
        jvm.addProperty("threads", threadBean.getThreadCount());
        jvm.addProperty("daemonThreads", threadBean.getDaemonThreadCount());
        jvm.addProperty("pid", ProcessHandle.current().pid());
        jvm.addProperty("javaVersion", System.getProperty("java.version", "unknown"));
        jvm.addProperty("javaVendor", System.getProperty("java.vendor", "unknown"));
        system.add("jvm", jvm);

        JsonObject os = new JsonObject();
        os.addProperty("family", System.getProperty("os.name", "unknown"));
        os.addProperty("version", System.getProperty("os.version", "unknown"));
        os.addProperty("arch", System.getProperty("os.arch", "unknown"));
        os.addProperty("bitness", System.getProperty("sun.arch.data.model", "unknown"));
        os.addProperty("uptimeSeconds", ManagementFactory.getRuntimeMXBean().getUptime() / 1000L);
        system.add("os", os);

        if (config.isShowDiskStats()) {
            system.add("disk", buildDiskStats());
        }

        return system;
    }

    private JsonObject buildDiskStats() {
        Path serverPath = plugin.getServer().getWorldContainer().toPath().toAbsolutePath().normalize();
        File root = serverPath.toFile();
        Path rootPath = serverPath.getRoot();
        if (rootPath != null) root = rootPath.toFile();
        long bestTotal = root.getTotalSpace();
        long bestUsable = root.getUsableSpace();
        String bestMount = rootPath != null ? rootPath.toString() : serverPath.toString();

        JsonObject disk = new JsonObject();
        long used = Math.max(0L, bestTotal - bestUsable);
        disk.addProperty("path", serverPath.toString());
        disk.addProperty("mount", bestMount);
        disk.addProperty("totalBytes", bestTotal);
        disk.addProperty("usableBytes", bestUsable);
        disk.addProperty("usedBytes", used);
        disk.addProperty("usedPercent", percent(bestTotal <= 0 ? 0.0 : (double) used / bestTotal));
        return disk;
    }

    private void maybeBroadcast(JsonObject snapshot) {
        WebSocketHandler handler = wsHandler;
        if (handler == null || handler.getConnectionCount() == 0) return;

        long now = System.currentTimeMillis();
        String signature = Math.round(lastTps * 10.0) + ":" + lastRamUsed + ":" + lastPlayers + ":" + lastWorlds + ":" + lastTotalChunks + ":" + lastTotalEntities + ":" + lastSystemStats.hashCode();
        if (!signature.equals(lastBroadcastSignature) || now - lastBroadcastAt >= 3000L) {
            lastBroadcastSignature = signature;
            lastBroadcastAt = now;
            handler.broadcastStats(snapshot.deepCopy());
        }
    }

    private <T> void addToHistory(Deque<T> deque, T value) {
        if (deque.size() >= HISTORY_POINTS) deque.pollFirst();
        deque.addLast(value);
    }

    public JsonObject toJson() {
        return lastSnapshot.deepCopy();
    }

    private JsonObject buildSnapshotJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("tps", Math.round(lastTps * 10.0) / 10.0);
        obj.addProperty("ramUsed", lastRamUsed);
        obj.addProperty("ramMax", maxRam);
        obj.addProperty("players", lastPlayers);
        obj.addProperty("maxPlayers", Bukkit.getMaxPlayers());
        // Server uptime in seconds since plugin enable
        obj.addProperty("uptimeSeconds", (System.currentTimeMillis() - startTimeMs) / 1000L);

        obj.add("worlds", lastWorldStats.deepCopy());

        obj.add("tpsHistory", toJsonArray(tpsHistory));
        obj.add("ramHistory", toJsonArray(ramHistory));
        obj.add("playersHistory", toJsonArray(playersHistory));
        obj.add("cpuHistory", toJsonArray(cpuHistory));
        obj.add("system", lastSystemStats.deepCopy());

        JsonArray players = new JsonArray();
        Collection<? extends Player> online = Bukkit.getOnlinePlayers();
        for (Player p : online) {
            JsonObject pobj = new JsonObject();
            pobj.addProperty("name", p.getName());
            pobj.addProperty("uuid", p.getUniqueId().toString());
            pobj.addProperty("world", p.getWorld().getName());
            pobj.addProperty("ping", p.getPing());
            pobj.addProperty("op", p.isOp());
            players.add(pobj);
        }
        obj.add("playerList", players);

        if (plugin.getPlayerActivityStore() != null) {
            obj.add("playerEvents", plugin.getPlayerActivityStore().recentEventsJson());
            obj.add("playerCommands", plugin.getPlayerActivityStore().recentCommandsJson());
            obj.add("playerActivityDays", plugin.getPlayerActivityStore().groupedActivityJson());
            obj.add("playerActivitySummary", plugin.getPlayerActivityStore().summaryJson());
        } else {
            obj.add("playerEvents", new JsonArray());
            obj.add("playerCommands", new JsonArray());
            obj.add("playerActivityDays", new JsonArray());
            obj.add("playerActivitySummary", new JsonObject());
        }

        return obj;
    }

    private JsonArray toJsonArray(Deque<?> deque) {
        JsonArray arr = new JsonArray();
        for (Object v : deque) {
            if (v instanceof Double d) arr.add(Math.round(d * 10.0) / 10.0);
            else if (v instanceof Long l) arr.add(l);
            else if (v instanceof Integer i) arr.add(i);
        }
        return arr;
    }

    private double percent(double ratio) {
        if (Double.isNaN(ratio) || Double.isInfinite(ratio)) return 0.0;
        return Math.max(0.0, Math.min(100.0, Math.round(ratio * 1000.0) / 10.0));
    }

    private double systemCpuLoad(java.lang.management.OperatingSystemMXBean osBean) {
        if (osBean instanceof com.sun.management.OperatingSystemMXBean sunOsBean) {
            double load = sunOsBean.getCpuLoad();
            return load < 0 ? 0.0 : percent(load);
        }

        double average = osBean.getSystemLoadAverage();
        return average < 0 ? 0.0 : percent(average / Math.max(1, osBean.getAvailableProcessors()));
    }

    private double processCpuLoad(java.lang.management.OperatingSystemMXBean osBean, int processors) {
        if (osBean instanceof com.sun.management.OperatingSystemMXBean sunOsBean) {
            double load = sunOsBean.getProcessCpuLoad();
            if (load >= 0) return percent(load);

            long cpuTimeNs = sunOsBean.getProcessCpuTime();
            long wallNs = System.nanoTime();
            if (lastProcessCpuTimeNs >= 0 && lastProcessCpuWallNs >= 0 && wallNs > lastProcessCpuWallNs) {
                double ratio = (double) (cpuTimeNs - lastProcessCpuTimeNs) / (double) (wallNs - lastProcessCpuWallNs) / processors;
                lastProcessCpuTimeNs = cpuTimeNs;
                lastProcessCpuWallNs = wallNs;
                return percent(ratio);
            }
            lastProcessCpuTimeNs = cpuTimeNs;
            lastProcessCpuWallNs = wallNs;
        }
        return 0.0;
    }

    private long totalPhysicalMemory(java.lang.management.OperatingSystemMXBean osBean) {
        if (osBean instanceof com.sun.management.OperatingSystemMXBean sunOsBean) {
            return Math.max(0L, sunOsBean.getTotalMemorySize());
        }
        return 0L;
    }

    private long freePhysicalMemory(java.lang.management.OperatingSystemMXBean osBean) {
        if (osBean instanceof com.sun.management.OperatingSystemMXBean sunOsBean) {
            return Math.max(0L, sunOsBean.getFreeMemorySize());
        }
        return 0L;
    }
}
