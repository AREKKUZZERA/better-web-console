package dev.webconsole.stats;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.webconsole.BetterWebConsolePlugin;
import dev.webconsole.config.PluginConfig;
import dev.webconsole.web.WebSocketHandler;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.software.os.OSFileStore;
import oshi.software.os.OperatingSystem;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadMXBean;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;

/**
 * Collects TPS, RAM and player data every second.
 * Pushes updates only when something materially changed to keep the UI snappy without spamming the server.
 */
public class ServerStats {

    private static final int HISTORY_POINTS = 300; // 300 x 1s = 5 min

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
    private volatile WebSocketHandler wsHandler;
    private final long startTimeMs = System.currentTimeMillis();

    private BukkitTask task;
    private String lastBroadcastSignature = "";
    private long lastBroadcastAt = 0L;
    private long lastSystemStatsAt = 0L;
    private long lastSystemInfoInitAttempt = 0L;
    private SystemInfo systemInfo;
    private HardwareAbstractionLayer hardware;
    private OperatingSystem operatingSystem;
    private long[] previousCpuTicks;

    public ServerStats(BetterWebConsolePlugin plugin) {
        this.plugin = plugin;
        initSystemInfo();
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::collect, 20L, 20L);
    }

    public void stop() {
        if (task != null) task.cancel();
    }

    public void setWebSocketHandler(WebSocketHandler wsHandler) {
        this.wsHandler = wsHandler;
    }

    private void collect() {
        double[] tpsArr = Bukkit.getTPS();
        lastTps = tpsArr.length > 0 ? Math.min(20.0, tpsArr[0]) : 20.0;

        long used = memBean.getHeapMemoryUsage().getUsed() / (1024 * 1024);
        long max = memBean.getHeapMemoryUsage().getMax() / (1024 * 1024);
        lastRamUsed = used;
        maxRam = max;

        lastPlayers = Bukkit.getOnlinePlayers().size();
        lastWorlds = Bukkit.getWorlds().size();
        lastTotalChunks = Bukkit.getWorlds().stream().mapToInt(w -> w.getLoadedChunks().length).sum();

        addToHistory(tpsHistory, lastTps);
        addToHistory(ramHistory, lastRamUsed);
        addToHistory(playersHistory, lastPlayers);
        collectSystemStatsIfDue();

        maybeBroadcast();
    }

    private void collectSystemStatsIfDue() {
        PluginConfig config = plugin.getPluginConfig();
        if (!config.isSystemStatsEnabled()) {
            lastSystemStats = new JsonObject();
            return;
        }

        if (!initSystemInfo()) {
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
        } catch (Exception e) {
            JsonObject error = new JsonObject();
            error.addProperty("enabled", false);
            error.addProperty("error", e.getMessage());
            lastSystemStats = error;
        }
    }

    private boolean initSystemInfo() {
        if (systemInfo != null && hardware != null && operatingSystem != null) return true;
        if (!plugin.getPluginConfig().isSystemStatsEnabled()) return false;
        long now = System.currentTimeMillis();
        if (now - lastSystemInfoInitAttempt < 60_000L) return false;
        lastSystemInfoInitAttempt = now;

        try {
            this.systemInfo = new SystemInfo();
            this.hardware = systemInfo.getHardware();
            this.operatingSystem = systemInfo.getOperatingSystem();
            this.previousCpuTicks = hardware.getProcessor().getSystemCpuLoadTicks();
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("[BWC] Failed to initialize system stats: " + e.getMessage());
            return false;
        }
    }

    private JsonObject buildSystemStats(PluginConfig config) {
        JsonObject system = new JsonObject();
        system.addProperty("enabled", true);

        CentralProcessor processor = hardware.getProcessor();
        double systemCpuLoad = processor.getSystemCpuLoadBetweenTicks(previousCpuTicks);
        previousCpuTicks = processor.getSystemCpuLoadTicks();

        double processCpuLoad = processCpuLoad();

        JsonObject cpu = new JsonObject();
        cpu.addProperty("model", processor.getProcessorIdentifier().getName());
        cpu.addProperty("physicalCores", processor.getPhysicalProcessorCount());
        cpu.addProperty("logicalCores", processor.getLogicalProcessorCount());
        cpu.addProperty("systemLoadPercent", percent(systemCpuLoad));
        cpu.addProperty("processLoadPercent", percent(processCpuLoad));
        system.add("cpu", cpu);

        GlobalMemory memory = hardware.getMemory();
        long totalMemory = memory.getTotal();
        long availableMemory = memory.getAvailable();
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
        jvm.addProperty("pid", operatingSystem.getProcessId());
        jvm.addProperty("javaVersion", System.getProperty("java.version", "unknown"));
        jvm.addProperty("javaVendor", System.getProperty("java.vendor", "unknown"));
        system.add("jvm", jvm);

        JsonObject os = new JsonObject();
        os.addProperty("family", operatingSystem.getFamily());
        os.addProperty("version", operatingSystem.getVersionInfo().toString());
        os.addProperty("arch", System.getProperty("os.arch", "unknown"));
        os.addProperty("bitness", operatingSystem.getBitness());
        os.addProperty("uptimeSeconds", operatingSystem.getSystemUptime());
        system.add("os", os);

        if (config.isShowDiskStats()) {
            system.add("disk", buildDiskStats());
        }

        return system;
    }

    private JsonObject buildDiskStats() {
        Path serverPath = plugin.getServer().getWorldContainer().toPath().toAbsolutePath().normalize();
        String bestMount = "";
        long bestTotal = 0L;
        long bestUsable = 0L;

        for (OSFileStore store : operatingSystem.getFileSystem().getFileStores()) {
            String mount = store.getMount();
            if (mount == null || mount.isBlank()) continue;
            Path mountPath;
            try {
                mountPath = Path.of(mount).toAbsolutePath().normalize();
            } catch (Exception ignored) {
                continue;
            }
            if (serverPath.startsWith(mountPath) && mount.length() >= bestMount.length()) {
                bestMount = mount;
                bestTotal = store.getTotalSpace();
                bestUsable = store.getUsableSpace();
            }
        }

        if (bestTotal <= 0L) {
            bestMount = serverPath.toString();
            bestTotal = serverPath.toFile().getTotalSpace();
            bestUsable = serverPath.toFile().getUsableSpace();
        }

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

    private void maybeBroadcast() {
        WebSocketHandler handler = wsHandler;
        if (handler == null || handler.getConnectionCount() == 0) return;

        long now = System.currentTimeMillis();
        String signature = Math.round(lastTps * 10.0) + ":" + lastRamUsed + ":" + lastPlayers + ":" + lastWorlds + ":" + lastTotalChunks + ":" + lastSystemStats.hashCode();
        if (!signature.equals(lastBroadcastSignature) || now - lastBroadcastAt >= 3000L) {
            lastBroadcastSignature = signature;
            lastBroadcastAt = now;
            handler.broadcastStats();
        }
    }

    private <T> void addToHistory(Deque<T> deque, T value) {
        if (deque.size() >= HISTORY_POINTS) deque.pollFirst();
        deque.addLast(value);
    }

    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("tps", Math.round(lastTps * 10.0) / 10.0);
        obj.addProperty("ramUsed", lastRamUsed);
        obj.addProperty("ramMax", maxRam);
        obj.addProperty("players", lastPlayers);
        obj.addProperty("maxPlayers", Bukkit.getMaxPlayers());
        // Server uptime in seconds since plugin enable
        obj.addProperty("uptimeSeconds", (System.currentTimeMillis() - startTimeMs) / 1000L);

        JsonArray worlds = new JsonArray();
        for (World w : Bukkit.getWorlds()) {
            JsonObject wobj = new JsonObject();
            wobj.addProperty("name", w.getName());
            wobj.addProperty("entities", w.getEntities().size());
            wobj.addProperty("chunks", w.getLoadedChunks().length);
            wobj.addProperty("environment", w.getEnvironment().name());
            worlds.add(wobj);
        }
        obj.add("worlds", worlds);

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

    private double processCpuLoad() {
        java.lang.management.OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        if (osBean instanceof com.sun.management.OperatingSystemMXBean sunOsBean) {
            double load = sunOsBean.getProcessCpuLoad();
            return load < 0 ? 0.0 : load;
        }
        return 0.0;
    }
}
