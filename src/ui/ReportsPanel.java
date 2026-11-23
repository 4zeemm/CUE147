package ui;

import dao.BillDAO;
import dao.ReportDAO;
import model.MonthlyReport;
import service.ReportService;
import util.AppLogger;
import util.ReportExport;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Screen 8 (owner only) - the monthly report: choose a month and year, compare with the month before,
 * see the trend charts and export to CSV or PDF. Old months are never changed, so any month can be looked at.
 */
public class ReportsPanel extends ScreenPanel {
    private static final String[] MONTHS = {"January", "February", "March", "April", "May", "June", "July",
            "August", "September", "October", "November", "December"};
    private static final DateTimeFormatter SHORT = DateTimeFormatter.ofPattern("MMM yyyy");

    private final ReportService reportService = new ReportService();
    private final BillDAO billDAO = new BillDAO();

    private final JComboBox<String> monthBox = Theme.combo(MONTHS);
    private final JComboBox<Integer> yearBox = new JComboBox<>();

    private final StatCard revenueCard = new StatCard("Revenue", "-", Theme.GOLD);
    private final StatCard expensesCard = new StatCard("Expenses", "-", Theme.RED);
    private final StatCard profitCard = new StatCard("Profit", "-", Theme.GREEN);
    private final StatCard discountCard = new StatCard("Discounts given", "-", Theme.AMBER);

    private final DefaultTableModel compareModel = Theme.tableModel("Item", "This month", "Last month", "Change");
    private final DefaultTableModel staffModel = Theme.tableModel("Billed by", "Sales", "Revenue", "Discounts");
    private final DefaultTableModel expenseModel = Theme.tableModel("Expense category", "Amount");
    private final DefaultTableModel discountModel = Theme.tableModel("Occasion", "Discount");
    private final BarChart trendChart = new BarChart();
    private final BarChart dailyChart = new BarChart();

    // table usage (today / last 7 days)
    private final DefaultTableModel usageModel = Theme.tableModel("Table", "Type", "Games", "Hours", "Billed");
    private final JComboBox<String> usageBox = Theme.combo("Today", "Last 7 days", "Last 30 days");

    private MonthlyReport shownReport, previousReport;
    private List<Object[]> shownTrend = new ArrayList<>();
    private boolean weekly;

