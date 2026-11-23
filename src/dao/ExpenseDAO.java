package dao;

import database.DBConnection;
import model.Expense;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Database operations for the expenses table.
 * Old records are never overwritten: an edit first copies the old values to expense_changes,
 * and "delete" only marks the expense DELETED (it stays in the database and leaves the totals).
 */
public class ExpenseDAO {

    public void add(Expense e, int userId) throws SQLException {
        String sql = "INSERT INTO expenses (category, amount, expense_date, description, payment_method, recorded_by, recorded_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, NOW())";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, e.getCategory());
            ps.setBigDecimal(2, e.getAmount());
            ps.setDate(3, Date.valueOf(e.getDate()));
            ps.setString(4, e.getDescription());
            ps.setString(5, e.getPaymentMethod());
            ps.setInt(6, userId);
            ps.executeUpdate();
        }
    }

    /** Expenses that count (not deleted), newest first. */
    public List<Expense> findAll() throws SQLException { return find("ACTIVE"); }

    /** Expenses that were deleted - kept for the record, not counted in any total. */
    public List<Expense> findDeleted() throws SQLException { return find("DELETED"); }

    private List<Expense> find(String status) throws SQLException {
        List<Expense> list = new ArrayList<>();
        String sql = "SELECT e.*, u.full_name FROM expenses e JOIN users u ON u.user_id = e.recorded_by "
                + "WHERE e.status = '" + status + "' ORDER BY e.expense_date DESC, e.expense_id DESC";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Expense e = new Expense(rs.getInt("expense_id"), rs.getString("category"), rs.getBigDecimal("amount"),
                        rs.getDate("expense_date").toLocalDate(), rs.getString("description"), rs.getString("payment_method"));
                e.setRecordedBy(rs.getString("full_name"));
                list.add(e);
            }
        }
        return list;
    }

    /**
     * Changes an expense (found by its id). In ONE transaction the old values are first copied to
     * expense_changes, so the history is never lost. Returns false if the expense does not exist (or was deleted).
     */
    public boolean update(Expense e, int userId) throws SQLException {
        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                try (PreparedStatement ps = con.prepareStatement("INSERT INTO expense_changes (expense_id, changed_at, changed_by, "
                        + "old_category, old_amount, old_date, old_description, old_method) "
                        + "SELECT expense_id, NOW(), ?, category, amount, expense_date, description, payment_method "
                        + "FROM expenses WHERE expense_id = ? AND status = 'ACTIVE'")) {
                    ps.setInt(1, userId);
                    ps.setInt(2, e.getId());
                    if (ps.executeUpdate() == 0) { con.rollback(); return false; }
                }
                try (PreparedStatement ps = con.prepareStatement("UPDATE expenses SET category = ?, amount = ?, expense_date = ?, "
                        + "description = ?, payment_method = ? WHERE expense_id = ?")) {
                    ps.setString(1, e.getCategory());
                    ps.setBigDecimal(2, e.getAmount());
                    ps.setDate(3, Date.valueOf(e.getDate()));
                    ps.setString(4, e.getDescription());
                    ps.setString(5, e.getPaymentMethod());
                    ps.setInt(6, e.getId());
                    ps.executeUpdate();
                }
                con.commit();
                return true;
            } catch (SQLException ex) {
                con.rollback();
                throw ex;
            }
        }
    }

    /** The earlier versions of one expense, oldest first: "23 Nov 17:30 by Club Owner: RENT Rs. 1500.00 on 2026-11-01 (CASH)". */
    public List<String> findChanges(int expenseId) throws SQLException {
        List<String> list = new ArrayList<>();
        String sql = "SELECT c.*, u.full_name FROM expense_changes c JOIN users u ON u.user_id = c.changed_by "
                + "WHERE c.expense_id = ? ORDER BY c.change_id";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, expenseId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(String.format("Changed %s by %s. Before: %s Rs. %.2f on %s (%s) %s",
                            rs.getTimestamp("changed_at").toLocalDateTime().toString().replace('T', ' '), rs.getString("full_name"),
                            rs.getString("old_category"), rs.getBigDecimal("old_amount"), rs.getDate("old_date"),
                            rs.getString("old_method"), rs.getString("old_description") == null ? "" : rs.getString("old_description")));
                }
            }
        }
        return list;
    }

    /**
     * "Deletes" ONE expense, found by its unique id: it is marked DELETED with who did it and when.
     * The row stays in the database (old records are never removed) but no longer counts in any total.
     * Returns true if it was deleted now, false if that id does not exist or was already deleted.
     */
    public boolean delete(int expenseId, int userId) throws SQLException {
        String sql = "UPDATE expenses SET status = 'DELETED', deleted_by = ?, deleted_at = NOW() WHERE expense_id = ? AND status = 'ACTIVE'";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, expenseId);
            return ps.executeUpdate() == 1;
        }
    }

    public BigDecimal totalBetween(LocalDate from, LocalDate to) throws SQLException {
        String sql = "SELECT COALESCE(SUM(amount), 0) FROM expenses WHERE status = 'ACTIVE' AND expense_date BETWEEN ? AND ?";
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
