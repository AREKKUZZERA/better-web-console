package dev.webconsole.console;

import dev.webconsole.web.WebSocketHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class ConsoleLogHandler extends Handler {

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private final int maxBuffer;
    private final Deque<String> logBuffer;
    private final ReentrantLock bufferLock = new ReentrantLock();
    private final SimpleFormatter rawFormatter = new SimpleFormatter();

    private volatile WebSocketHandler wsHandler;

    private PrintStream originalOut;
    private PrintStream originalErr;
    private boolean systemStreamsHooked = false;

    private WebConsoleAppender log4jAppender;

    public ConsoleLogHandler(int maxBuffer) {
        this.maxBuffer = maxBuffer;
        this.logBuffer = new ArrayDeque<>(maxBuffer);
        setLevel(Level.ALL);
    }

    @Override
    public void publish(LogRecord record) {
        if (record == null) return;

        String msg = rawFormatter.formatMessage(record);
        if (msg == null || msg.isBlank()) return;

        String time = TIME_FMT.format(Instant.ofEpochMilli(record.getMillis()));
        String level = levelName(record.getLevel());
        String line = "[" + time + " " + level + "]: " + msg;

        publishFormattedLine(line);

        if (record.getThrown() != null) {
            publishThrowable(record.getThrown());
        }
    }

    public void publishFormattedLine(String line) {
        if (line == null) return;
        String cleaned = line.replace("\r", "").stripTrailing();
        if (cleaned.isEmpty()) return;

        bufferLock.lock();
        try {
            if (logBuffer.size() >= maxBuffer) {
                logBuffer.pollFirst();
            }
            logBuffer.addLast(cleaned);
        } finally {
            bufferLock.unlock();
        }

        WebSocketHandler h = wsHandler;
        if (h != null) {
            h.broadcast(cleaned);
        }
    }

    public void publishThrowable(Throwable throwable) {
        if (throwable == null) return;

        StringWriter sw = new StringWriter();
        throwable.printStackTrace(new PrintWriter(sw));
        String stack = sw.toString().replace("\r", "");

        for (String line : stack.split("\n")) {
            String trimmed = line.stripTrailing();
            if (!trimmed.isEmpty()) {
                publishFormattedLine(trimmed);
            }
        }
    }

    public void loadBacklog(Path logFile, Logger logger) {
        if (logFile == null || !Files.isRegularFile(logFile)) return;

        try (var lines = Files.lines(logFile, StandardCharsets.UTF_8)) {
            lines.forEach(this::publishFormattedLine);
        } catch (IOException e) {
            if (logger != null) {
                logger.fine("Failed to load console backlog from " + logFile + ": " + e.getMessage());
            }
        }
    }

    public void hookSystemStreams() {
        if (systemStreamsHooked) return;

        originalOut = System.out;
        originalErr = System.err;

        System.setOut(createTeePrintStream(originalOut));
        System.setErr(createTeePrintStream(originalErr));

        systemStreamsHooked = true;
    }

    public void restoreSystemStreams() {
        if (!systemStreamsHooked) return;

        if (originalOut != null) System.setOut(originalOut);
        if (originalErr != null) System.setErr(originalErr);

        systemStreamsHooked = false;
    }

    public void hookLog4j() {
        if (log4jAppender != null) return;

        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        var config = context.getConfiguration();

        log4jAppender = WebConsoleAppender.create(this);
        log4jAppender.start();

        config.getRootLogger().addAppender(log4jAppender, null, null);
        context.updateLoggers();
    }

    public void unhookLog4j() {
        if (log4jAppender == null) return;

        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        var config = context.getConfiguration();

        config.getRootLogger().removeAppender(log4jAppender.getName());
        log4jAppender.stop();
        log4jAppender = null;

        context.updateLoggers();
    }

    private PrintStream createTeePrintStream(PrintStream original) {
        return new PrintStream(new OutputStream() {
            private final StringBuilder buffer = new StringBuilder();

            @Override
            public void write(int b) {
                original.write(b);

                char c = (char) b;
                if (c == '\r') return;

                if (c == '\n') {
                    flushBuffer();
                } else {
                    buffer.append(c);
                }
            }

            @Override
            public void flush() {
                original.flush();
                flushBuffer();
            }

            private void flushBuffer() {
                if (buffer.isEmpty()) return;
                String line = buffer.toString();
                buffer.setLength(0);
                publishFormattedLine(line);
            }
        }, true);
    }

    private String levelName(Level level) {
        if (level.intValue() >= Level.SEVERE.intValue()) return "SEVERE";
        if (level.intValue() >= Level.WARNING.intValue()) return "WARN";
        if (level.intValue() >= Level.INFO.intValue()) return "INFO";
        if (level.intValue() >= Level.FINE.intValue()) return "DEBUG";
        return "TRACE";
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() {
        unhookLog4j();
        restoreSystemStreams();
        wsHandler = null;
    }

    public void setWebSocketHandler(WebSocketHandler handler) {
        this.wsHandler = handler;
    }

    public List<String> getBufferedLines() {
        bufferLock.lock();
        try {
            return new ArrayList<>(logBuffer);
        } finally {
            bufferLock.unlock();
        }
    }

    public com.google.gson.JsonArray errorGroupsJson() {
        Map<String, ErrorGroup> groups = new LinkedHashMap<>();
        List<String> lines = getBufferedLines();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (!isErrorLine(line)) continue;
            String signature = errorSignature(line);
            ErrorGroup group = groups.computeIfAbsent(signature, ignored -> new ErrorGroup(signature));
            group.count++;
            if (group.firstLine == 0) group.firstLine = i + 1;
            group.lastLine = i + 1;
            group.sample = line;
        }

        com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
        groups.values().stream()
                .sorted((a, b) -> Integer.compare(b.count, a.count))
                .limit(25)
                .forEach(group -> {
                    com.google.gson.JsonObject obj = new com.google.gson.JsonObject();
                    obj.addProperty("signature", group.signature);
                    obj.addProperty("count", group.count);
                    obj.addProperty("firstLine", group.firstLine);
                    obj.addProperty("lastLine", group.lastLine);
                    obj.addProperty("sample", group.sample);
                    arr.add(obj);
                });
        return arr;
    }

    private boolean isErrorLine(String line) {
        String upper = line.toUpperCase(Locale.ROOT);
        return upper.contains(" SEVERE") || upper.contains(" ERROR") || upper.contains("EXCEPTION")
                || upper.contains("CAUSED BY:");
    }

    private String errorSignature(String line) {
        String normalized = line
                .replaceAll("^\\[[^]]+]", "")
                .replaceAll("\\bat [\\w.$]+\\([^)]*\\)", "at <stack>")
                .replaceAll("\\b[0-9a-fA-F-]{32,}\\b", "<id>")
                .replaceAll("\\b\\d+\\b", "<n>")
                .trim();
        return normalized.length() > 160 ? normalized.substring(0, 160) : normalized;
    }

    private static final class ErrorGroup {
        private final String signature;
        private int count;
        private int firstLine;
        private int lastLine;
        private String sample = "";

        private ErrorGroup(String signature) {
            this.signature = signature;
        }
    }
}
