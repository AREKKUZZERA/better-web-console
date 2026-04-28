package dev.webconsole.auth;

import at.favre.lib.crypto.bcrypt.BCrypt;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Manages web console users. Passwords are stored as bcrypt hashes.
 * User data is stored in users.dat (username:bcrypt_hash per line).
 */
public class UserManager {

    private static final Logger log = Logger.getLogger("Better-WebConsole");
    private static final int BCRYPT_COST = 12;

    private final File usersFile;
    private final Map<String, String> users = new ConcurrentHashMap<>(); // username -> bcrypt hash

    public UserManager(File dataFolder) {
        this.usersFile = new File(dataFolder, "users.dat");
        loadUsers();
    }

    private void loadUsers() {
        if (!usersFile.exists()) return;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(usersFile), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int sep = line.indexOf(':');
                if (sep < 1) continue;
                String username = line.substring(0, sep);
                String hash = line.substring(sep + 1);
                users.put(username.toLowerCase(), hash);
            }
            log.info("[Better-WebConsole] Loaded " + users.size() + " user(s).");
        } catch (IOException e) {
            log.severe("[Better-WebConsole] Failed to load users: " + e.getMessage());
        }
    }

    private synchronized void saveUsers() {
        try {
            File parent = usersFile.getParentFile();
            if (!parent.exists()) parent.mkdirs();

            // Write to temp file then rename for atomicity
            File tmp = new File(parent, "users.dat.tmp");
            try (PrintWriter writer = new PrintWriter(
                    new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8))) {
                writer.println("# Better-WebConsole user database - DO NOT EDIT MANUALLY");
                for (Map.Entry<String, String> entry : users.entrySet()) {
                    writer.println(entry.getKey() + ":" + entry.getValue());
                }
            }
            Files.move(tmp.toPath(), usersFile.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            log.severe("[Better-WebConsole] Failed to save users: " + e.getMessage());
        }
    }

    /**
     * Adds a user with bcrypt-hashed password.
     */
    public void addUser(String username, String password) {
        String hash = BCrypt.withDefaults().hashToString(BCRYPT_COST, password.toCharArray());
        users.put(username.toLowerCase(), hash);
        saveUsers();
    }

    public boolean setPassword(String username, String password) {
        String key = username.toLowerCase();
        if (!users.containsKey(key)) return false;
        String hash = BCrypt.withDefaults().hashToString(BCRYPT_COST, password.toCharArray());
        users.put(key, hash);
        saveUsers();
        return true;
    }

    /**
     * Removes a user. Returns true if the user existed.
     */
    public boolean removeUser(String username) {
        boolean existed = users.remove(username.toLowerCase()) != null;
        if (existed) saveUsers();
        return existed;
    }

    /**
     * Verifies username and password. Uses constant-time comparison.
     */
    public boolean verifyPassword(String username, String password) {
        if (username == null || password == null) return false;
        String hash = users.get(username.toLowerCase());
        if (hash == null) {
            // Perform a dummy hash to prevent timing attacks
            BCrypt.verifyer().verify("dummy".toCharArray(), BCrypt.withDefaults().hash(4, "dummy".toCharArray()));
            return false;
        }
        BCrypt.Result result = BCrypt.verifyer().verify(password.toCharArray(), hash);
        return result.verified;
    }

    public boolean userExists(String username) {
        return users.containsKey(username.toLowerCase());
    }

    public List<String> listUsers() {
        return new ArrayList<>(users.keySet());
    }

    public int getUserCount() {
        return users.size();
    }
}
