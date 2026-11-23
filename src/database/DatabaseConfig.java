package database;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Properties;

/**
 * Where the club database is: host, port, database name, user and password.
 *
 * Saved in the file  .cue147/database.properties  in the user's home folder
 * (Windows: C:\Users\NAME\.cue147\database.properties, Mac: /Users/NAME/.cue147/database.properties).
 * The file is written by the "Database settings" window on the login screen.
 * If the file does not exist, the defaults below are used (a MySQL server on this computer).
 */
public class DatabaseConfig {
    public static final Path FOLDER = Paths.get(System.getProperty("user.home"), ".cue147");
    public static final Path FILE = FOLDER.resolve("database.properties");

    private final String host;
    private final int port;
    private final String database;
    private final String user;
    private final String password;

    public DatabaseConfig(String host, int port, String database, String user, String password) {
        this.host = host;
        this.port = port;
        this.database = database;
        this.user = user;
        this.password = password;
    }

    /** The settings for a computer that runs MySQL itself (used until settings are saved). */
    public static DatabaseConfig defaults() {
        return new DatabaseConfig("127.0.0.1", 3306, "cue147", "cue147_app", "cue147_pass");
    }

    /** Reads the saved settings, or the defaults when nothing was saved yet. */
    public static DatabaseConfig load() {
        DatabaseConfig d = defaults();
        if (!Files.exists(FILE)) return d;
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(FILE)) {
            p.load(in);
        } catch (IOException e) {
            return d;
        }
        int port;
        try {
            port = Integer.parseInt(p.getProperty("port", String.valueOf(d.port)).trim());
        } catch (NumberFormatException e) {
            port = d.port;
        }
        return new DatabaseConfig(p.getProperty("host", d.host).trim(), port,
                p.getProperty("database", d.database).trim(), p.getProperty("user", d.user).trim(),
                p.getProperty("password", d.password));
    }

    /** Saves these settings so the next start uses them. Only the user of this computer can read the file. */
    public void save() throws IOException {
        Files.createDirectories(FOLDER);
        Properties p = new Properties();
        p.setProperty("host", host);
        p.setProperty("port", String.valueOf(port));
        p.setProperty("database", database);
        p.setProperty("user", user);
        p.setProperty("password", password);
        try (OutputStream out = Files.newOutputStream(FILE)) {
            p.store(out, "CUE 147 database connection");
        }
        try {
            Files.setPosixFilePermissions(FILE, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException e) {
            // Windows: the file is in the user's own profile folder, which other users cannot read
        }
    }

    public String getHost() { return host; }
    public int getPort() { return port; }
    public String getDatabase() { return database; }
    public String getUser() { return user; }
    public String getPassword() { return password; }

    /** The tests start Java with -Dcue147.db=cue147_test to use a separate database. */
    public String effectiveDatabase() { return System.getProperty("cue147.db", database); }

    public String jdbcUrl() {
        return "jdbc:mysql://" + host + ":" + port + "/" + effectiveDatabase()
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Kolkata&connectTimeout=5000";
    }

    /** "192.168.1.20:3306/cue147" - shown in messages. */
    public String describe() { return host + ":" + port + "/" + effectiveDatabase(); }
}
