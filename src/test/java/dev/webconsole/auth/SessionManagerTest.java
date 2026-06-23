package dev.webconsole.auth;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SessionManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void sessionsReportIdleExpiryWhenIdleExpiresFirst() {
        SessionManager manager = new SessionManager(60, 120, null);
        try {
            manager.createSession("admin", "127.0.0.1");

            JsonObject session = manager.sessionsJson().get(0).getAsJsonObject();

            assertEquals(
                    session.get("lastSeenAt").getAsLong() + 60L * 60_000L,
                    session.get("expiresAt").getAsLong());
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void loadFromDiskSkipsSessionsPastAbsoluteLifetime() throws Exception {
        Path sessionsFile = tempDir.resolve("sessions.tsv");
        long now = System.currentTimeMillis();
        long createdAt = now - 121L * 60_000L;
        Files.writeString(sessionsFile,
                "legacy-token\tadmin\t127.0.0.1\t" + createdAt + "\t" + now + "\n",
                StandardCharsets.UTF_8);

        SessionManager manager = new SessionManager(60, 120, sessionsFile);
        try {
            assertNull(manager.validateSession("legacy-token"));
            assertEquals(0, manager.getActiveSessionCount());
        } finally {
            manager.shutdown();
        }
    }
}
