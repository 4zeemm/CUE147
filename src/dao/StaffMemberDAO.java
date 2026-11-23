package dao;

import database.DBConnection;
import model.StaffMember;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/** The "Billed by" list. Names are never deleted (old bills point to them); people who left are switched off. */
public class StaffMemberDAO {

    public List<StaffMember> findAll() throws SQLException { return find("SELECT * FROM staff_members ORDER BY is_active DESC, name"); }

    public List<StaffMember> findActive() throws SQLException { return find("SELECT * FROM staff_members WHERE is_active = TRUE ORDER BY name"); }

    private List<StaffMember> find(String sql) throws SQLException {
        List<StaffMember> list = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(new StaffMember(rs.getInt("staff_id"), rs.getString("name"), rs.getBoolean("is_active")));
        }
        return list;
    }

    /** Adds a name. MySQL refuses a name that is already in the list (error 1062). */
    public int add(String name) throws SQLException {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("INSERT INTO staff_members (name) VALUES (?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    public void setActive(int staffId, boolean active) throws SQLException {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("UPDATE staff_members SET is_active = ? WHERE staff_id = ?")) {
            ps.setBoolean(1, active);
            ps.setInt(2, staffId);
            ps.executeUpdate();
        }
    }

    /** On a brand-new database the list starts with the names of the login accounts. */
    public void fillFromUsersIfEmpty() throws SQLException {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "INSERT IGNORE INTO staff_members (name) SELECT DISTINCT full_name FROM users "
                     + "WHERE is_active = TRUE AND NOT EXISTS (SELECT 1 FROM staff_members)")) {
            ps.executeUpdate();
        }
    }
}
