package service;

import dao.BillDAO;
import dao.CustomerDAO;
import dao.ExpenseDAO;
import dao.GameTableDAO;
import dao.MembershipDAO;
import dao.SessionDAO;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Collects numbers from several DAOs for the dashboard, the reports and the notifications. */
public class ReportService {
    private final BillDAO billDAO = new BillDAO();
    private final ExpenseDAO expenseDAO = new ExpenseDAO();
    private final CustomerDAO customerDAO = new CustomerDAO();
    private final SessionDAO sessionDAO = new SessionDAO();
    private final GameTableDAO tableDAO = new GameTableDAO();
    private final MembershipDAO membershipDAO = new MembershipDAO();

    public DashboardStats loadDashboardStats() throws SQLException {
        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);
        DashboardStats s = new DashboardStats();
        s.todayRevenue = billDAO.revenueBetween(today, today);
        s.monthRevenue = billDAO.revenueBetween(monthStart, today);
        s.todayExpenses = expenseDAO.totalBetween(today, today);
        s.monthExpenses = expenseDAO.totalBetween(monthStart, today);
        s.totalCustomers = customerDAO.countRegistered();
        s.activeSessions = sessionDAO.countActive();
        s.totalTables = tableDAO.countActiveTables();
        s.occupiedTables = s.activeSessions;
        s.maintenanceTables = tableDAO.countUnderMaintenance();
        // tables under maintenance can not be used, so they are not "available"
        s.availableTables = Math.max(0, s.totalTables - s.occupiedTables - s.maintenanceTables);
        return s;
    }

    /** The full report of one month (see MonthlyReport). */
    public model.MonthlyReport monthly(java.time.YearMonth month) throws SQLException {
        return new dao.ReportDAO().monthly(month);
    }

    /** Revenue and expenses of the given month and the 5 months before it, oldest first: {month, revenue, expenses}. */
    public List<Object[]> lastSixMonths(java.time.YearMonth month) throws SQLException {
        dao.ReportDAO reportDAO = new dao.ReportDAO();
        List<Object[]> list = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            java.time.YearMonth m = month.minusMonths(i);
            java.math.BigDecimal[] t = reportDAO.totals(m);
            list.add(new Object[]{m, t[0], t[1]});
        }
        return list;
    }

    /** Table usage for the last "days" days (1 = today only, 7 = this week). */
    public List<String[]> getTableUsage(int days) throws SQLException {
        LocalDate today = LocalDate.now();
        return sessionDAO.usageByTable(today.minusDays(days - 1), today);
    }

    public List<String> getNotifications() throws SQLException {
        List<String> list = new ArrayList<>();
        int unpaid = billDAO.countUnpaid();
        if (unpaid > 0) list.add(unpaid + " bill(s) are waiting for payment");
        int active = sessionDAO.countActive();
        if (active > 0) list.add(active + " game(s) are running right now");
        int maintenance = tableDAO.countUnderMaintenance();
        if (maintenance > 0) list.add(maintenance + " table(s) under maintenance");
        int expiring = membershipDAO.countExpiringSoon(7);
        if (expiring > 0) list.add(expiring + " membership(s) expire within 7 days");
        if (list.isEmpty()) list.add("No new notifications");
        return list;
    }
}