    public ReportsPanel(MainFrame frame) {
        super(frame);
        monthBox.setName("reportMonth");
        yearBox.setName("reportYear");
        yearBox.setFont(Theme.BODY);
        LocalDate today = LocalDate.now();
        monthBox.setSelectedIndex(today.getMonthValue() - 1);
        add(header("Reports", "Monthly history: money received, expenses, profit and more",
                Theme.label("Month:", Theme.BODY, Theme.MUTED), monthBox, yearBox,
                Theme.button("Show", Theme.BLUE, e -> refresh()),
                Theme.button("Export CSV", Theme.AMBER, e -> exportCsv()),
                Theme.button("Export PDF", Theme.AMBER, e -> exportPdf()),
                Theme.button("Daily Report", Theme.GREEN, e -> showDaily())), BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(Theme.BODY);
        tabs.addTab("Monthly summary", buildSummary());
        tabs.addTab("Charts", buildCharts());
        tabs.addTab("Table usage", buildUsage());
        add(tabs, BorderLayout.CENTER);
        monthBox.addActionListener(e -> { if (isShowing()) refresh(); });
        yearBox.addActionListener(e -> { if (isShowing()) refresh(); });
    }

    private JPanel buildSummary() {
        JPanel cards = Theme.panel(new GridLayout(1, 4, 14, 0));
        for (StatCard c : new StatCard[]{revenueCard, expensesCard, profitCard, discountCard}) cards.add(c);

        JPanel grid = Theme.panel(new GridLayout(2, 2, 14, 14));
        grid.add(tableCard("Compared with the month before", compareModel, 180, 95, 95, 70));
        grid.add(tableCard("Totals per Billed-by person", staffModel, 150, 55, 110, 100));
        grid.add(tableCard("Expenses by category", expenseModel, 220, 120));
        grid.add(tableCard("Discounts by occasion", discountModel, 220, 120));

        JPanel p = Theme.panel(new BorderLayout(0, 14));
        p.setBorder(new EmptyBorder(12, 0, 0, 0));
        p.add(cards, BorderLayout.NORTH);
        p.add(grid, BorderLayout.CENTER);
        return p;
    }

    private JPanel tableCard(String title, DefaultTableModel model, int... columnWidths) {
        JTable t = new JTable(model);
        Theme.styleTable(t);
        for (int i = 0; i < columnWidths.length && i < t.getColumnCount(); i++) t.getColumnModel().getColumn(i).setPreferredWidth(columnWidths[i]);
        RoundedPanel card = new RoundedPanel(new BorderLayout(0, 8));
        card.setBorder(new EmptyBorder(12, 12, 12, 12));
        card.add(Theme.label(title, Theme.BOLD, Theme.TEXT), BorderLayout.NORTH);
        card.add(Theme.scroll(t), BorderLayout.CENTER);
        return card;
    }

    private JPanel buildCharts() {
        JPanel p = Theme.panel(new GridLayout(2, 1, 0, 14));
        p.setBorder(new EmptyBorder(12, 0, 0, 0));
        p.add(trendChart);
        p.add(dailyChart);
        return p;
    }

    private JPanel buildUsage() {
        JTable table = new JTable(usageModel);
        Theme.styleTable(table);
        table.getColumnModel().getColumn(0).setPreferredWidth(170);
        JPanel top = Theme.panel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        top.add(Theme.label("Period:", Theme.BODY, Theme.MUTED));
        top.add(usageBox);
        top.add(Theme.button("Export CSV", Theme.AMBER, e -> exportTable("table_usage", usageModel)));
        usageBox.addActionListener(e -> loadUsage());
        RoundedPanel card = new RoundedPanel(new BorderLayout(0, 10));
        card.setBorder(new EmptyBorder(16, 16, 16, 16));
        card.add(top, BorderLayout.NORTH);
        card.add(Theme.scroll(table), BorderLayout.CENTER);
        JPanel p = Theme.panel(new BorderLayout());
        p.setBorder(new EmptyBorder(12, 0, 0, 0));
        p.add(card);
        return p;
    }

    /** The month chosen at the top. */
    public YearMonth selectedMonth() {
        Integer year = (Integer) yearBox.getSelectedItem();
        return YearMonth.of(year == null ? LocalDate.now().getYear() : year, monthBox.getSelectedIndex() + 1);
    }

    @Override
    public void refresh() {
        try {
            fillYears();
            YearMonth month = selectedMonth();
            shownReport = reportService.monthly(month);
            previousReport = reportService.monthly(month.minusMonths(1));
            shownTrend = reportService.lastSixMonths(month);
            showReport(shownReport, previousReport);
            loadUsage();
            frame.setStatus("Report for " + month.format(SHORT));
        } catch (SQLException e) {
            showDbError(e);
        }
    }

    /** Years from the first payment/expense up to this year. */
    private void fillYears() throws SQLException {
        int first = new ReportDAO().firstActivity().getYear();
        int now = LocalDate.now().getYear();
        if (yearBox.getItemCount() == now - Math.min(first, now) + 1) return;
        Integer chosen = (Integer) yearBox.getSelectedItem();
        yearBox.removeAllItems();
        for (int y = Math.min(first, now); y <= now; y++) yearBox.addItem(y);
        yearBox.setSelectedItem(chosen == null ? now : chosen);
    }

    private void showReport(MonthlyReport m, MonthlyReport p) {
        String vs = "vs " + p.month.format(SHORT) + ": ";
        revenueCard.setValue(Theme.money0(m.revenue));
        revenueCard.setNote(vs + MonthlyReport.change(m.revenue, p.revenue));
        expensesCard.setValue(Theme.money0(m.expenses));
        expensesCard.setNote(vs + MonthlyReport.change(m.expenses, p.expenses));
        profitCard.setValue(Theme.money0(m.getProfit()));
        profitCard.setNote(vs + MonthlyReport.change(m.getProfit(), p.getProfit()));
        discountCard.setValue(Theme.money0(m.billDiscounts));
        discountCard.setNote(vs + MonthlyReport.change(m.billDiscounts, p.billDiscounts));

        rightAlign(compareModel, 1, 2);
        compareModel.setRowCount(0);
        for (Object[] r : ReportExport.summaryRows(m, p)) {
            boolean count = ((String) r[0]).contains("(count)");
            BigDecimal now = (BigDecimal) r[1], before = (BigDecimal) r[2];
            compareModel.addRow(new Object[]{r[0], count ? now.toPlainString() : Theme.money(now), count ? before.toPlainString() : Theme.money(before),
                    MonthlyReport.change(now, before)});
        }
        staffModel.setRowCount(0);
        for (MonthlyReport.StaffTotal s : m.byStaff) staffModel.addRow(new Object[]{s.name, s.sales, Theme.money(s.revenue), Theme.money(s.discounts)});
        expenseModel.setRowCount(0);
        for (Map.Entry<String, BigDecimal> e : m.expensesByCategory.entrySet()) expenseModel.addRow(new Object[]{e.getKey(), Theme.money(e.getValue())});
        discountModel.setRowCount(0);
        for (Map.Entry<String, BigDecimal> e : m.discountsByOccasion.entrySet()) discountModel.addRow(new Object[]{e.getKey(), Theme.money(e.getValue())});

        List<String> labels = new ArrayList<>();
        List<BigDecimal[]> values = new ArrayList<>();
        for (Object[] r : shownTrend) {
            labels.add(((YearMonth) r[0]).format(DateTimeFormatter.ofPattern("MMM yy")));
            values.add(new BigDecimal[]{(BigDecimal) r[1], (BigDecimal) r[2]});
        }
        trendChart.setData("Last 6 months", new String[]{"Revenue", "Expenses"}, new Color[]{Theme.GREEN, Theme.RED}, labels, values);
        List<String> days = new ArrayList<>();
        List<BigDecimal[]> dayValues = new ArrayList<>();
        for (Map.Entry<Integer, BigDecimal> e : m.revenueByDay.entrySet()) {
            days.add(String.valueOf(e.getKey()));
            dayValues.add(new BigDecimal[]{e.getValue()});
        }
        dailyChart.setData("Revenue per day - " + m.month.format(SHORT), new String[]{"Revenue"}, new Color[]{Theme.GOLD}, days, dayValues);
    }

    /** Shows the given columns of the last built table on the right (money reads better that way). */
    private void rightAlign(DefaultTableModel model, int... columns) {
        for (JTable t : tablesOf(this)) {
            if (t.getModel() != model) continue;
            javax.swing.table.DefaultTableCellRenderer right = new javax.swing.table.DefaultTableCellRenderer();
            right.setHorizontalAlignment(SwingConstants.RIGHT);
            for (int c : columns) if (c < t.getColumnCount()) t.getColumnModel().getColumn(c).setCellRenderer(right);
        }
    }

    private List<JTable> tablesOf(Container root) {
        List<JTable> found = new ArrayList<>();
        for (Component c : root.getComponents()) {
            if (c instanceof JTable) found.add((JTable) c);
            else if (c instanceof Container) found.addAll(tablesOf((Container) c));
        }
        return found;
    }

    private void loadUsage() {
        try {
            int days = usageBox.getSelectedIndex() == 0 ? 1 : usageBox.getSelectedIndex() == 1 ? 7 : 30;
            usageModel.setRowCount(0);
            for (String[] r : reportService.getTableUsage(days)) {
                usageModel.addRow(new Object[]{r[0], r[1], Integer.parseInt(r[2]), r[3], Theme.money0(new BigDecimal(r[4]))});
            }
        } catch (SQLException e) {
            showDbError(e);
        }
    }

    // ------------------------------------------------------------------ export

    private void exportCsv() {
        if (shownReport == null) refresh();
        if (shownReport == null) return;
        exportTable("cue147_report_" + shownReport.month, ReportExport.csvModel(shownReport, previousReport));
    }

    private void exportPdf() {
        if (shownReport == null) refresh();
        if (shownReport == null) return;
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File(System.getProperty("user.home"), "cue147_report_" + shownReport.month + ".pdf"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File file = chooser.getSelectedFile();
        if (!file.getName().toLowerCase().endsWith(".pdf")) file = new File(file.getParentFile(), file.getName() + ".pdf");
        if (file.exists() && !Theme.confirm(this, file.getName() + " already exists. Replace it?", "Export PDF")) return;
        try {
            ReportExport.writePdf(file, shownReport, previousReport, shownTrend);
            frame.setStatus("Report saved to " + file.getName());
            Theme.info(this, "The report for " + shownReport.month.format(SHORT) + " was saved to:\n" + file.getAbsolutePath(), "Export Done");
        } catch (IOException e) {
            AppLogger.error("PDF export failed", e);
            Theme.error(this, "The PDF could not be saved:\n" + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ quick daily / weekly pop-ups (menu bar)

    public void showDaily() {
        weekly = false;
        summaryDialog("DAILY REPORT", LocalDate.now(), "Daily Report");
    }

    public void showWeekly() {
        weekly = true;
        summaryDialog("WEEKLY REPORT (last 7 days)", LocalDate.now().minusDays(6), "Weekly Report");
    }

    private void summaryDialog(String title, LocalDate from, String windowTitle) {
        try {
            BigDecimal revenue = billDAO.revenueBetween(from, LocalDate.now());
            int games = 0;
            String busiest = "-";
            List<String[]> rows = reportService.getTableUsage(weekly ? 7 : 1);
            for (String[] r : rows) games += Integer.parseInt(r[2]);
            if (!rows.isEmpty() && Integer.parseInt(rows.get(0)[2]) > 0) busiest = rows.get(0)[0];
            Theme.info(this, title + "\n\nRevenue: " + Theme.money(revenue) + "\nGames played: " + games
                    + "\nBusiest table: " + busiest, windowTitle);
        } catch (SQLException e) {
            showDbError(e);
        }
    }
}
