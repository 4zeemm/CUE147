package dao;

import database.DBConnection;
import model.Bill;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Database operations for bills and their payments. Bills are never deleted. */
public class BillDAO {

    // COALESCE: a cancelled bill of a REOPENED game has no end time in its session yet, so we show the time the bill was made.
    // One query joins bills + sessions + customers + tables + payments to get everything for a bill.
    private static final String SELECT_BILL =
            "SELECT b.*, s.start_time, COALESCE(s.end_time, b.created_at) AS end_time, c.name AS customer_name, t.table_name, "
            + "p.status AS pay_status, p.method AS pay_method, p.paid_at, "
            + "ue.full_name AS served_by, ur.full_name AS received_by_name "
            + "FROM bills b "
            + "JOIN sessions s ON s.session_id = b.session_id "
            + "JOIN customers c ON c.customer_id = s.customer_id "
            + "JOIN game_tables t ON t.table_id = s.table_id "
            + "JOIN payments p ON p.bill_id = b.bill_id "
            + "LEFT JOIN users ue ON ue.user_id = s.ended_by "
            + "LEFT JOIN users ur ON ur.user_id = p.received_by ";

    private Bill readBill(ResultSet rs) throws SQLException {
        Bill bill = new Bill(rs.getInt("bill_id"), rs.getString("bill_number"), rs.getInt("session_id"),
                rs.getInt("billed_minutes"), rs.getBigDecimal("hourly_rate"), rs.getBigDecimal("subtotal"),
                rs.getBigDecimal("discount_percent"), rs.getBigDecimal("final_amount"),
                rs.getTimestamp("created_at").toLocalDateTime());
        bill.setSessionDetails(rs.getString("customer_name"), rs.getString("table_name"),
                rs.getTimestamp("start_time").toLocalDateTime(), rs.getTimestamp("end_time").toLocalDateTime());
        Timestamp paidAt = rs.getTimestamp("paid_at");
        bill.setPaymentDetails(rs.getString("pay_status"), rs.getString("pay_method"),
                paidAt == null ? null : paidAt.toLocalDateTime());
        bill.setCorrectionDetails(rs.getString("status"), rs.getString("cancel_reason"));
        bill.setStaffDetails(rs.getString("served_by"), rs.getString("received_by_name"));
        return bill;
    }

