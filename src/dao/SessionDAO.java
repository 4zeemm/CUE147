package dao;

import database.DBConnection;
import model.Bill;
import model.GameSession;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Database operations for the sessions table (and the bill that is created when a session ends). */
public class SessionDAO {

    private static final String SELECT_SESSION =
            "SELECT s.*, t.table_name, c.name FROM sessions s "
            + "JOIN game_tables t ON t.table_id = s.table_id "
            + "JOIN customers c ON c.customer_id = s.customer_id ";

    private GameSession readSession(ResultSet rs) throws SQLException {
        Timestamp end = rs.getTimestamp("end_time");
        return new GameSession(rs.getInt("session_id"), rs.getInt("table_id"), rs.getString("table_name"),
                rs.getInt("customer_id"), rs.getString("name"), rs.getTimestamp("start_time").toLocalDateTime(),
                end == null ? null : end.toLocalDateTime(), rs.getBigDecimal("hourly_rate"), rs.getString("status"));
    }

    /** Creates an ACTIVE session. MySQL error 1062 means the table already has an active session. */
    public int start(int tableId, int customerId, int userId, LocalDateTime startTime, BigDecimal rate) throws SQLException {
        String sql = "INSERT INTO sessions (table_id, customer_id, started_by, start_time, hourly_rate) VALUES (?, ?, ?, ?, ?)";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, tableId);
            ps.setInt(2, customerId);
            ps.setInt(3, userId);
            ps.setTimestamp(4, Timestamp.valueOf(startTime));
            ps.setBigDecimal(5, rate);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    public GameSession findById(int sessionId) throws SQLException {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(SELECT_SESSION + "WHERE s.session_id = ?")) {
            ps.setInt(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? readSession(rs) : null;
            }
        }
    }

    /** All sessions that are running now. */
    public List<GameSession> findActive() throws SQLException {
        List<GameSession> list = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(SELECT_SESSION + "WHERE s.status = 'ACTIVE' ORDER BY s.start_time");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(readSession(rs));
        }
        return list;
    }

    public int countActive() throws SQLException {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM sessions WHERE status = 'ACTIVE'");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    /**
     * Ends a session and saves its bill. Four SQL statements must succeed TOGETHER or not at all,
     * so we use a TRANSACTION: setAutoCommit(false) ... commit() (or rollback() if something fails).
     * Returns the id of the new bill.
     */
    public int endSession(int sessionId, int customerId, LocalDateTime endTime, int billedMinutes, BigDecimal rate,
                          BigDecimal subtotal, BigDecimal discountPercent, BigDecimal finalAmount, int endedBy) throws SQLException {
        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                // 1. mark the session as finished
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE sessions SET end_time = ?, status = 'COMPLETED', ended_by = ? WHERE session_id = ? AND status = 'ACTIVE'")) {
                    ps.setTimestamp(1, Timestamp.valueOf(endTime));
                    ps.setInt(2, endedBy);
                    ps.setInt(3, sessionId);
                    if (ps.executeUpdate() == 0) throw new SQLException("Session is not active");
                }
                // 2. unique bill number like CUE20261104-0003
                String billNumber = nextBillNumber(con, endTime.toLocalDate());
                int billId;
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO bills (bill_number, session_id, billed_minutes, hourly_rate, subtotal, discount_percent, final_amount, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, billNumber);
                    ps.setInt(2, sessionId);
                    ps.setInt(3, billedMinutes);
                    ps.setBigDecimal(4, rate);
                    ps.setBigDecimal(5, subtotal);
                    ps.setBigDecimal(6, discountPercent);
                    ps.setBigDecimal(7, finalAmount);
                    ps.setTimestamp(8, Timestamp.valueOf(endTime));
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        keys.next();
                        billId = keys.getInt(1);
                    }
                }
                // 3. a PENDING payment row, waiting for the customer to pay
                try (PreparedStatement ps = con.prepareStatement("INSERT INTO payments (bill_id, amount) VALUES (?, ?)")) {
                    ps.setInt(1, billId);
                    ps.setBigDecimal(2, finalAmount);
                    ps.executeUpdate();
                }
                // 4. one more visit for the customer
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE customers SET total_visits = total_visits + 1 WHERE customer_id = ?")) {
                    ps.setInt(1, customerId);
                    ps.executeUpdate();
                }
                con.commit();
                return billId;
            } catch (SQLException e) {
                con.rollback();   // undo everything
                throw e;
            }
        }
    }

    /**
     * Cancels a game that was started by mistake. No bill is made. The row stays in the table
     * with status CANCELLED, so we still know who cancelled it and why.
     * Returns false if the session was not ACTIVE any more.
     */
    public boolean cancelSession(int sessionId, int userId, String reason) throws SQLException {
        String sql = "UPDATE sessions SET status = 'CANCELLED', cancelled_by = ?, cancel_reason = ? WHERE session_id = ? AND status = 'ACTIVE'";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, reason);
            ps.setInt(3, sessionId);
            return ps.executeUpdate() == 1;
        }
    }

    /** Moves the start time of a RUNNING game (for example the staff forgot to press Start). */
    public boolean adjustStartTime(int sessionId, LocalDateTime newStartTime) throws SQLException {
        String sql = "UPDATE sessions SET start_time = ? WHERE session_id = ? AND status = 'ACTIVE'";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.valueOf(newStartTime));
            ps.setInt(2, sessionId);
            return ps.executeUpdate() == 1;
        }
    }

    /**
     * Brings an accidentally ended game back to life. One transaction:
     *  1. the unpaid bill is marked CANCELLED (kept, not deleted) and its payment CANCELLED,
     *  2. the session becomes ACTIVE again (the unique key refuses this if the table is busy again),
     *  3. the extra visit is taken back.
     */
    public void reopenSession(int sessionId, int billId, int customerId, int userId, String reason) throws SQLException {
        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE bills SET status = 'CANCELLED', cancel_reason = ?, cancelled_by = ?, cancelled_at = ? "
                        + "WHERE bill_id = ? AND status = 'ACTIVE'")) {
                    ps.setString(1, reason);
                    ps.setInt(2, userId);
                    ps.setTimestamp(3, new Timestamp(System.currentTimeMillis()));
                    ps.setInt(4, billId);
                    if (ps.executeUpdate() == 0) throw new SQLException("Bill is not active");
                }
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE payments SET status = 'CANCELLED' WHERE bill_id = ? AND status = 'PENDING'")) {
                    ps.setInt(1, billId);
                    if (ps.executeUpdate() == 0) throw new SQLException("Bill is already paid");
                }
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE sessions SET status = 'ACTIVE', end_time = NULL, ended_by = NULL WHERE session_id = ? AND status = 'COMPLETED'")) {
                    ps.setInt(1, sessionId);
                    if (ps.executeUpdate() == 0) throw new SQLException("Session is not completed");
                }
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE customers SET total_visits = total_visits - 1 WHERE customer_id = ? AND total_visits > 0")) {
                    ps.setInt(1, customerId);
                    ps.executeUpdate();
                }
                con.commit();
            } catch (SQLException e) {
                con.rollback();
                throw e;
            }
        }
    }

    private String nextBillNumber(Connection con, LocalDate day) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM bills WHERE DATE(created_at) = ?")) {
            ps.setDate(1, Date.valueOf(day));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return String.format("CUE%s-%04d", day.toString().replace("-", ""), rs.getInt(1) + 1);
            }
        }
    }

    /**
     * Usage per table between two dates: {table name, type, games, hours, revenue}.
     * Only finished games are counted; revenue is the final bill amount.
     */
    public List<String[]> usageByTable(LocalDate from, LocalDate to) throws SQLException {
        List<String[]> rows = new ArrayList<>();
        String sql = "SELECT t.table_name, t.table_type, COUNT(b.bill_id) AS games, "
                + "COALESCE(SUM(b.billed_minutes), 0) AS minutes, COALESCE(SUM(b.final_amount), 0) AS revenue "
                + "FROM game_tables t "
                + "LEFT JOIN sessions s ON s.table_id = t.table_id AND s.status <> 'CANCELLED' AND DATE(s.start_time) BETWEEN ? AND ? "
                + "LEFT JOIN bills b ON b.session_id = s.session_id AND b.status = 'ACTIVE' "
                + "WHERE t.is_active = TRUE GROUP BY t.table_id, t.table_name, t.table_type ORDER BY games DESC, t.table_name";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(from));
            ps.setDate(2, Date.valueOf(to));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new String[]{rs.getString(1), rs.getString(2), String.valueOf(rs.getInt(3)),
                            String.format("%.1f", rs.getInt(4) / 60.0), rs.getBigDecimal(5).toPlainString()});
                }
            }
        }
        return rows;
    }
}
