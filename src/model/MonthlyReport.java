package model;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything the owner sees for one month. All money is counted on the day it was RECEIVED (paid).
 *
 *   Revenue = table time + food & drinks - bill discounts + membership sales
 *   Profit  = Revenue - expenses
 */
public class MonthlyReport {
    public YearMonth month;
    public BigDecimal tableTime = BigDecimal.ZERO;          // table time of the paid bills (member discount already taken off)
    public BigDecimal foodAndDrinks = BigDecimal.ZERO;
    public BigDecimal billDiscounts = BigDecimal.ZERO;      // discounts given at the checkout
    public BigDecimal memberships = BigDecimal.ZERO;        // membership sales
    public BigDecimal revenue = BigDecimal.ZERO;            // all PAID payments of the month
    public int paidBills;
    public int membershipsSold;
    public BigDecimal cash = BigDecimal.ZERO;
    public BigDecimal upi = BigDecimal.ZERO;
    public BigDecimal expenses = BigDecimal.ZERO;
    public final Map<String, BigDecimal> expensesByCategory = new LinkedHashMap<>();
    public final Map<String, BigDecimal> discountsByOccasion = new LinkedHashMap<>();
    public final List<StaffTotal> byStaff = new ArrayList<>();
    public final Map<Integer, BigDecimal> revenueByDay = new LinkedHashMap<>();   // day of month -> revenue

    /** Sales of one "Billed by" person in the month. */
    public static class StaffTotal {
        public final String name;
        public final int sales;              // bills + memberships
        public final BigDecimal revenue;
        public final BigDecimal discounts;

        public StaffTotal(String name, int sales, BigDecimal revenue, BigDecimal discounts) {
            this.name = name;
            this.sales = sales;
            this.revenue = revenue;
            this.discounts = discounts;
        }
    }

    public BigDecimal getProfit() { return revenue.subtract(expenses); }

    /** "+12.5%" / "-3.0%" compared with an older value, or "new" when the older value was 0. */
    public static String change(BigDecimal now, BigDecimal before) {
        if (now.compareTo(before) == 0) return "no change";
        if (before.signum() == 0) return "new";
        BigDecimal pct = now.subtract(before).multiply(BigDecimal.valueOf(100)).divide(before.abs(), 1, java.math.RoundingMode.HALF_UP);
        return (pct.signum() > 0 ? "+" : "") + pct.toPlainString() + "%";
    }
}
