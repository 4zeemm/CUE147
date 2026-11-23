package dao;

import database.DBConnection;
import model.MenuItem;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/** The food & drinks menu. Items are never deleted (old orders point to them); they are switched off instead. */
public class MenuItemDAO {

    public List<MenuItem> findAll() throws SQLException { return find("SELECT * FROM menu_items ORDER BY category, name"); }

    public List<MenuItem> findAvailable() throws SQLException {
        return find("SELECT * FROM menu_items WHERE is_available = TRUE ORDER BY category, name");
    }

    private List<MenuItem> find(String sql) throws SQLException {
        List<MenuItem> list = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new MenuItem(rs.getInt("item_id"), rs.getString("name"), rs.getString("category"),
                        rs.getBigDecimal("price"), rs.getBoolean("is_available")));
            }
        }
        return list;
    }

    /** Adds an item. MySQL refuses a name that is already on the menu (error 1062). */
    public int add(String name, String category, BigDecimal price) throws SQLException {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("INSERT INTO menu_items (name, category, price) VALUES (?, ?, ?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, category);
            ps.setBigDecimal(3, price);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    /** Changes name, category and price (old orders keep their own copy of the old values). */
    public void update(int itemId, String name, String category, BigDecimal price) throws SQLException {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("UPDATE menu_items SET name = ?, category = ?, price = ? WHERE item_id = ?")) {
            ps.setString(1, name);
            ps.setString(2, category);
            ps.setBigDecimal(3, price);
            ps.setInt(4, itemId);
            ps.executeUpdate();
        }
    }

    public void setAvailable(int itemId, boolean available) throws SQLException {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("UPDATE menu_items SET is_available = ? WHERE item_id = ?")) {
            ps.setBoolean(1, available);
            ps.setInt(2, itemId);
            ps.executeUpdate();
        }
    }
}
