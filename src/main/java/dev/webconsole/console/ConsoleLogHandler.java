package dev.webconsole.console;

import dev.webconsole.web.WebSocketHandler;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;

/**
 * Captures ALL server console output by attaching to the root Java logger.
 * Buffers recent lines and broadcasts to all active WebSocket sessions.
 *
 * Key fix: uses record.getLevel() + loggerName to produce clean, full lines
 * matching what the server console actually displays.
 */
public class ConsoleLogHandler extends Handler {

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private final int maxBuffer;
    private final Deque<String> logBuffer;
    private final ReentrantLock bufferLock = new ReentrantLock();
    private final SimpleFormatter rawFormatter = new SimpleFormatter();
    private volatile WebSocketHandler wsHandler;

    public ConsoleLogHandler(int maxBuffer) {
        this.maxBuffer = maxBuffer;
        this.logBuffer = new ArrayDeque<>(maxBuffer);
        // Accept everything — we want ALL console output
        setLevel(Level.ALL);
    }

    @Override
    public void publish(LogRecord record) {
        if (record == null) return;

        // Format the message text (resolves parameters, ResourceBundle, etc.)
        String msg = rawFormatter.formatMessage(record);
        if (msg == null) msg = "";

        // Append exception info if present
        if (record.getThrown() != null) {
            msg = msg + " — " + record.getThrown();
        }

        // Skip completely empty records
        if (msg.isBlank()) return;

        // Build a clean console-style line: [HH:mm:ss LEVEL] message
        String time  = TIME_FMT.format(Instant.ofEpochMilli(record.getMillis()));
        String level = levelName(record.getLevel());
        String line  = "[" + time + " " + level + "] " + msg;

        bufferLock.lock();
        try {
            if (logBuffer.size() >= maxBuffer) logBuffer.pollFirst();
            logBuffer.addLast(line);
        } finally {
            bufferLock.unlock();
        }

        WebSocketHandler h = wsHandler;
        if (h != null) h.broadcast(line);
    }

    private String levelName(Level level) {
        if (level.intValue() >= Level.SEVERE.intValue())  return "SEVERE";
        if (level.intValue() >= Level.WARNING.intValue()) return "WARN";
        if (level.intValue() >= Level.INFO.intValue())    return "INFO";
        if (level.intValue() >= Level.FINE.intValue())    return "DEBUG";
        return "TRACE";
    }

    @Override public void flush() {}
    @Override public void close() { wsHandler = null; }

    public void setWebSocketHandler(WebSocketHandler h) { this.wsHandler = h; }

    public List<String> getBufferedLines() {
        bufferLock.lock();
        try { return new ArrayList<>(logBuffer); }
        finally { bufferLock.unlock(); }
    }
}
