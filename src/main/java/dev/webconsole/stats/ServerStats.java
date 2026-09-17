package dev.webconsole.stats;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.webconsole.BetterWebConsolePlugin;
import dev.webconsole.config.PluginConfig;
import dev.webconsole.web.WebSocketHandler;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
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
import java.lang.management.GarbageCollectorMXBean;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

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
    private volatile JsonArray lastPlayerActivityDays = new JsonArray();
    private volatile JsonObject lastPlayerActivitySummary = new JsonObject();
    private volatile JsonArray lastKnownPlayerList = new JsonArray();
    private volatile JsonArray lastOfflinePlayerList = new JsonArray();
    private volatile JsonObject lastSnapshot = new JsonObject();
    private volatile WebSocketHandler wsHandler;
    private final long startTimeMs = System.currentTimeMillis();

    private BukkitTask task;
    private String lastBroadcastSignature = "";
    private long lastBroadcastAt = 0L;
    private long lastSystemStatsAt = 0L;
    private long lastWorldStatsAt = 0L;
    private long lastPlayerActivityPayloadAt = 0L;
    private long lastOfflinePlayerPayloadAt = 0L;
    private long lastHistoryRecordAt = 0L;
    private long lastProcessCpuTimeNs = -1L;
    private long lastProcessCpuWallNs = -1L;
    private double lastCpuLoad = 0.0;
    private final Map<UUID, JsonObject> luckPermsCache = new ConcurrentHashMap<>();
    private final Set<UUID> luckPermsLoading = ConcurrentHashMap.newKeySet();

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
        if (player == null) {
            OfflinePlayer offline = findOfflinePlayer(nameOrUuid);
            if (offline == null) return obj;
            String uuid = offline.getUniqueId().toString();
            String name = offline.getName() == null ? nameOrUuid : offline.getName();
            obj.addProperty("name", name);
            obj.addProperty("uuid", uuid);
            obj.addProperty("lastSeen", offline.getLastSeen());
            obj.addProperty("op", offline.isOp());
            addLuckPermsStatus(obj, offline.getUniqueId(), name);
            if (plugin.getPlayerActivityStore() != null) {
                obj.add("history", plugin.getPlayerActivityStore().playerHistoryJson(uuid, name, 80));
            } else {
                obj.add("history", new JsonArray());
            }
            return obj;
        }

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
        addLuckPermsStatus(obj, player.getUniqueId(), player.getName());
        if (plugin.getPlayerActivityStore() != null) {
            obj.add("history", plugin.getPlayerActivityStore().playerHistoryJson(player.getUniqueId().toString(), player.getName(), 80));
        } else {
            obj.add("history", new JsonArray());
        }
        return obj;
    }

    public JsonObject offlinePlayersJson(String query, int limit, int offset) {
        Set<String> onlineUuids = new HashSet<>();
        Set<String> onlineNames = new HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            onlineUuids.add(player.getUniqueId().toString().toLowerCase(Locale.ROOT));
            onlineNames.add(player.getName().toLowerCase(Locale.ROOT));
        }

        if (plugin.getPlayerActivityStore() != null) {
            refreshPlayerActivityPayloadIfDue(onlineUuids, onlineNames);
            refreshOfflinePlayerPayloadIfDue(onlineUuids, onlineNames);
        }

        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        int safeLimit = Math.max(1, Math.min(plugin.getPluginConfig().getOfflinePlayersMaxApiLimit(), limit));
        int safeOffset = Math.max(0, offset);
        JsonArray allOffline = currentOfflinePlayerList(onlineUuids, onlineNames);
        JsonArray players = new JsonArray();
        int matched = 0;

        for (int i = 0; i < allOffline.size(); i++) {
            JsonObject player = allOffline.get(i).getAsJsonObject();
            if (!matchesPlayerQuery(player, normalizedQuery)) continue;
            if (matched >= safeOffset && players.size() < safeLimit) players.add(player);
            matched++;
        }

        JsonObject obj = new JsonObject();
        obj.add("players", players);
        obj.addProperty("total", matched);
        obj.addProperty("limit", safeLimit);
        obj.addProperty("offset", safeOffset);
        obj.addProperty("hasMore", safeOffset + players.size() < matched);
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

    private OfflinePlayer findOfflinePlayer(String nameOrUuid) {
        if (nameOrUuid == null || nameOrUuid.isBlank()) return null;
        String needle = nameOrUuid.trim();
        for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
            String name = player.getName();
            if ((name != null && name.equalsIgnoreCase(needle)) || player.getUniqueId().toString().equalsIgnoreCase(needle)) {
                return player;
            }
        }
        UUID uuid = parseUuid(needle);
        return uuid == null ? null : Bukkit.getOfflinePlayer(uuid);
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
        collectCpuSample();
        collectSystemStatsIfDue();
        long now = System.currentTimeMillis();
        if (plugin.getServerStatsHistoryStore() != null
                && now - lastHistoryRecordAt >= plugin.getPluginConfig().getStatsHistoryWriteIntervalSeconds() * 1000L) {
            lastHistoryRecordAt = now;
            plugin.getServerStatsHistoryStore().record(System.currentTimeMillis(), lastTps, lastRamUsed, lastPlayers,
                    lastCpuLoad, lastWorlds, lastTotalChunks, lastTotalEntities);
        }

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
        cpu.addProperty("effectiveLoadPercent", systemCpuLoad > 0.0 ? systemCpuLoad : processCpuLoad);
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
        jvm.addProperty("heapCommittedBytes", heap.getCommitted());
        jvm.addProperty("heapUsedPercent", percent(heap.getMax() <= 0 ? 0.0 : (double) heap.getUsed() / heap.getMax()));
        jvm.addProperty("nonHeapUsedBytes", nonHeap.getUsed());
        jvm.addProperty("uptimeSeconds", ManagementFactory.getRuntimeMXBean().getUptime() / 1000L);
        jvm.addProperty("threads", threadBean.getThreadCount());
        jvm.addProperty("daemonThreads", threadBean.getDaemonThreadCount());
        long gcCollections = 0L;
        long gcCollectionTimeMs = 0L;
        for (GarbageCollectorMXBean collector : ManagementFactory.getGarbageCollectorMXBeans()) {
            if (collector.getCollectionCount() >= 0) gcCollections += collector.getCollectionCount();
            if (collector.getCollectionTime() >= 0) gcCollectionTimeMs += collector.getCollectionTime();
        }
        jvm.addProperty("gcCollections", gcCollections);
        jvm.addProperty("gcCollectionTimeMs", gcCollectionTimeMs);
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

    private void collectCpuSample() {
        PluginConfig config = plugin.getPluginConfig();
        if (!config.isSystemStatsEnabled()) {
            lastCpuLoad = 0.0;
            addToHistory(cpuHistory, lastCpuLoad);
            return;
        }
        try {
            java.lang.management.OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
            int processors = Math.max(1, osBean.getAvailableProcessors());
            double systemLoad = systemCpuLoad(osBean);
            double processLoad = processCpuLoad(osBean, processors);
            lastCpuLoad = systemLoad > 0.0 ? systemLoad : processLoad;
        } catch (Throwable ignored) {
            lastCpuLoad = 0.0;
        }
        addToHistory(cpuHistory, lastCpuLoad);
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

        obj.add("system", lastSystemStats.deepCopy());
        obj.add("statsHistory", liveStatsHistoryJson());

        JsonArray players = new JsonArray();
        Collection<? extends Player> online = Bukkit.getOnlinePlayers();
        Set<String> onlineUuids = new HashSet<>();
        Set<String> onlineNames = new HashSet<>();
        for (Player p : online) {
            onlineUuids.add(p.getUniqueId().toString().toLowerCase(Locale.ROOT));
            onlineNames.add(p.getName().toLowerCase(Locale.ROOT));
            JsonObject pobj = new JsonObject();
            pobj.addProperty("name", p.getName());
            pobj.addProperty("uuid", p.getUniqueId().toString());
            pobj.addProperty("world", p.getWorld().getName());
            pobj.addProperty("ping", p.getPing());
            pobj.addProperty("op", p.isOp());
            addLuckPermsStatus(pobj, p.getUniqueId(), p.getName());
            players.add(pobj);
        }
        obj.add("playerList", players);

        if (plugin.getPlayerActivityStore() != null) {
            obj.add("playerEvents", plugin.getPlayerActivityStore().recentEventsJson());
            obj.add("playerCommands", plugin.getPlayerActivityStore().recentCommandsJson());
            refreshPlayerActivityPayloadIfDue(onlineUuids, onlineNames);
            obj.add("playerActivityDays", lastPlayerActivityDays.deepCopy());
            obj.add("playerActivitySummary", lastPlayerActivitySummary.deepCopy());
        } else {
            obj.add("playerEvents", new JsonArray());
            obj.add("playerCommands", new JsonArray());
            obj.add("playerActivityDays", new JsonArray());
            obj.add("playerActivitySummary", new JsonObject());
        }

        return obj;
    }

    private JsonObject liveStatsHistoryJson() {
        JsonObject history = new JsonObject();
        JsonArray timestamps = new JsonArray();
        JsonArray tps = new JsonArray();
        JsonArray ram = new JsonArray();
        JsonArray players = new JsonArray();
        JsonArray cpu = new JsonArray();
        timestamps.add(System.currentTimeMillis());
        tps.add(Math.round(lastTps * 10.0) / 10.0);
        ram.add(lastRamUsed);
        players.add(lastPlayers);
        cpu.add(Math.round(lastCpuLoad * 10.0) / 10.0);
        history.add("timestamps", timestamps);
        history.add("tps", tps);
        history.add("ram", ram);
        history.add("players", players);
        history.add("cpu", cpu);
        return history;
    }

    private void refreshPlayerActivityPayloadIfDue(Set<String> onlineUuids, Set<String> onlineNames) {
        long now = System.currentTimeMillis();
        if (now - lastPlayerActivityPayloadAt < plugin.getPluginConfig().getPlayerActivityCacheSeconds() * 1000L) return;
        lastPlayerActivityPayloadAt = now;

        JsonArray knownPlayers = plugin.getPlayerActivityStore().knownPlayersJson(onlineUuids, onlineNames);
        for (int i = 0; i < knownPlayers.size(); i++) {
            JsonObject known = knownPlayers.get(i).getAsJsonObject();
            addLuckPermsStatus(known, parseUuid(known.has("uuid") ? known.get("uuid").getAsString() : ""), known.has("name") ? known.get("name").getAsString() : "");
        }

        lastPlayerActivityDays = plugin.getPlayerActivityStore().groupedActivityJson();
        lastPlayerActivitySummary = plugin.getPlayerActivityStore().summaryJson();
        lastKnownPlayerList = knownPlayers;
    }

    private void refreshOfflinePlayerPayloadIfDue(Set<String> onlineUuids, Set<String> onlineNames) {
        long now = System.currentTimeMillis();
        if (now - lastOfflinePlayerPayloadAt < plugin.getPluginConfig().getOfflinePlayersCacheSeconds() * 1000L) return;
        lastOfflinePlayerPayloadAt = now;

        JsonArray offlinePlayers = new JsonArray();
        JsonArray knownPlayers = lastKnownPlayerList.deepCopy();
        Set<String> knownKeys = new HashSet<>();
        for (int i = 0; i < knownPlayers.size(); i++) {
            JsonObject known = knownPlayers.get(i).getAsJsonObject();
            addKnownKeys(knownKeys, known);
            if (!known.has("online") || !known.get("online").getAsBoolean()) offlinePlayers.add(known);
        }

        for (OfflinePlayer offline : Bukkit.getOfflinePlayers()) {
            String name = offline.getName();
            String uuid = offline.getUniqueId().toString();
            if (onlineUuids.contains(uuid.toLowerCase(Locale.ROOT))
                    || (name != null && onlineNames.contains(name.toLowerCase(Locale.ROOT)))) continue;
            String key = !uuid.isBlank() ? "uuid:" + uuid.toLowerCase(Locale.ROOT)
                    : "name:" + (name == null ? "" : name.toLowerCase(Locale.ROOT));
            if (!knownKeys.add(key)) continue;
            JsonObject objPlayer = new JsonObject();
            objPlayer.addProperty("name", name == null || name.isBlank() ? uuid : name);
            objPlayer.addProperty("uuid", uuid);
            objPlayer.addProperty("online", false);
            objPlayer.addProperty("lastSeen", offline.getLastSeen());
            objPlayer.addProperty("op", offline.isOp());
            addLuckPermsStatus(objPlayer, offline.getUniqueId(), name);
            knownPlayers.add(objPlayer);
            offlinePlayers.add(objPlayer);
        }

        lastKnownPlayerList = knownPlayers;
        lastOfflinePlayerList = offlinePlayers;
    }

    private boolean matchesPlayerQuery(JsonObject player, String query) {
        if (query == null || query.isBlank()) return true;
        String name = player.has("name") ? player.get("name").getAsString().toLowerCase(Locale.ROOT) : "";
        String uuid = player.has("uuid") ? player.get("uuid").getAsString().toLowerCase(Locale.ROOT) : "";
        String group = player.has("primaryGroup") ? player.get("primaryGroup").getAsString().toLowerCase(Locale.ROOT) : "";
        return name.contains(query) || uuid.contains(query) || group.contains(query);
    }

    private JsonArray currentOfflinePlayerList(Set<String> onlineUuids, Set<String> onlineNames) {
        JsonArray filtered = new JsonArray();
        JsonArray cached = lastOfflinePlayerList;
        for (int i = 0; i < cached.size(); i++) {
            JsonObject player = cached.get(i).getAsJsonObject();
            String uuid = player.has("uuid") ? player.get("uuid").getAsString() : "";
            String name = player.has("name") ? player.get("name").getAsString() : "";
            if (!uuid.isBlank() && onlineUuids.contains(uuid.toLowerCase(Locale.ROOT))) continue;
            if (!name.isBlank() && onlineNames.contains(name.toLowerCase(Locale.ROOT))) continue;
            filtered.add(player.deepCopy());
        }
        return filtered;
    }

    private UUID parseUuid(String value) {
        try {
            return value == null || value.isBlank() ? null : UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void addKnownKeys(Set<String> knownKeys, JsonObject player) {
        if (player.has("uuid") && !player.get("uuid").getAsString().isBlank()) {
            knownKeys.add("uuid:" + player.get("uuid").getAsString().toLowerCase(Locale.ROOT));
        }
        if (player.has("name") && !player.get("name").getAsString().isBlank()) {
            knownKeys.add("name:" + player.get("name").getAsString().toLowerCase(Locale.ROOT));
        }
    }

    private void addLuckPermsStatus(JsonObject obj, UUID uuid, String playerName) {
        JsonObject status = luckPermsStatus(uuid, playerName);
        obj.add("status", status);
        if (status.has("primaryGroup")) obj.addProperty("primaryGroup", status.get("primaryGroup").getAsString());
        if (status.has("prefix")) obj.addProperty("prefix", status.get("prefix").getAsString());
    }

    private JsonObject luckPermsStatus(UUID uuid, String playerName) {
        JsonObject obj = new JsonObject();
        obj.addProperty("source", "none");
        obj.addProperty("primaryGroup", "");
        obj.addProperty("prefix", "");
        if (uuid != null) {
            JsonObject cached = luckPermsCache.get(uuid);
            if (cached != null) return cached.deepCopy();
        }
        try {
            if (Bukkit.getPluginManager().getPlugin("LuckPerms") == null) return obj;
            Class<?> luckPermsClass = Class.forName("net.luckperms.api.LuckPerms");
            Object provider = Bukkit.getServicesManager().load(luckPermsClass);
            if (provider == null) return obj;
            Object userManager = provider.getClass().getMethod("getUserManager").invoke(provider);
            Object user = null;
            if (uuid != null) {
                try {
                    user = userManager.getClass().getMethod("getUser", UUID.class).invoke(userManager, uuid);
                } catch (ReflectiveOperationException ignored) {
                }
            }
            if (user == null && playerName != null && !playerName.isBlank()) {
                Player player = Bukkit.getPlayerExact(playerName);
                if (player != null) {
                    Object adapter = provider.getClass().getMethod("getPlayerAdapter", Class.class).invoke(provider, Player.class);
                    user = adapter.getClass().getMethod("getUser", Player.class).invoke(adapter, player);
                }
            }
            if (user == null) {
                loadLuckPermsUserAsync(userManager, uuid);
                obj.addProperty("source", uuid == null ? "none" : "luckperms_loading");
                return obj;
            }
            JsonObject status = statusFromLuckPermsUser(user);
            if (uuid != null) luckPermsCache.put(uuid, status.deepCopy());
            return status;
        } catch (Throwable ignored) {
            obj.addProperty("source", "error");
        }
        return obj;
    }

    private void loadLuckPermsUserAsync(Object userManager, UUID uuid) {
        if (uuid == null || !luckPermsLoading.add(uuid)) return;
        try {
            Object future = userManager.getClass().getMethod("loadUser", UUID.class).invoke(userManager, uuid);
            if (future instanceof CompletableFuture<?> completable) {
                completable.whenComplete((user, error) -> {
                    try {
                        if (error == null && user != null) luckPermsCache.put(uuid, statusFromLuckPermsUser(user));
                    } finally {
                        luckPermsLoading.remove(uuid);
                    }
                });
            } else {
                luckPermsLoading.remove(uuid);
            }
        } catch (Throwable ignored) {
            luckPermsLoading.remove(uuid);
        }
    }

    private JsonObject statusFromLuckPermsUser(Object user) {
        JsonObject obj = new JsonObject();
        obj.addProperty("source", "luckperms");
        obj.addProperty("primaryGroup", "");
        obj.addProperty("prefix", "");
        try {
            Object primaryGroup = user.getClass().getMethod("getPrimaryGroup").invoke(user);
            if (primaryGroup != null) obj.addProperty("primaryGroup", String.valueOf(primaryGroup));
            Object cachedData = user.getClass().getMethod("getCachedData").invoke(user);
            Object metaData = cachedData.getClass().getMethod("getMetaData").invoke(cachedData);
            Object prefix = metaData.getClass().getMethod("getPrefix").invoke(metaData);
            if (prefix != null) obj.addProperty("prefix", String.valueOf(prefix));
        } catch (Throwable ignored) {
            obj.addProperty("source", "error");
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
