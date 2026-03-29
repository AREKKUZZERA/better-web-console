package dev.webconsole.console;

import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.layout.PatternLayout;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;

public class WebConsoleAppender extends AbstractAppender {

    private final ConsoleLogHandler handler;

    protected WebConsoleAppender(String name,
                                 Layout<? extends Serializable> layout,
                                 ConsoleLogHandler handler) {
        super(name, null, layout, false, Property.EMPTY_ARRAY);
        this.handler = handler;
    }

    @Override
    public void append(LogEvent event) {
        if (event == null) return;

        String line = new String(getLayout().toByteArray(event), StandardCharsets.UTF_8);
        line = stripAnsi(line).replace("\r", "");

        String[] parts = line.split("\n");
        for (String part : parts) {
            String trimmed = part.stripTrailing();
            if (!trimmed.isEmpty()) {
                handler.publishFormattedLine(trimmed);
            }
        }

        if (event.getThrown() != null) {
            handler.publishThrowable(event.getThrown());
        }
    }

    private static String stripAnsi(String s) {
        return s.replaceAll("\\u001B\\[[;\\d]*m", "");
    }

    public static WebConsoleAppender create(ConsoleLogHandler handler) {
        return new WebConsoleAppender(
                "BetterWebConsoleAppender",
                PatternLayout.newBuilder()
                        .withPattern("[%d{HH:mm:ss} %level]: %msg%n")
                        .build(),
                handler
        );
    }
}