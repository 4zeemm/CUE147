package dao;

import database.DBConnection;
import model.MonthlyReport;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * The SQL for the monthly report. Money counts on the day it was received: payments with status PAID
 * (bills AND membership sales). Refunded or cancelled payments are not counted. Deleted expenses are not counted.
 */
public class ReportDAO {

    public MonthlyReport monthly(YearMonth month) throws SQLException {
        MonthlyReport r = new MonthlyReport();
        r.month = month;
        Date from = Date.valueOf(month.atDay(1));
        Date to = Date.valueOf(month.atEndOfMonth());
        try (Connection con = DBConnection.getConnection()) {
            // 1. paid bills: table time, food & drinks, discounts
            try (PreparedStatement ps = con.prepareStatement("SELECT COUNT(*), COALESCE(SUM(b.table_amount), 0), COALESCE(SUM(b.items_amount), 0), "
                    + "COALESCE(SUM(b.bill_discount_amount), 0) FROM payments p JOIN bills b ON b.bill_id = p.bill_id "
                    + "WHERE p.status = 'PAID' AND DATE(p.paid_at) BETWEEN ? AND ?")) {
                range(ps, from, to);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    r.paidBills = rs.getInt(1);
                    r.tableTime = rs.getBigDecimal(2);
                    r.foodAndDrinks = rs.getBigDecimal(3);
                    r.billDiscounts = rs.getBigDecimal(4);
                }
            }
            // 2. membership sales
            try (PreparedStatement ps = con.prepareStatement("SELECT COUNT(*), COALESCE(SUM(amount), 0) FROM payments "
                    + "WHERE membership_id IS NOT NULL AND status = 'PAID' AND DATE(paid_at) BETWEEN ? AND ?")) {
                range(ps, from, to);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    r.membershipsSold = rs.getInt(1);
                    r.memberships = rs.getBigDecimal(2);
                }
            }
            // 3. all money received (must equal 1 + 2; the tests check this)
            r.revenue = sum(con, "SELECT COALESCE(SUM(amount), 0) FROM payments WHERE status = 'PAID' AND DATE(paid_at) BETWEEN ? AND ?", from, to);
            // 4. cash and UPI (every part of a split payment counts separately)
            try (PreparedStatement ps = con.prepareStatement("SELECT pp.method, SUM(pp.amount) FROM payment_parts pp "
                    + "JOIN payments p ON p.payment_id = pp.payment_id WHERE p.status = 'PAID' AND DATE(p.paid_at) BETWEEN ? AND ? GROUP BY pp.method")) {
                range(ps, from, to);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        if (rs.getString(1).equals("CASH")) r.cash = rs.getBigDecimal(2); else r.upi = rs.getBigDecimal(2);
                    }
                }
            }
            // 5. expenses (deleted ones do not count)
            r.expenses = sum(con, "SELECT COALESCE(SUM(amount), 0) FROM expenses WHERE status = 'ACTIVE' AND expense_date BETWEEN ? AND ?", from, to);
            try (PreparedStatement ps = con.prepareStatement("SELECT category, SUM(amount) FROM expenses WHERE status = 'ACTIVE' "
                    + "AND expense_date BETWEEN ? AND ? GROUP BY category ORDER BY SUM(amount) DESC")) {
                range(ps, from, to);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) r.expensesByCategory.put(rs.getString(1), rs.getBigDecimal(2));
                }
            }
            // 6. discounts per occasion
            try (PreparedStatement ps = con.prepareStatement("SELECT COALESCE(b.discount_occasion, '(no occasion)'), SUM(b.bill_discount_amount) "
                    + "FROM payments p JOIN bills b ON b.bill_id = p.bill_id WHERE p.status = 'PAID' AND DATE(p.paid_at) BETWEEN ? AND ? "
                    + "AND b.bill_discount_amount > 0 GROUP BY 1 ORDER BY 2 DESC")) {
                range(ps, from, to);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) r.discountsByOccasion.put(rs.getString(1), rs.getBigDecimal(2));
                }
            }
            // 7. per "Billed by" person: bills and membership sales together
            String byStaff = "SELECT name, COUNT(*), SUM(revenue), SUM(discount) FROM ("
                    + " SELECT COALESCE(sm.name, '(not recorded)') AS name, p.amount AS revenue, b.bill_discount_amount AS discount"
                    + " FROM payments p JOIN bills b ON b.bill_id = p.bill_id LEFT JOIN staff_members sm ON sm.staff_id = b.billed_by_staff_id"
                    + " WHERE p.status = 'PAID' AND DATE(p.paid_at) BETWEEN ? AND ?"
                    + " UNION ALL"
                    + " SELECT COALESCE(sm.name, '(not recorded)'), p.amount, 0"
                    + " FROM payments p JOIN memberships m ON m.membership_id = p.membership_id LEFT JOIN staff_members sm ON sm.staff_id = m.billed_by_staff_id"
                    + " WHERE p.status = 'PAID' AND DATE(p.paid_at) BETWEEN ? AND ?"
                    + ") x GROUP BY name ORDER BY SUM(revenue) DESC";
            try (PreparedStatement ps = con.prepareStatement(byStaff)) {
                ps.setDate(1, from); ps.setDate(2, to); ps.setDate(3, from); ps.setDate(4, to);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) r.byStaff.add(new MonthlyReport.StaffTotal(rs.getString(1), rs.getInt(2), rs.getBigDecimal(3), rs.getBigDecimal(4)));
                }
            }
            // 8. revenue per day (for the chart)
            for (int d = 1; d <= month.lengthOfMonth(); d++) r.revenueByDay.put(d, BigDecimal.ZERO);
            try (PreparedStatement ps = con.prepareStatement("SELECT DAY(paid_at), SUM(amount) FROM payments WHERE status = 'PAID' "
                    + "AND DATE(paid_at) BETWEEN ? AND ? GROUP BY DAY(paid_at)")) {
                range(ps, from, to);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) r.revenueByDay.put(rs.getInt(1), rs.getBigDecimal(2));
                }
            }
        }
        return r;
    }

    /** Revenue and expenses of a month only (for the trend chart). Returns {revenue, expenses}. */
    public BigDecimal[] totals(YearMonth month) throws SQLException {
        Date from = Date.valueOf(month.atDay(1));
        Date to = Date.valueOf(month.atEndOfMonth());
        try (Connection con = DBConnection.getConnection()) {
            return new BigDecimal[]{
                    sum(con, "SELECT COALESCE(SUM(amount), 0) FROM payments WHERE status = 'PAID' AND DATE(paid_at) BETWEEN ? AND ?", from, to),
                    sum(con, "SELECT COALESCE(SUM(amount), 0) FROM expenses WHERE status = 'ACTIVE' AND expense_date BETWEEN ? AND ?", from, to)};
        }
    }

    private static void range(PreparedStatement ps, Date from, Date to) throws SQLException {
        ps.setDate(1, from);
        ps.setDate(2, to);
    }

    private static BigDecimal sum(Connection con, String sql, Date from, Date to) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            range(ps, from, to);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBigDecimal(1);
            }
        }
    }

    /** The first month that has any payment or expense (for the year list); this month if none. */
    public LocalDate firstActivity() throws SQLException {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT LEAST(COALESCE((SELECT MIN(DATE(paid_at)) FROM payments), CURDATE()), "
                     + "COALESCE((SELECT MIN(expense_date) FROM expenses), CURDATE()))");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getDate(1).toLocalDate();
        }
    }
}
