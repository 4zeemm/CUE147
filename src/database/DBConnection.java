package database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * The ONLY class that knows how to connect to MySQL.
 * Every DAO calls DBConnection.getConnection() when it needs the database.
 * The address, user and password come from DatabaseConfig (not written in the code).
 */
public class DBConnection {
    private static DatabaseConfig config = DatabaseConfig.load();

    /** Opens a new connection. The caller must close it (we use try-with-resources). */
    public static Connection getConnection() throws SQLException {
        return open(config);
    }

    static Connection open(DatabaseConfig c) throws SQLException {
        return DriverManager.getConnection(c.jdbcUrl(), c.getUser(), c.getPassword());
    }

    public static DatabaseConfig getConfig() { return config; }

    /** Uses new settings from now on (called after the Database settings window saved them). */
    public static void setConfig(DatabaseConfig newConfig) { config = newConfig; }

    /** Used at start-up to check that MySQL is running and the database exists. */
    public static boolean isDatabaseReachable() {
        return testConnection(config) == null;
    }

    /** Tries the given settings. Returns null when they work, otherwise a message for the user. */
    public static String testConnection(DatabaseConfig c) {
        try (Connection con = open(c)) {
            return con.isValid(3) ? null : "The database did not answer.";
        } catch (SQLException e) {
            return explain(e, c);
        }
    }

    /** Turns a MySQL error into a sentence that counter staff can understand. */
    public static String explain(SQLException e, DatabaseConfig c) {
        if (e.getErrorCode() == 1045) {
            return "The database user name or password is wrong.";
        }
        if (e.getErrorCode() == 1049) {
            return "The server has no database called \"" + c.effectiveDatabase() + "\".\n"
                    + "Run the server setup (see README) on the server computer.";
        }
        if (e.getSQLState() != null && e.getSQLState().startsWith("08")) {
            return "Cannot reach the database server at " + c.getHost() + ":" + c.getPort() + ".\n"
                    + "Check that the server computer is switched on, MySQL is running,\n"
                    + "and this computer is on the same network.";
        }
        return "Database error: " + e.getMessage();
    }
}
