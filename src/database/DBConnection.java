package database;

import java.io.FileInputStream;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * The ONLY class that knows how to connect to MySQL.
 * Every DAO calls DBConnection.getConnection() when it needs the database.
 *
 * The database address comes from the file "cue147.properties" in the project folder:
 *   db.host=127.0.0.1      (this computer; the Windows laptop, or the Mac when it is the server)
 *   db.host=192.168.1.20   (the address of the computer that runs MySQL, when this one is only a client)
 * If the file is missing, the address is 127.0.0.1.
 */
public class DBConnection {
    // The tests start Java with -Dcue147.db=cue147_test to use a separate database.
    private static final String DATABASE = System.getProperty("cue147.db", "cue147");
    private static final String HOST = readHost();
    private static final String URL = "jdbc:mysql://" + HOST + ":3306/" + DATABASE
            + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Kolkata";
    private static final String USER = "cue147_app";
    private static final String PASSWORD = "cue147_pass";

    private static String readHost() {
        Properties p = new Properties();
        try (InputStream in = new FileInputStream("cue147.properties")) {
            p.load(in);
        } catch (java.io.IOException e) {
            return "127.0.0.1";   // no settings file: use this computer
        }
        return p.getProperty("db.host", "127.0.0.1").trim();
    }

    /** Opens a new connection. The caller must close it (we use try-with-resources). */
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    /** Used at start-up to check that MySQL is running and the database exists. */
    public static boolean isDatabaseReachable() {
        try (Connection con = getConnection()) {
            return con.isValid(2);
        } catch (SQLException e) {
            return false;
        }
    }
}
