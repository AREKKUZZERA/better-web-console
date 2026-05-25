package dev.webconsole.audit;

import dev.webconsole.util.SecureFiles;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Writes audit entries asynchronously to plugins/Better-WebConsole/audit.log.
 * Each line: [YYYY-MM-DD HH:mm:ss] [ACTION] user@ip :: detail
 */
public class AuditLog {

    private static final DateTimeFormatter DT_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Logger log = Logger.getLogger("Better-WebConsole");
    private static final Pattern LINE_PATTERN = Pattern.compile("^\\[(.+?)] \\[(.+?)] (.*?)@(.*?) :: (.*)$");

    private final File logFile;
    private final BlockingQueue<String> queue = new LinkedBlockingQueue<>(10_000);
    private final Thread writerThread;
    private volatile boolean running = true;

    public AuditLog(File dataFolder) {
        this.logFile = new File(dataFolder, "audit.log");
        if (!dataFolder.exists()) dataFolder.mkdirs();
        this.writerThread = new Thread(this::writerLoop, "BWC-AuditLog");
        this.writerThread.setDaemon(true);
        this.writerThread.start();
    }

    public void log(String username, String ip, String action, String detail) {
        String ts   = LocalDateTime.now().format(DT_FMT);
        String line = "[" + ts + "] [" + action + "] " + username + "@" + ip + " :: " + detail;
        queue.offer(line);
    }

    public void logLogin(String username, String ip)  { log(username, ip, "LOGIN",   "authenticated"); }
    public void logLogout(String username, String ip) { log(username, ip, "LOGOUT",  "session ended"); }
    public void logFailed(String username, String ip) { log(username, ip, "FAIL",    "bad credentials"); }
    public void logCommand(String username, String ip, String cmd) { log(username, ip, "COMMAND", cmd); }
    public void logKick(String username, String ip, String target, String reason) {
        log(username, ip, "KICK", "target=" + target + " reason=" + reason);
    }
    public void logBan(String username, String ip, String target, String reason) {
        log(username, ip, "BAN", "target=" + target + " reason=" + reason);
    }

    public JsonArray recentJson(int limit) {
        int safeLimit = Math.max(1, Math.min(500, limit));
        JsonArray arr = new JsonArray();
        if (!logFile.isFile()) return arr;

        Deque<String> lines = new ArrayDeque<>(safeLimit);
        try (BufferedReader reader = Files.newBufferedReader(logFile.toPath(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (lines.size() >= safeLimit) lines.removeFirst();
                lines.addLast(line);
            }
        } catch (IOException e) {
            log.warning("[Better-WebConsole] Failed to read audit log: " + e.getMessage());
            return arr;
        }

        for (String line : lines) {
            arr.add(parseLine(line));
        }
        return arr;
    }

    private JsonObject parseLine(String line) {
        JsonObject obj = new JsonObject();
        obj.addProperty("raw", line);
        Matcher matcher = LINE_PATTERN.matcher(line);
        if (!matcher.matches()) return obj;

        obj.addProperty("timestamp", matcher.group(1));
        obj.addProperty("action", matcher.group(2));
        obj.addProperty("username", matcher.group(3));
        obj.addProperty("ip", matcher.group(4));
        obj.addProperty("detail", matcher.group(5));
        return obj;
    }

    private void writerLoop() {
        try (PrintWriter writer = new PrintWriter(new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(logFile, true), StandardCharsets.UTF_8)))) {
            SecureFiles.ownerOnlyFile(logFile.toPath());
            while (running || !queue.isEmpty()) {
                try {
                    String line = queue.poll(500, TimeUnit.MILLISECONDS);
                    if (line != null) { writer.println(line); writer.flush(); }
                } catch (InterruptedException e) {
                    if (!running && queue.isEmpty()) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        } catch (IOException e) {
            log.severe("[Better-WebConsole] Audit log error: " + e.getMessage());
        }
    }

    public void shutdown() {
        running = false;
        try {
            writerThread.join(2_000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
