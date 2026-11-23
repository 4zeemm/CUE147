package dao;

import database.DBConnection;
import model.Expense;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Database operations for the expenses table. */
public class ExpenseDAO {

    public void add(Expense e, int userId) throws SQLException {
        String sql = "INSERT INTO expenses (category, amount, expense_date, description, payment_method, recorded_by) VALUES (?, ?, ?, ?, ?, ?)";
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

    /** Expense history, newest first. */
    public List<Expense> findAll() throws SQLException {
        List<Expense> list = new ArrayList<>();
        String sql = "SELECT e.*, u.full_name FROM expenses e JOIN users u ON u.user_id = e.recorded_by "
                + "ORDER BY e.expense_date DESC, e.expense_id DESC";
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

    /** Changes the details of an existing expense. The row is found by its id. */
    public boolean update(Expense e) throws SQLException {
        String sql = "UPDATE expenses SET category = ?, amount = ?, expense_date = ?, description = ?, payment_method = ? WHERE expense_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, e.getCategory());
            ps.setBigDecimal(2, e.getAmount());
            ps.setDate(3, Date.valueOf(e.getDate()));
            ps.setString(4, e.getDescription());
            ps.setString(5, e.getPaymentMethod());
            ps.setInt(6, e.getId());
            return ps.executeUpdate() == 1;
        }
    }

    /**
     * Deletes ONE expense. The row is found by its unique id (the primary key expense_id),
     * so no other expense can be removed by mistake.
     * Returns true if a row was deleted, false if that id does not exist any more.
     */
    public boolean delete(int expenseId) throws SQLException {
        String sql = "DELETE FROM expenses WHERE expense_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, expenseId);
            return ps.executeUpdate() == 1;   // executeUpdate returns the number of deleted rows
        }
    }

    public BigDecimal totalBetween(LocalDate from, LocalDate to) throws SQLException {
        String sql = "SELECT COALESCE(SUM(amount), 0) FROM expenses WHERE expense_date BETWEEN ? AND ?";
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
