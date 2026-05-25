package dev.webconsole.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

public final class SecureFiles {

    private static final Set<PosixFilePermission> OWNER_FILE = Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE);

    private SecureFiles() {}

    public static void ownerOnlyFile(Path path) {
        if (path == null) return;
        try {
            if (Files.exists(path)) {
                Files.setPosixFilePermissions(path, OWNER_FILE);
            }
        } catch (UnsupportedOperationException | SecurityException ignored) {
            // Windows and some filesystems do not expose POSIX permissions.
        } catch (Exception ignored) {
        }
    }
}
