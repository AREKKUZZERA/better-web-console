package dev.webconsole.stats;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.webconsole.BetterWebConsolePlugin;
import dev.webconsole.web.WebSocketHandler;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
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

    private final Deque<Double> tpsHistory = new ArrayDeque<>(HISTORY_POINTS);
    private final Deque<Long> ramHistory = new ArrayDeque<>(HISTORY_POINTS);
    private final Deque<Integer> playersHistory = new ArrayDeque<>(HISTORY_POINTS);

    private volatile double lastTps = 20.0;
    private volatile long lastRamUsed = 0;
    private volatile long maxRam = 0;
    private volatile int lastPlayers = 0;
    private volatile int lastWorlds = 0;
    private volatile int lastTotalChunks = 0;
    private volatile WebSocketHandler wsHandler;

    private BukkitTask task;
    private String lastBroadcastSignature = "";
    private long lastBroadcastAt = 0L;

    public ServerStats(BetterWebConsolePlugin plugin) {
        this.plugin = plugin;
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

        maybeBroadcast();
    }

    private void maybeBroadcast() {
        WebSocketHandler handler = wsHandler;
        if (handler == null || handler.getConnectionCount() == 0) return;

        long now = System.currentTimeMillis();
        String signature = Math.round(lastTps * 10.0) + ":" + lastRamUsed + ":" + lastPlayers + ":" + lastWorlds + ":" + lastTotalChunks;
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

        JsonArray worlds = new JsonArray();
        for (World w : Bukkit.getWorlds()) {
            JsonObject wobj = new JsonObject();
            wobj.addProperty("name", w.getName());
            wobj.addProperty("entities", w.getEntities().size());
            wobj.addProperty("chunks", w.getLoadedChunks().length);
            worlds.add(wobj);
        }
        obj.add("worlds", worlds);

        obj.add("tpsHistory", toJsonArray(tpsHistory));
        obj.add("ramHistory", toJsonArray(ramHistory));
        obj.add("playersHistory", toJsonArray(playersHistory));

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
}
