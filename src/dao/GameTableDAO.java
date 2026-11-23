package dao;

import database.DBConnection;
import model.GameTable;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/** Database operations for the game_tables table. */
public class GameTableDAO {

    /**
     * Reads all active tables. LEFT JOIN adds the running session (if any),
     * so a table with an ACTIVE session is OCCUPIED, otherwise AVAILABLE.
     */
    public List<GameTable> findAll() throws SQLException {
        List<GameTable> list = new ArrayList<>();
        String sql = "SELECT t.table_id, t.table_name, t.table_type, t.is_under_maintenance, s.session_id, s.start_time, c.name "
                + "FROM game_tables t "
                + "LEFT JOIN sessions s ON s.table_id = t.table_id AND s.status = 'ACTIVE' "
                + "LEFT JOIN customers c ON c.customer_id = s.customer_id "
                + "WHERE t.is_active = TRUE ORDER BY t.table_type, t.table_name";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                GameTable t = new GameTable(rs.getInt("table_id"), rs.getString("table_name"), rs.getString("table_type"));
                t.setUnderMaintenance(rs.getBoolean("is_under_maintenance"));
                int sessionId = rs.getInt("session_id");   // 0 when there is no running session
                if (sessionId > 0) {
                    t.setActiveSession(sessionId, rs.getTimestamp("start_time").toLocalDateTime(), rs.getString("name"));
                }
                list.add(t);
            }
        }
        return list;
    }

    public int add(String name, String type) throws SQLException {
        String sql = "INSERT INTO game_tables (table_name, table_type) VALUES (?, ?)";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, type);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    public void update(int id, String name, String type) throws SQLException {
        String sql = "UPDATE game_tables SET table_name = ?, table_type = ? WHERE table_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, type);
            ps.setInt(3, id);
            ps.executeUpdate();
        }
    }

    /**
     * Removes a table. If the table has game history we must keep it (bills point to it),
     * so we only switch it off (is_active = FALSE). Returns true if the row was really deleted.
     */
    public boolean remove(int id) throws SQLException {
        try (Connection con = DBConnection.getConnection()) {
            boolean hasHistory;
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT (SELECT COUNT(*) FROM sessions WHERE table_id = ?) + (SELECT COUNT(*) FROM bookings WHERE table_id = ?)")) {
                ps.setInt(1, id);
                ps.setInt(2, id);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    hasHistory = rs.getInt(1) > 0;
                }
            }
            String sql = hasHistory ? "UPDATE game_tables SET is_active = FALSE WHERE table_id = ?"
                                    : "DELETE FROM game_tables WHERE table_id = ?";
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setInt(1, id);
                ps.executeUpdate();
            }
            return !hasHistory;
        }
    }

    /** Puts a table under maintenance (true) or back in service (false). */
    public void setMaintenance(int tableId, boolean underMaintenance) throws SQLException {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("UPDATE game_tables SET is_under_maintenance = ? WHERE table_id = ?")) {
            ps.setBoolean(1, underMaintenance);
            ps.setInt(2, tableId);
            ps.executeUpdate();
        }
    }

    /** Asks MySQL right now (the table object on screen may be out of date). */
    public boolean isUnderMaintenance(int tableId) throws SQLException {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT is_under_maintenance FROM game_tables WHERE table_id = ?")) {
            ps.setInt(1, tableId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getBoolean(1);
            }
        }
    }

    public int countUnderMaintenance() throws SQLException {
        String sql = "SELECT COUNT(*) FROM game_tables WHERE is_active = TRUE AND is_under_maintenance = TRUE";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    public int countActiveTables() throws SQLException {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM game_tables WHERE is_active = TRUE");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
