package database;

import exception.BusinessException;

import java.io.IOException;
import java.io.Writer;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes the WHOLE database to a .sql text file, and reads it back - in plain Java (JDBC), so it works
 * on every computer that runs CUE 147, without mysqldump or any other tool.
 *
 * The file has, for every table: DROP TABLE, CREATE TABLE (from MySQL itself) and INSERT statements with all rows.
 */
public class DatabaseBackup {
    public static final String HEADER = "-- CUE 147 backup";
    private static final int ROWS_PER_INSERT = 100;

    /** Writes every table of the current database. note = one line of information (when, where, version). */
    public static void write(Connection con, Writer out, String note) throws SQLException, IOException {
        out.write(HEADER + "\n-- " + note.replace("\n", " ") + "\n");
        out.write("SET FOREIGN_KEY_CHECKS = 0;\n\n");
        for (String table : baseTables(con)) {
            out.write("-- table " + table + "\n");
            out.write("DROP TABLE IF EXISTS `" + table + "`;\n");
            try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery("SHOW CREATE TABLE `" + table + "`")) {
                rs.next();
                out.write(rs.getString(2) + ";\n");
            }
            writeRows(con, out, table);
            out.write("\n");
        }
        out.write("SET FOREIGN_KEY_CHECKS = 1;\n");
    }

    private static void writeRows(Connection con, Writer out, String table) throws SQLException, IOException {
        List<String> columns = new ArrayList<>();
        // generated columns (e.g. sessions.active_table_id) are calculated by MySQL and cannot be inserted
        try (PreparedStatement ps = con.prepareStatement("SELECT column_name FROM information_schema.columns WHERE table_schema = DATABASE() "
                + "AND table_name = ? AND extra NOT LIKE '%GENERATED%' ORDER BY ordinal_position")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) columns.add("`" + rs.getString(1) + "`");
            }
        }
        String insert = "INSERT INTO `" + table + "` (" + String.join(", ", columns) + ") VALUES\n";
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery("SELECT " + String.join(", ", columns) + " FROM `" + table + "`")) {
            ResultSetMetaData meta = rs.getMetaData();
            int inBatch = 0;
            while (rs.next()) {
                out.write(inBatch == 0 ? insert : ",\n");
                out.write("(");
                for (int i = 1; i <= columns.size(); i++) {
                    if (i > 1) out.write(", ");
                    out.write(value(rs, i, meta.getColumnType(i)));
                }
                out.write(")");
                if (++inBatch == ROWS_PER_INSERT) { out.write(";\n"); inBatch = 0; }
            }
            if (inBatch > 0) out.write(";\n");
        }
    }

    /** One value as SQL text: NULL, a number, 1/0 for true/false, or 'quoted text'. */
    private static String value(ResultSet rs, int i, int type) throws SQLException {
        if (type == Types.BIT || type == Types.BOOLEAN) {
            boolean b = rs.getBoolean(i);
            return rs.wasNull() ? "NULL" : (b ? "1" : "0");
        }
        String s = rs.getString(i);
        if (s == null) return "NULL";
        switch (type) {
            case Types.TINYINT: case Types.SMALLINT: case Types.INTEGER: case Types.BIGINT:
            case Types.DECIMAL: case Types.NUMERIC: case Types.FLOAT: case Types.DOUBLE: case Types.REAL:
                return s;
            default:
                return "'" + s.replace("\\", "\\\\").replace("'", "''").replace("\n", "\\n").replace("\r", "\\r") + "'";
        }
    }

    /**
     * Replaces ALL data of the current database with the data in a backup.
     * Every existing table is dropped first, then the tables of the backup are created and filled.
     */
    public static void restore(Connection con, String backupText) throws SQLException, BusinessException {
        if (!backupText.startsWith(HEADER)) throw new BusinessException("This file is not a CUE 147 backup.");
        List<String> statements = SchemaMigrator.splitStatements(backupText);
        if (statements.stream().noneMatch(s -> s.startsWith("CREATE TABLE"))) throw new BusinessException("This backup file is empty or damaged.");
        try (Statement st = con.createStatement()) {
            st.execute("SET FOREIGN_KEY_CHECKS = 0");
            try {
                for (String table : baseTables(con)) st.execute("DROP TABLE IF EXISTS `" + table + "`");
                for (String s : statements) st.execute(s);
            } finally {
                st.execute("SET FOREIGN_KEY_CHECKS = 1");
            }
        }
    }

    private static List<String> baseTables(Connection con) throws SQLException {
        List<String> list = new ArrayList<>();
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery("SELECT table_name FROM information_schema.tables "
                + "WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE' ORDER BY table_name")) {
            while (rs.next()) list.add(rs.getString(1));
        }
        return list;
    }
}
