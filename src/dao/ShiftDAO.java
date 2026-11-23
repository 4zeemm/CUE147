package dao;

import database.DBConnection;
import model.Shift;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Database operations for the staff_shifts table. */
public class ShiftDAO {

    public boolean hasOpenShift(int userId) throws SQLException {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM staff_shifts WHERE user_id = ? AND shift_end IS NULL")) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        }
    }

    public void startShift(int userId, LocalDateTime time) throws SQLException {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("INSERT INTO staff_shifts (user_id, shift_start) VALUES (?, ?)")) {
            ps.setInt(1, userId);
            ps.setTimestamp(2, Timestamp.valueOf(time));
            ps.executeUpdate();
        }
    }

    public void endShift(int userId, LocalDateTime time) throws SQLException {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("UPDATE staff_shifts SET shift_end = ? WHERE user_id = ? AND shift_end IS NULL")) {
            ps.setTimestamp(1, Timestamp.valueOf(time));
            ps.setInt(2, userId);
            ps.executeUpdate();
        }
    }

    /** Recent shifts. userId = 0 means everybody (owner view). */
    public List<Shift> findRecent(int userId) throws SQLException {
        List<Shift> list = new ArrayList<>();
        String sql = "SELECT s.*, u.full_name FROM staff_shifts s JOIN users u ON u.user_id = s.user_id "
                + (userId > 0 ? "WHERE s.user_id = ? " : "") + "ORDER BY s.shift_start DESC LIMIT 50";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            if (userId > 0) ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Timestamp end = rs.getTimestamp("shift_end");
                    list.add(new Shift(rs.getInt("shift_id"), rs.getString("full_name"),
                            rs.getTimestamp("shift_start").toLocalDateTime(), end == null ? null : end.toLocalDateTime()));
                }
            }
        }
        return list;
    }
}
