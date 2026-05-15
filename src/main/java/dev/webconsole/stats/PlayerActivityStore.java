package dev.webconsole.stats;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

public class PlayerActivityStore {
    private static final long RETENTION_MS = Duration.ofHours(24).toMillis();
    private static final String TYPE_COMMAND = "command";
    private static final String TYPE_JOIN = "join";
    private static final String TYPE_LEAVE = "leave";

    private final Logger logger;
    private final File file;
    private final List<Entry> entries = new ArrayList<>();

    public PlayerActivityStore(File dataFolder, Logger logger) {
        this.logger = logger;
        this.file = new File(dataFolder, "player-command-history.tsv");
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            logger.warning("[BWC] Failed to create data folder for player activity history");
        }
        load();
        compact();
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
        pruneExpired(false);
        JsonArray array = new JsonArray();
        for (Entry entry : entries) {
            if (!TYPE_JOIN.equals(entry.type) && !TYPE_LEAVE.equals(entry.type)) continue;
            array.add(entry.toJson());
        }
        return array;
    }

    public synchronized JsonArray recentCommandsJson() {
        pruneExpired(false);
        JsonArray array = new JsonArray();
        for (Entry entry : entries) {
            if (TYPE_COMMAND.equals(entry.type)) array.add(entry.toJson());
        }
        return array;
    }

    private void record(String type, long timestamp, UUID uuid, String playerName, String detail) {
        pruneExpired(true);
        Entry entry = new Entry(type, timestamp, uuid.toString(), playerName, detail);
        entries.add(entry);
        append(entry);
    }

    private void load() {
        if (!file.isFile()) return;
        long cutoff = System.currentTimeMillis() - RETENTION_MS;
        try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                Entry entry = parse(line);
                if (entry != null && entry.timestamp >= cutoff) entries.add(entry);
            }
        } catch (IOException e) {
            logger.warning("[BWC] Failed to read player activity history: " + e.getMessage());
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

    private void pruneExpired(boolean compactAfterPrune) {
        long cutoff = System.currentTimeMillis() - RETENTION_MS;
        boolean removed = false;
        Iterator<Entry> iterator = entries.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().timestamp < cutoff) {
                iterator.remove();
                removed = true;
            }
        }
        if (removed && compactAfterPrune) compact();
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
        pruneExpired(false);
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
}
