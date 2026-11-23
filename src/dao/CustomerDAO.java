package dao;

import database.DBConnection;
import model.Customer;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/** Database operations for the customers table (CRUD = Create, Read, Update, Delete). */
public class CustomerDAO {

    private Customer readCustomer(ResultSet rs) throws SQLException {
        return new Customer(rs.getInt("customer_id"), rs.getString("name"), rs.getString("phone"),
                rs.getString("gender"), rs.getDate("registered_on").toLocalDate(),
                rs.getInt("total_visits"), rs.getBigDecimal("total_spent"), rs.getBoolean("is_walk_in"));
    }

    /** CREATE: saves a new customer. Fails with MySQL error 1062 if the phone number already exists. */
    public int add(Customer c) throws SQLException {
        String sql = "INSERT INTO customers (name, phone, gender, registered_on) VALUES (?, ?, ?, ?)";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, c.getName());
            ps.setString(2, c.getPhone());
            ps.setString(3, c.getGender());
            ps.setDate(4, Date.valueOf(c.getRegisteredOn()));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    /** UPDATE */
    public void update(Customer c) throws SQLException {
        String sql = "UPDATE customers SET name = ?, phone = ?, gender = ? WHERE customer_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, c.getName());
            ps.setString(2, c.getPhone());
            ps.setString(3, c.getGender());
            ps.setInt(4, c.getId());
            ps.executeUpdate();
        }
    }

    /** READ: all customers. */
    public List<Customer> findAll() throws SQLException {
        return search("");
    }

    /** READ: customers whose name or phone contains the text. An empty text returns everyone. */
    public List<Customer> search(String text) throws SQLException {
        List<Customer> list = new ArrayList<>();
        String sql = "SELECT * FROM customers WHERE name LIKE ? OR phone LIKE ? ORDER BY is_walk_in DESC, name";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, "%" + text + "%");
            ps.setString(2, "%" + text + "%");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(readCustomer(rs));
            }
        }
        return list;
    }

    public Customer findById(int id) throws SQLException {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT * FROM customers WHERE customer_id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? readCustomer(rs) : null;
            }
        }
    }

    /**
     * Deletes a customer who never played, booked or became a member.
     * A customer WITH history is kept (their bills must stay), and false is returned.
     */
    public boolean deleteIfNoHistory(int customerId) throws SQLException {
        try (Connection con = DBConnection.getConnection()) {
            String countSql = "SELECT (SELECT COUNT(*) FROM sessions WHERE customer_id = ?) + "
                    + "(SELECT COUNT(*) FROM bookings WHERE customer_id = ?) + (SELECT COUNT(*) FROM memberships WHERE customer_id = ?)";
            try (PreparedStatement ps = con.prepareStatement(countSql)) {
                ps.setInt(1, customerId);
                ps.setInt(2, customerId);
                ps.setInt(3, customerId);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    if (rs.getInt(1) > 0) return false;   // has history: keep it
                }
            }
            try (PreparedStatement ps = con.prepareStatement("DELETE FROM customers WHERE customer_id = ? AND is_walk_in = FALSE")) {
                ps.setInt(1, customerId);
                return ps.executeUpdate() == 1;
            }
        }
    }

    /** Number of real customers (the two walk-in guest rows are not counted). */
    public int countRegistered() throws SQLException {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM customers WHERE is_walk_in = FALSE");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    /** Number of different customers who played on the given day. */
    public int countPlayedOn(java.time.LocalDate day) throws SQLException {
        String sql = "SELECT COUNT(DISTINCT customer_id) FROM sessions WHERE DATE(start_time) = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(day));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }
}
