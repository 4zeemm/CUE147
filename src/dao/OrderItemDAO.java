package dao;

import database.DBConnection;
import model.OrderItem;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/** Food and drinks ordered by a table during a game (stored with the date and time they were ordered). */
public class OrderItemDAO {

    public List<OrderItem> findBySession(int sessionId) throws SQLException {
        try (Connection con = DBConnection.getConnection()) {
            return findBySession(con, sessionId);
        }
    }

    static List<OrderItem> findBySession(Connection con, int sessionId) throws SQLException {
        List<OrderItem> list = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement("SELECT * FROM order_items WHERE session_id = ? ORDER BY order_item_id")) {
            ps.setInt(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new OrderItem(rs.getInt("order_item_id"), rs.getInt("item_id"), rs.getString("item_name"), rs.getString("category"),
                            rs.getBigDecimal("unit_price"), rs.getInt("quantity"), rs.getTimestamp("added_at").toLocalDateTime()));
                }
            }
        }
        return list;
    }

    /** Adds items to a RUNNING game. */
    public void add(int sessionId, OrderItem item) throws SQLException {
        try (Connection con = DBConnection.getConnection()) {
            insert(con, sessionId, List.of(item));
        }
    }

    /** Removes one line from the order of a RUNNING game. */
    public void delete(int orderItemId) throws SQLException {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("DELETE FROM order_items WHERE order_item_id = ? AND session_id IN "
                     + "(SELECT session_id FROM sessions WHERE status = 'ACTIVE')")) {
            ps.setInt(1, orderItemId);
            ps.executeUpdate();
        }
    }

    /** Replaces the whole order of a game with the given list (used when the bill is saved). */
    public void replaceAll(int sessionId, List<OrderItem> items) throws SQLException {
        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                replaceAll(con, sessionId, items);
                con.commit();
            } catch (SQLException e) {
                con.rollback();
                throw e;
            }
        }
    }

    static void replaceAll(Connection con, int sessionId, List<OrderItem> items) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("DELETE FROM order_items WHERE session_id = ?")) {
            ps.setInt(1, sessionId);
            ps.executeUpdate();
        }
        insert(con, sessionId, items);
    }

    private static void insert(Connection con, int sessionId, List<OrderItem> items) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("INSERT INTO order_items "
                + "(session_id, item_id, item_name, category, unit_price, quantity, added_at) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            for (OrderItem i : items) {
                ps.setInt(1, sessionId);
                ps.setInt(2, i.getMenuItemId());
                ps.setString(3, i.getName());
                ps.setString(4, i.getCategory());
                ps.setBigDecimal(5, i.getUnitPrice());
                ps.setInt(6, i.getQuantity());
                ps.setTimestamp(7, Timestamp.valueOf(i.getAddedAt()));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }
}
