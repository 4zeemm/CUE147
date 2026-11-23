package model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The "compared with last month" text of the report. */
class MonthlyReportTest {
    private static BigDecimal rs(String v) { return new BigDecimal(v); }

    @Test
    void changeInPercent() {
        assertEquals("+50.0%", MonthlyReport.change(rs("150"), rs("100")));
        assertEquals("-25.0%", MonthlyReport.change(rs("75"), rs("100")));
        assertEquals("no change", MonthlyReport.change(rs("100"), rs("100")));
    }

    @Test
    void nothingBeforeMeansNew() {
        assertEquals("new", MonthlyReport.change(rs("500"), rs("0")));
        assertEquals("no change", MonthlyReport.change(rs("0"), rs("0")));
    }

    @Test
    void profitIsRevenueMinusExpenses() {
        MonthlyReport r = new MonthlyReport();
        r.revenue = rs("1000");
        r.expenses = rs("1250");
        assertEquals(0, r.getProfit().compareTo(rs("-250")));
    }
}
