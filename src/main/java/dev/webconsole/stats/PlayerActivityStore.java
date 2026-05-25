package dev.webconsole.stats;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

public class PlayerActivityStore {
    private static final int MAX_STORED_ENTRIES = 10_000;
    private static final int COMPACT_INTERVAL_WRITES = 500;
    private static final int MAX_EVENT_JSON = 80;
    private static final int MAX_COMMAND_JSON = 120;
    private static final int MAX_ACTIVITY_DAYS = 30;
    private static final int MAX_ACTIVITY_JSON = 1000;
    private static final DateTimeFormatter DAY_KEY = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.ROOT);
    private static final String TYPE_COMMAND = "command";
    private static final String TYPE_JOIN = "join";
    private static final String TYPE_LEAVE = "leave";

    private final Logger logger;
    private final File file;
    private final List<Entry> entries = new ArrayList<>();
    private final Map<String, PlayerSummary> cachedPlayers = new HashMap<>();
    private int cachedJoins;
    private int cachedLeaves;
    private int cachedCommands;
    private int writesSinceCompact;

    public PlayerActivityStore(File dataFolder, Logger logger) {
        this.logger = logger;
        this.file = new File(dataFolder, "player-command-history.tsv");
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            logger.warning("[BWC] Failed to create data folder for player activity history");
        }
        load();
        if (trimOldEntries()) compact();
        rebuildSummaryCache();
    }

    public synchronized void recordJoin(UUID uuid, String playerName) {
        record(TYPE_JOIN, System.currentTimeMillis(), uuid, playerName, "");
    }

    public synchronized void recordLeave(UUID uuid, String playerName) {
        record(TYPE_LEAVE, System.currentTimeMillis(), uuid, playerName, "");
    }

    public synchronized void recordCommand(UUID uuid, String playerName, String command) {
        record(TYPE_COMMAND, System.currentTimeMillis(), uuid, playerName, command);
    }

    public synchronized JsonArray recentEventsJson() {
        return recentJson(MAX_EVENT_JSON, TYPE_JOIN, TYPE_LEAVE);
    }

    public synchronized JsonArray recentCommandsJson() {
        return recentJson(MAX_COMMAND_JSON, TYPE_COMMAND);
    }

    public synchronized JsonArray groupedActivityJson() {
        LinkedHashMap<LocalDate, List<Entry>> byDay = new LinkedHashMap<>();
        ZoneId zone = ZoneId.systemDefault();
        int included = 0;
        for (int i = entries.size() - 1; i >= 0 && included < MAX_ACTIVITY_JSON; i--) {
            Entry entry = entries.get(i);
            LocalDate day = Instant.ofEpochMilli(entry.timestamp()).atZone(zone).toLocalDate();
            if (!byDay.containsKey(day) && byDay.size() >= MAX_ACTIVITY_DAYS) continue;
            byDay.computeIfAbsent(day, ignored -> new ArrayList<>()).add(entry);
            included++;
        }

        JsonArray days = new JsonArray();
        for (Map.Entry<LocalDate, List<Entry>> dayEntry : byDay.entrySet()) {
            JsonObject dayJson = new JsonObject();
            dayJson.addProperty("date", DAY_KEY.format(dayEntry.getKey()));
            dayJson.addProperty("label", DAY_LABEL.format(dayEntry.getKey()));
            JsonArray items = new JsonArray();
            for (Entry entry : dayEntry.getValue()) {
                items.add(entry.toJson());
            }
            dayJson.add("items", items);
            days.add(dayJson);
        }
        return days;
    }

    public synchronized JsonObject summaryJson() {
        JsonObject summary = new JsonObject();
        summary.addProperty("joins", cachedJoins);
        summary.addProperty("leaves", cachedLeaves);
        summary.addProperty("commands", cachedCommands);
        summary.add("topPlayers", topPlayersJson(cachedPlayers));
        return summary;
    }

    private JsonArray recentJson(int limit, String... types) {
        List<Entry> selected = new ArrayList<>(Math.min(limit, entries.size()));
        for (int i = entries.size() - 1; i >= 0 && selected.size() < limit; i--) {
            Entry entry = entries.get(i);
            if (matchesType(entry, types)) selected.add(entry);
        }
        JsonArray array = new JsonArray();
        for (int i = selected.size() - 1; i >= 0; i--) {
            array.add(selected.get(i).toJson());
        }
        return array;
    }

    private boolean matchesType(Entry entry, String[] types) {
        for (String type : types) {
            if (type.equals(entry.type)) return true;
        }
        return false;
    }

    private void record(String type, long timestamp, UUID uuid, String playerName, String detail) {
        Entry entry = new Entry(type, timestamp, uuid.toString(), playerName, detail);
        entries.add(entry);
        addToSummaryCache(entry);
        append(entry);

        writesSinceCompact++;
        if (trimOldEntries()) {
            rebuildSummaryCache();
            compact();
            writesSinceCompact = 0;
        } else if (writesSinceCompact >= COMPACT_INTERVAL_WRITES) {
            compact();
            writesSinceCompact = 0;
        }
    }

    private boolean load() {
        if (!file.isFile()) return true;
        try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                Entry entry = parse(line);
                if (entry != null) entries.add(entry);
            }
            return true;
        } catch (IOException e) {
            logger.warning("[BWC] Failed to read player activity history: " + e.getMessage());
            return false;
        }
    }

    private Entry parse(String line) {
        String[] parts = line.split("\t", -1);
        if (parts.length != 5) return null;
        String type = parts[0];
        if (!TYPE_COMMAND.equals(type) && !TYPE_JOIN.equals(type) && !TYPE_LEAVE.equals(type)) return null;
        try {
            long timestamp = Long.parseLong(parts[1]);
            return new Entry(type, timestamp, unescape(parts[2]), unescape(parts[3]), unescape(parts[4]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void append(Entry entry) {
        try (BufferedWriter writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND)) {
            writer.write(entry.toLine());
            writer.newLine();
        } catch (IOException e) {
            logger.warning("[BWC] Failed to append player activity history: " + e.getMessage());
        }
    }

    private void compact() {
        try (BufferedWriter writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING)) {
            for (Entry entry : entries) {
                writer.write(entry.toLine());
                writer.newLine();
            }
        } catch (IOException e) {
            logger.warning("[BWC] Failed to compact player activity history: " + e.getMessage());
        }
    }

    private boolean trimOldEntries() {
        int extra = entries.size() - MAX_STORED_ENTRIES;
        if (extra <= 0) return false;
        entries.subList(0, extra).clear();
        return true;
    }

    private JsonArray topPlayersJson(Map<String, PlayerSummary> players) {
        JsonArray arr = new JsonArray();
        players.values().stream()
                .sorted((a, b) -> Integer.compare(b.score(), a.score()))
                .limit(8)
                .forEach(player -> {
                    JsonObject obj = new JsonObject();
                    obj.addProperty("player", player.name);
                    obj.addProperty("joins", player.joins);
                    obj.addProperty("leaves", player.leaves);
                    obj.addProperty("commands", player.commands);
                    obj.addProperty("score", player.score());
                    arr.add(obj);
                });
        return arr;
    }

    private void rebuildSummaryCache() {
        cachedJoins = 0;
        cachedLeaves = 0;
        cachedCommands = 0;
        cachedPlayers.clear();
        for (Entry entry : entries) {
            addToSummaryCache(entry);
        }
    }

    private void addToSummaryCache(Entry entry) {
        String name = entry.playerName().isBlank() ? "unknown" : entry.playerName();
        PlayerSummary player = cachedPlayers.computeIfAbsent(name, PlayerSummary::new);
        if (TYPE_JOIN.equals(entry.type())) {
            cachedJoins++;
            player.joins++;
        } else if (TYPE_LEAVE.equals(entry.type())) {
            cachedLeaves++;
            player.leaves++;
        } else if (TYPE_COMMAND.equals(entry.type())) {
            cachedCommands++;
            player.commands++;
        }
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\t", "\\t").replace("\r", "\\r").replace("\n", "\\n");
    }

    private static String unescape(String value) {
        StringBuilder out = new StringBuilder();
        boolean escaped = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (escaped) {
                if (c == 't') out.append('\t');
                else if (c == 'r') out.append('\r');
                else if (c == 'n') out.append('\n');
                else out.append(c);
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else {
                out.append(c);
            }
        }
        if (escaped) out.append('\\');
        return out.toString();
    }

    private record Entry(String type, long timestamp, String uuid, String playerName, String detail) {
        String toLine() {
            return type + "\t" + timestamp + "\t" + escape(uuid) + "\t" + escape(playerName) + "\t" + escape(detail);
        }

        JsonObject toJson() {
            JsonObject obj = new JsonObject();
            obj.addProperty("type", type);
            obj.addProperty("timestamp", timestamp);
            obj.addProperty("uuid", uuid);
            obj.addProperty("player", playerName);
            if (TYPE_COMMAND.equals(type)) obj.addProperty("command", detail);
            return obj;
        }
    }

    private static final class PlayerSummary {
        private final String name;
        private int joins;
        private int leaves;
        private int commands;

        private PlayerSummary(String name) {
            this.name = name;
        }

        private int score() {
            return joins + leaves + commands;
        }
    }
}
