package ui;

import dao.BillDAO;
import service.DashboardStats;
import service.ReportService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

/** Screen 8 (owner only) - revenue cards, per-table usage and progress bars, all from MySQL. */
public class ReportsPanel extends ScreenPanel {
    private final ReportService reportService = new ReportService();
    private final BillDAO billDAO = new BillDAO();

    private final StatCard todayCard = new StatCard("Today's Revenue", "-", Theme.GOLD);
    private final StatCard weekCard = new StatCard("Weekly Revenue", "-", Theme.GREEN);
    private final StatCard gamesCard = new StatCard("Total Games", "-", Theme.BLUE);
    private final StatCard customersCard = new StatCard("Customers", "-", Theme.AMBER);
    private final StatCard mostUsedCard = new StatCard("Most Used Table", "-", Theme.RED);

    private final DefaultTableModel model = Theme.tableModel("Table", "Type", "Games", "Hours", "Billed");
    private final JLabel modeLabel = Theme.label("", Theme.HEADING, Theme.TEXT);
    private final JProgressBar utilBar = new JProgressBar(0, 100);
    private final JProgressBar paidBar = new JProgressBar(0, 100);
    private final JProgressBar expenseBar = new JProgressBar(0, 100);

    private boolean weekly;

    public ReportsPanel(MainFrame frame) {
        super(frame);
        add(header("Reports", "Revenue and table usage",
                Theme.button("Daily Report", Theme.GREEN, e -> showDaily()),
                Theme.button("Weekly Report", Theme.BLUE, e -> showWeekly()),
                Theme.button("Export CSV", Theme.AMBER, e -> exportTable(weekly ? "weekly_report" : "daily_report", model)),
                Theme.button("Refresh", Theme.NEUTRAL, e -> { refresh(); frame.setStatus("Report refreshed"); })), BorderLayout.NORTH);

        JPanel cards = Theme.panel(new GridLayout(1, 5, 14, 0));
        for (StatCard c : new StatCard[]{todayCard, weekCard, gamesCard, customersCard, mostUsedCard}) cards.add(c);

        JTable table = new JTable(model);
        Theme.styleTable(table);
        table.getColumnModel().getColumn(0).setPreferredWidth(170);   // table names are long
        RoundedPanel usage = new RoundedPanel(new BorderLayout(0, 10));
        usage.setBorder(new EmptyBorder(16, 16, 16, 16));
        usage.add(modeLabel, BorderLayout.NORTH);
        usage.add(Theme.scroll(table), BorderLayout.CENTER);

        JPanel centre = Theme.panel(new BorderLayout(16, 0));
        centre.add(usage, BorderLayout.CENTER);
        centre.add(buildBars(), BorderLayout.EAST);

        JPanel body = Theme.panel(new BorderLayout(0, 16));
        body.add(cards, BorderLayout.NORTH);
        body.add(centre, BorderLayout.CENTER);
        add(body, BorderLayout.CENTER);
    }

    private JPanel buildBars() {
        RoundedPanel card = new RoundedPanel(new BorderLayout(0, 10));
        card.setBorder(new EmptyBorder(16, 16, 16, 16));
        card.setPreferredSize(new Dimension(320, 0));
        card.add(Theme.label("Health of the club", Theme.HEADING, Theme.TEXT), BorderLayout.NORTH);
        JPanel bars = Theme.panel(new GridLayout(6, 1, 0, 4));
        bars.add(Theme.label("Tables in use right now", Theme.SMALL, Theme.MUTED));
        bars.add(utilBar);
        bars.add(Theme.label("Bills paid this month (by value)", Theme.SMALL, Theme.MUTED));
        bars.add(paidBar);
        bars.add(Theme.label("Expenses as % of revenue (this month)", Theme.SMALL, Theme.MUTED));
        bars.add(expenseBar);
        for (JProgressBar b : new JProgressBar[]{utilBar, paidBar, expenseBar}) b.setStringPainted(true);
        JPanel wrap = Theme.panel(new BorderLayout());
        wrap.add(bars, BorderLayout.NORTH);
        card.add(wrap, BorderLayout.CENTER);
        return card;
    }

    public void showDaily() {
        weekly = false;
        refresh();
        summaryDialog("DAILY REPORT", LocalDate.now(), "Daily Report");
    }

    public void showWeekly() {
        weekly = true;
        refresh();
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

    @Override
    public void refresh() {
        try {
            LocalDate today = LocalDate.now();
            LocalDate monthStart = today.withDayOfMonth(1);
            DashboardStats stats = reportService.loadDashboardStats();
            List<String[]> usage = reportService.getTableUsage(weekly ? 7 : 1);

            int games = 0;
            for (String[] r : usage) games += Integer.parseInt(r[2]);
            todayCard.setValue(Theme.money0(stats.todayRevenue));
            weekCard.setValue(Theme.money0(billDAO.revenueBetween(today.minusDays(6), today)));
            gamesCard.setValue(String.valueOf(games));
            customersCard.setValue(String.valueOf(stats.totalCustomers));
            boolean anyGames = !usage.isEmpty() && Integer.parseInt(usage.get(0)[2]) > 0;
            mostUsedCard.setValue(anyGames ? usage.get(0)[0].replace(" Table", "").replace(" Station", "") : "-");

            modeLabel.setText(weekly ? "Weekly report - last 7 days" : "Daily report - today");
            model.setRowCount(0);
            for (String[] r : usage) {
                model.addRow(new Object[]{r[0], r[1], r[2], r[3], Theme.money0(new BigDecimal(r[4]))});
            }

            utilBar.setValue(stats.totalTables == 0 ? 0 : stats.occupiedTables * 100 / stats.totalTables);
            BigDecimal billed = billDAO.billedBetween(monthStart, today);
            paidBar.setValue(percent(stats.monthRevenue, billed));
            expenseBar.setValue(Math.min(100, percent(stats.monthExpenses, stats.monthRevenue)));
            utilBar.setString(utilBar.getValue() + "%");
            paidBar.setString(paidBar.getValue() + "%");
            expenseBar.setString(stats.monthRevenue.signum() == 0 ? "no revenue yet" : percent(stats.monthExpenses, stats.monthRevenue) + "%");
        } catch (SQLException e) {
            showDbError(e);
        }
    }

    private int percent(BigDecimal part, BigDecimal whole) {
        if (whole.signum() == 0) return 0;
        return part.multiply(BigDecimal.valueOf(100)).divide(whole, 0, RoundingMode.DOWN).intValue();
    }
}
