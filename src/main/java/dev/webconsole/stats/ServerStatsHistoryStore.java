package dev.webconsole.stats;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.webconsole.config.PluginConfig;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class ServerStatsHistoryStore {
    private static final int COMPACT_INTERVAL_WRITES = 1_000;

    private final Logger logger;
    private final File file;
    private PluginConfig config;
    private final List<Point> points = new ArrayList<>();
    private int writesSinceCompact;

    public ServerStatsHistoryStore(File dataFolder, Logger logger, PluginConfig config) {
        this.logger = logger;
        this.file = new File(dataFolder, "server-stats-history.tsv");
        this.config = config;
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            logger.warning("[BWC] Failed to create data folder for server stats history");
        }
        load();
        if (trimOldPoints()) compact();
    }

    public synchronized void record(long timestamp, double tps, long ramUsed, int players, double cpu, int worlds, int chunks, int entities) {
        Point point = new Point(timestamp, tps, ramUsed, players, cpu, worlds, chunks, entities);
        points.add(point);
        append(point);

        writesSinceCompact++;
        if (trimOldPoints()) {
            compact();
            writesSinceCompact = 0;
        } else if (writesSinceCompact >= COMPACT_INTERVAL_WRITES) {
            compact();
            writesSinceCompact = 0;
        }
    }

    public synchronized void updateConfig(PluginConfig config) {
        this.config = config;
        if (trimOldPoints()) compact();
    }

    public synchronized JsonObject historyJson(String range) {
        JsonObject obj = new JsonObject();
        JsonArray timestamps = new JsonArray();
        JsonArray tps = new JsonArray();
        JsonArray ram = new JsonArray();
        JsonArray players = new JsonArray();
        JsonArray cpu = new JsonArray();

        long cutoff = cutoffTimestamp(range);
        List<Point> selected = new ArrayList<>();
        for (Point point : points) {
            if (cutoff <= 0L || point.timestamp() >= cutoff) selected.add(point);
        }

        int maxPoints = config.getStatsHistoryApiMaxPoints();
        int returnedPoints = Math.min(selected.size(), maxPoints);
        for (int i = 0; i < returnedPoints; i++) {
            int sourceIndex = returnedPoints <= 1 ? 0 : (int) Math.round(i * (selected.size() - 1) / (double) (returnedPoints - 1));
            Point point = selected.get(sourceIndex);
            timestamps.add(point.timestamp());
            tps.add(Math.round(point.tps() * 10.0) / 10.0);
            ram.add(point.ramUsed());
            players.add(point.players());
            cpu.add(Math.round(point.cpu() * 10.0) / 10.0);
        }

        obj.add("timestamps", timestamps);
        obj.add("tps", tps);
        obj.add("ram", ram);
        obj.add("players", players);
        obj.add("cpu", cpu);
        obj.addProperty("storedPoints", points.size());
        obj.addProperty("returnedPoints", timestamps.size());
        obj.addProperty("range", normalizeRange(range));
        obj.addProperty("downsampleStride", selected.size() <= maxPoints ? 1 : Math.max(1, selected.size() / Math.max(1, maxPoints)));
        return obj;
    }

    private long cutoffTimestamp(String range) {
        return switch (normalizeRange(range)) {
            case "1h" -> System.currentTimeMillis() - 60L * 60L * 1000L;
            case "6h" -> System.currentTimeMillis() - 6L * 60L * 60L * 1000L;
            case "24h" -> System.currentTimeMillis() - 24L * 60L * 60L * 1000L;
            default -> 0L;
        };
    }

    private String normalizeRange(String range) {
        if ("6h".equals(range) || "24h".equals(range) || "all".equals(range)) return range;
        return "1h";
    }

    private void load() {
        if (!file.isFile()) return;
        try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                Point point = parse(line);
                if (point != null) points.add(point);
            }
        } catch (IOException e) {
            logger.warning("[BWC] Failed to read server stats history: " + e.getMessage());
        }
    }

    private Point parse(String line) {
        String[] parts = line.split("\t", -1);
        if (parts.length != 8) return null;
        try {
            return new Point(
                    Long.parseLong(parts[0]),
                    Double.parseDouble(parts[1]),
                    Long.parseLong(parts[2]),
                    Integer.parseInt(parts[3]),
                    Double.parseDouble(parts[4]),
                    Integer.parseInt(parts[5]),
                    Integer.parseInt(parts[6]),
                    Integer.parseInt(parts[7])
            );
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void append(Point point) {
        try (BufferedWriter writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND)) {
            writer.write(point.toLine());
            writer.newLine();
        } catch (IOException e) {
            logger.warning("[BWC] Failed to append server stats history: " + e.getMessage());
        }
    }

    private void compact() {
        try (BufferedWriter writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING)) {
            for (Point point : points) {
                writer.write(point.toLine());
                writer.newLine();
            }
        } catch (IOException e) {
            logger.warning("[BWC] Failed to compact server stats history: " + e.getMessage());
        }
    }

    private boolean trimOldPoints() {
        int extra = points.size() - config.getStatsHistoryRetentionPoints();
        if (extra <= 0) return false;
        points.subList(0, extra).clear();
        return true;
    }

    private record Point(long timestamp, double tps, long ramUsed, int players, double cpu, int worlds, int chunks, int entities) {
        String toLine() {
            return timestamp + "\t" + tps + "\t" + ramUsed + "\t" + players + "\t" + cpu + "\t" + worlds + "\t" + chunks + "\t" + entities;
        }
    }
}
