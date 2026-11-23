package service;

import java.math.BigDecimal;

/** A bundle of the numbers shown on the dashboard. All values come from SQL queries. */
public class DashboardStats {
    public BigDecimal todayRevenue = BigDecimal.ZERO;
    public BigDecimal monthRevenue = BigDecimal.ZERO;
    public BigDecimal todayExpenses = BigDecimal.ZERO;
    public BigDecimal monthExpenses = BigDecimal.ZERO;
    public int totalCustomers;
    public int activeSessions;
    public int totalTables;
    public int availableTables;
    public int occupiedTables;
    public int maintenanceTables;

    public BigDecimal getEstimatedProfit() { return monthRevenue.subtract(monthExpenses); }
}
