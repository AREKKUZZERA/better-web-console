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
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;

/**
 * Java logging Handler that captures server log messages and:
 * 1. Buffers recent lines for new WebSocket connections
 * 2. Broadcasts new lines to all active WebSocket sessions
 */
public class ConsoleLogHandler extends Handler {

    private static final int MAX_BUFFER = 500;
    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private final Deque<String> logBuffer = new ArrayDeque<>(MAX_BUFFER);
    private final ReentrantLock bufferLock = new ReentrantLock();
    private final Formatter formatter = new SimpleFormatter();

    // Reference to the WS handler, set when the server starts
    private volatile WebSocketHandler wsHandler;

    @Override
    public void publish(LogRecord record) {
        if (!isLoggable(record)) return;

        String raw = formatter.formatMessage(record);
        if (raw == null || raw.isBlank()) return;

        String level = record.getLevel().getName();
        String time = TIME_FMT.format(Instant.ofEpochMilli(record.getMillis()));
        String line = "[" + time + " " + level + "] " + raw;

        bufferLock.lock();
        try {
            if (logBuffer.size() >= MAX_BUFFER) logBuffer.pollFirst();
            logBuffer.addLast(line);
        } finally {
            bufferLock.unlock();
        }

        WebSocketHandler handler = wsHandler;
        if (handler != null) {
            handler.broadcast(line);
        }
    }

    @Override
    public void flush() {}

    @Override
    public void close() {
        wsHandler = null;
    }

    public void setWebSocketHandler(WebSocketHandler wsHandler) {
        this.wsHandler = wsHandler;
    }

    /**
     * Returns a snapshot of the current log buffer (for new connections).
     */
    public List<String> getBufferedLines() {
        bufferLock.lock();
        try {
            return new ArrayList<>(logBuffer);
        } finally {
            bufferLock.unlock();
        }
    }
}
