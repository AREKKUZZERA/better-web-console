package dev.webconsole.audit;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Writes audit entries asynchronously to plugins/Better-WebConsole/audit.log.
 * Each line: [YYYY-MM-DD HH:mm:ss] [ACTION] user@ip :: detail
 */
public class AuditLog {

    private static final DateTimeFormatter DT_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Logger log = Logger.getLogger("Better-WebConsole");

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

    private void writerLoop() {
        try (PrintWriter writer = new PrintWriter(new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(logFile, true), StandardCharsets.UTF_8)))) {
            while (running || !queue.isEmpty()) {
                try {
                    String line = queue.poll(500, TimeUnit.MILLISECONDS);
                    if (line != null) { writer.println(line); writer.flush(); }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } catch (IOException e) {
            log.severe("[Better-WebConsole] Audit log error: " + e.getMessage());
        }
    }

    public void shutdown() {
        running = false;
        writerThread.interrupt();
    }
}
