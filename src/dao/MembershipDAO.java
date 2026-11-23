package dao;

import database.DBConnection;
import model.Membership;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Database operations for the memberships table. */
public class MembershipDAO {

    private static final String SELECT_MEMBER =
            "SELECT m.*, c.name, c.phone FROM memberships m JOIN customers c ON c.customer_id = m.customer_id ";

    private Membership readMembership(ResultSet rs) throws SQLException {
        return new Membership(rs.getInt("membership_id"), rs.getInt("customer_id"), rs.getString("name"),
                rs.getString("phone"), rs.getDate("start_date").toLocalDate(), rs.getDate("expiry_date").toLocalDate(),
                rs.getBigDecimal("discount_percent"), rs.getString("status"));
    }

    public List<Membership> findAll() throws SQLException {
        List<Membership> list = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(SELECT_MEMBER + "ORDER BY m.expiry_date DESC");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(readMembership(rs));
        }
        return list;
    }

    /** The latest membership of a customer, or null. */
    public Membership findLatestForCustomer(int customerId) throws SQLException {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(SELECT_MEMBER + "WHERE m.customer_id = ? ORDER BY m.expiry_date DESC LIMIT 1")) {
            ps.setInt(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? readMembership(rs) : null;
            }
        }
    }

    public void add(int customerId, LocalDate start, LocalDate expiry, BigDecimal discountPercent) throws SQLException {
        String sql = "INSERT INTO memberships (customer_id, start_date, expiry_date, discount_percent) VALUES (?, ?, ?, ?)";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            ps.setDate(2, Date.valueOf(start));
            ps.setDate(3, Date.valueOf(expiry));
            ps.setBigDecimal(4, discountPercent);
            ps.executeUpdate();
        }
    }

    /** Renew = set a new expiry date and make the membership ACTIVE again. */
    public void renew(int membershipId, LocalDate newExpiry, BigDecimal discountPercent) throws SQLException {
        String sql = "UPDATE memberships SET expiry_date = ?, discount_percent = ?, status = 'ACTIVE' WHERE membership_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(newExpiry));
            ps.setBigDecimal(2, discountPercent);
            ps.setInt(3, membershipId);
            ps.executeUpdate();
        }
    }

    public void cancel(int membershipId) throws SQLException {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("UPDATE memberships SET status = 'CANCELLED' WHERE membership_id = ?")) {
            ps.setInt(1, membershipId);
            ps.executeUpdate();
        }
    }

    /** Discount percent a customer gets on the given day (0 if not an active member). */
    public BigDecimal discountFor(int customerId, LocalDate day) throws SQLException {
        String sql = "SELECT COALESCE(MAX(discount_percent), 0) FROM memberships "
                + "WHERE customer_id = ? AND status = 'ACTIVE' AND ? BETWEEN start_date AND expiry_date";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            ps.setDate(2, Date.valueOf(day));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBigDecimal(1);
            }
        }
    }

    /** Number of active members whose membership ends within the next few days. */
    public int countExpiringSoon(int days) throws SQLException {
        String sql = "SELECT COUNT(*) FROM memberships WHERE status = 'ACTIVE' AND expiry_date BETWEEN CURDATE() AND DATE_ADD(CURDATE(), INTERVAL ? DAY)";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, days);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }
}
