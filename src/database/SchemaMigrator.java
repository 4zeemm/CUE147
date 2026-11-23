package database;

import exception.BusinessException;
import util.AppLogger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Keeps the tables of the shared database up to date, without losing data.
 *
 * Every change to the tables is a numbered SQL file in resources/db (V001, V002, ...).
 * The table schema_version remembers which numbers were already applied.
 * When a newer CUE 147 connects, it applies only the files that are missing.
 * A MySQL lock makes sure two computers starting at the same time do not both update.
 */
public class SchemaMigrator {
    /** All migration files in order. Add new files at the END; never change an old one. */
    static final String[] MIGRATIONS = {
            "V001__base_schema.sql",
    };

    private static final String LOCK = "cue147_schema_update";

    /** The newest database version this program knows. */
    public static int latestVersion() { return MIGRATIONS.length; }

    /** Brings the database up to date. Returns the database version afterwards. */
    public static int migrate() throws SQLException, BusinessException {
        try (Connection con = DBConnection.getConnection()) {
            if (!getLock(con)) {
                throw new BusinessException("Another computer is updating the database right now.\nPlease try again in a minute.");
            }
            try {
                return migrate(con);
            } finally {
                try (Statement st = con.createStatement()) {
                    st.execute("SELECT RELEASE_LOCK('" + LOCK + "')");
                }
            }
        }
    }

    private static int migrate(Connection con) throws SQLException, BusinessException {
        try (Statement st = con.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS schema_version ("
                    + "version INT PRIMARY KEY, name VARCHAR(100) NOT NULL, "
                    + "applied_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        }
        int current = currentVersion(con);

        if (current == 0 && tableExists(con, "game_tables")) {
            // A database made before version numbers existed (the college-project version).
            if (!columnExists(con, "game_tables", "is_under_maintenance")) {
                AppLogger.info("Applying the old 'corrections' upgrade to an existing database");
                runScript(con, "legacy_upgrade_v2.sql");
            }
            record(con, 1, "existing database (baseline)");
            current = 1;
        }

        if (current > latestVersion()) {
            throw new BusinessException("This database was updated by a newer CUE 147 (database version " + current + ").\n"
                    + "Please install the newest CUE 147 on this computer.");
        }
        for (int v = current + 1; v <= latestVersion(); v++) {
            String file = MIGRATIONS[v - 1];
            AppLogger.info("Updating the database to version " + v + " (" + file + ")");
            runScript(con, file);
            record(con, v, file);
        }
        return latestVersion();
    }

    static int currentVersion(Connection con) throws SQLException {
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery("SELECT COALESCE(MAX(version), 0) FROM schema_version")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static boolean getLock(Connection con) throws SQLException {
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery("SELECT GET_LOCK('" + LOCK + "', 30)")) {
            return rs.next() && rs.getInt(1) == 1;
        }
    }

    private static void record(Connection con, int version, String name) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("INSERT INTO schema_version (version, name) VALUES (?, ?)")) {
            ps.setInt(1, version);
            ps.setString(2, name);
            ps.executeUpdate();
        }
    }

    private static boolean tableExists(Connection con, String table) throws SQLException {
        String sql = "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getInt(1) > 0; }
        }
    }

    private static boolean columnExists(Connection con, String table, String column) throws SQLException {
        String sql = "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, table);
            ps.setString(2, column);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getInt(1) > 0; }
        }
    }

    /** Runs every statement of one SQL file from resources/db. */
    private static void runScript(Connection con, String file) throws SQLException {
        String sql;
        try (InputStream in = SchemaMigrator.class.getResourceAsStream("/db/" + file)) {
            if (in == null) throw new SQLException("Migration file missing from the program: " + file);
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new SQLException("Cannot read migration file " + file, e);
        }
        try (Statement st = con.createStatement()) {
            for (String statement : splitStatements(sql)) st.execute(statement);
        }
    }

    /** Removes -- comments and splits the text at ; (ignoring ; and -- inside 'quoted text'). */
    static List<String> splitStatements(String sql) {
        List<String> list = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuote = false;
        for (int i = 0; i < sql.length(); i++) {
            char ch = sql.charAt(i);
            if (ch == '\'') inQuote = !inQuote;
            if (!inQuote && ch == '-' && i + 1 < sql.length() && sql.charAt(i + 1) == '-') {
                while (i < sql.length() && sql.charAt(i) != '\n') i++;   // skip to the end of the line
                current.append('\n');
                continue;
            }
            if (!inQuote && ch == ';') {
                if (!current.toString().isBlank()) list.add(current.toString().trim());
                current.setLength(0);
                continue;
            }
            current.append(ch);
        }
        if (!current.toString().isBlank()) list.add(current.toString().trim());
        return list;
    }

    /** Command line:  java -Dcue147.db=cue147_test -cp ... database.SchemaMigrator   (used by setup-db.sh and test.sh) */
    public static void main(String[] args) throws Exception {
        int version = migrate();
        System.out.println(DBConnection.getConfig().effectiveDatabase() + " is at database version " + version);
    }
}