    public Bill findById(int billId) throws SQLException {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(SELECT_BILL + "WHERE b.bill_id = ?")) {
            ps.setInt(1, billId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? readBill(rs) : null;
            }
        }
    }

    /** Bill history, newest first. The search text matches the customer name or the bill number. */
    public List<Bill> search(String text, boolean unpaidOnly) throws SQLException {
        List<Bill> list = new ArrayList<>();
        String sql = SELECT_BILL + "WHERE (c.name LIKE ? OR b.bill_number LIKE ?) "
                + (unpaidOnly ? "AND p.status = 'PENDING' AND b.status = 'ACTIVE' " : "") + "ORDER BY b.created_at DESC, b.bill_id DESC";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, "%" + text + "%");
            ps.setString(2, "%" + text + "%");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(readBill(rs));
            }
        }
        return list;
    }

    /** Previous bills of one customer (for "View customer history"). */
    public List<Bill> findByCustomer(int customerId) throws SQLException {
        List<Bill> list = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(SELECT_BILL + "WHERE s.customer_id = ? ORDER BY b.created_at DESC")) {
            ps.setInt(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(readBill(rs));
            }
        }
        return list;
    }

    /**
     * Records a payment (CASH or UPI). Two updates in one transaction:
     * the payment becomes PAID and the customer's total spending goes up.
     */
    public void recordPayment(int billId, String method, int receivedBy) throws SQLException {
        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                BigDecimal amount;
                try (PreparedStatement ps = con.prepareStatement(
                        "SELECT p.amount, s.customer_id FROM payments p JOIN bills b ON b.bill_id = p.bill_id "
                        + "JOIN sessions s ON s.session_id = b.session_id "
                        + "WHERE p.bill_id = ? AND p.status = 'PENDING' AND b.status = 'ACTIVE'")) {
                    ps.setInt(1, billId);
                    int customerId;
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new SQLException("Bill is already paid");
                        amount = rs.getBigDecimal(1);
                        customerId = rs.getInt(2);
                    }
                    try (PreparedStatement update = con.prepareStatement(
                            "UPDATE payments SET status = 'PAID', method = ?, paid_at = ?, received_by = ? WHERE bill_id = ?")) {
                        update.setString(1, method);
                        update.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
                        update.setInt(3, receivedBy);
                        update.setInt(4, billId);
                        update.executeUpdate();
                    }
                    try (PreparedStatement update = con.prepareStatement(
                            "UPDATE customers SET total_spent = total_spent + ? WHERE customer_id = ?")) {
                        update.setBigDecimal(1, amount);
                        update.setInt(2, customerId);
                        update.executeUpdate();
                    }
                }
                con.commit();
            } catch (SQLException e) {
                con.rollback();
                throw e;
            }
        }
    }

    /**
     * Cancels a wrong bill. The bill is NOT deleted: it gets status CANCELLED plus the reason and who did it.
     * Its payment becomes CANCELLED (if it was not paid yet) or REFUNDED (if the customer had paid).
     * A refund also takes the money back from the customer's total spending. One transaction.
     */
    public void cancelBill(int billId, int userId, String reason) throws SQLException {
        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                String paymentStatus;
                BigDecimal amount;
                int customerId;
                try (PreparedStatement ps = con.prepareStatement(
                        "SELECT p.status, p.amount, s.customer_id FROM payments p JOIN bills b ON b.bill_id = p.bill_id "
                        + "JOIN sessions s ON s.session_id = b.session_id WHERE p.bill_id = ? AND b.status = 'ACTIVE'")) {
                    ps.setInt(1, billId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new SQLException("Bill not found or already cancelled");
                        paymentStatus = rs.getString(1);
                        amount = rs.getBigDecimal(2);
                        customerId = rs.getInt(3);
                    }
                }
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE bills SET status = 'CANCELLED', cancel_reason = ?, cancelled_by = ?, cancelled_at = ? WHERE bill_id = ?")) {
                    ps.setString(1, reason);
                    ps.setInt(2, userId);
                    ps.setTimestamp(3, new Timestamp(System.currentTimeMillis()));
                    ps.setInt(4, billId);
                    ps.executeUpdate();
                }
                boolean wasPaid = paymentStatus.equals("PAID");
                try (PreparedStatement ps = con.prepareStatement("UPDATE payments SET status = ? WHERE bill_id = ?")) {
                    ps.setString(1, wasPaid ? "REFUNDED" : "CANCELLED");
                    ps.setInt(2, billId);
                    ps.executeUpdate();
                }
                if (wasPaid) {
                    try (PreparedStatement ps = con.prepareStatement(
                            "UPDATE customers SET total_spent = total_spent - ? WHERE customer_id = ?")) {
                        ps.setBigDecimal(1, amount);
                        ps.setInt(2, customerId);
                        ps.executeUpdate();
                    }
                }
                con.commit();
            } catch (SQLException e) {
                con.rollback();
                throw e;
            }
        }
    }

    /** Corrects the payment method (CASH or UPI) of a bill that was already PAID. Returns false if nothing changed. */
    public boolean changePaymentMethod(int billId, String newMethod) throws SQLException {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("UPDATE payments SET method = ? WHERE bill_id = ? AND status = 'PAID'")) {
            ps.setString(1, newMethod);
            ps.setInt(2, billId);
            return ps.executeUpdate() == 1;
        }
    }

    /** Money received (PAID payments) between two dates, both included. */
    public BigDecimal revenueBetween(LocalDate from, LocalDate to) throws SQLException {
        String sql = "SELECT COALESCE(SUM(amount), 0) FROM payments WHERE status = 'PAID' AND DATE(paid_at) BETWEEN ? AND ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(from));
            ps.setDate(2, Date.valueOf(to));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBigDecimal(1);
            }
        }
    }

    public int countUnpaid() throws SQLException {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM payments WHERE status = 'PENDING'");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    /** Total of all bills created between two dates (paid or not). */
    public BigDecimal billedBetween(LocalDate from, LocalDate to) throws SQLException {
        String sql = "SELECT COALESCE(SUM(final_amount), 0) FROM bills WHERE status = 'ACTIVE' AND DATE(created_at) BETWEEN ? AND ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(from));
            ps.setDate(2, Date.valueOf(to));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBigDecimal(1);
            }
        }
    }
}
