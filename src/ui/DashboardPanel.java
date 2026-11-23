package ui;

import dao.BookingDAO;
import dao.MembershipDAO;
import dao.SessionDAO;
import model.Booking;
import model.GameSession;
import model.Membership;
import service.DashboardStats;
import service.ReportService;
import service.SessionService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Screen 2 - dashboard. Every number comes from MySQL queries. */
public class DashboardPanel extends ScreenPanel {
    private final StatCard todayRevenueCard = new StatCard("Today's Revenue", "-", Theme.GOLD);
    private final StatCard monthRevenueCard = new StatCard("Monthly Revenue", "-", Theme.GOLD);
    private final StatCard todayExpenseCard = new StatCard("Today's Expenses", "-", Theme.RED);
    private final StatCard monthExpenseCard = new StatCard("Monthly Expenses", "-", Theme.RED);
    private final StatCard profitCard = new StatCard("Estimated Profit", "-", Theme.GREEN);
    private final StatCard customersCard = new StatCard("Total Customers", "-", Theme.AMBER);
    private final StatCard activeCard = new StatCard("Active Sessions", "-", Theme.BLUE);
    private final StatCard availableCard = new StatCard("Available Tables", "-", Theme.GREEN);
    private final StatCard occupiedCard = new StatCard("Occupied Tables", "-", Theme.RED);
    private final StatCard maintenanceCard = new StatCard("Under Maintenance", "-", Theme.AMBER);
    private final StatCard endingCard = new StatCard("Ending Soon", "-", Theme.BLUE);
    private final StatCard unpaidCard = new StatCard("Unpaid Bills", "-", Theme.AMBER);

    private final DefaultListModel<String> sessionModel = new DefaultListModel<>();
    private final JList<String> sessionList = new JList<>(sessionModel);
    private final DefaultTableModel bookingModel = Theme.tableModel("Customer", "Table", "Date", "Time");
    private final DefaultTableModel endingModel = Theme.tableModel("Member", "Ends", "Days left");

    private final ReportService reportService = new ReportService();
    private final SessionDAO sessionDAO = new SessionDAO();
    private final BookingDAO bookingDAO = new BookingDAO();
    private final MembershipDAO membershipDAO = new MembershipDAO();
    private final SessionService sessionService = new SessionService();
    /** A membership is "ending soon" when it has this many days left or fewer. */
    private static final int ENDING_SOON_DAYS = 14;
    private List<GameSession> activeSessions = new ArrayList<>();

    /** Everything the dashboard needs, loaded in one go in the background. */
    private static class DashboardData {
        DashboardStats stats;
        List<GameSession> sessions;
        List<Booking> bookings;
        List<Membership> ending;
    }

    public DashboardPanel(MainFrame frame) {
        super(frame);
        add(header("Dashboard", "Live overview of the club",
                Theme.button("Start Game", Theme.GREEN, e -> frame.newSession()),
                Theme.button("New Customer", Theme.BLUE, e -> frame.newCustomer()),
                Theme.button("New Booking", Theme.AMBER, e -> frame.showScreen(MainFrame.BOOKINGS)),
                Theme.button("Generate Bill", new Color(0x8E5CC9), e -> frame.showScreen(MainFrame.BILLING))), BorderLayout.NORTH);

        JPanel body = Theme.panel(new BorderLayout(0, 14));
        JPanel statRows = Theme.panel(new GridLayout(2, 1, 0, 14));
        JPanel row1 = Theme.panel(new GridLayout(1, 6, 14, 0));
        for (StatCard c : new StatCard[]{todayRevenueCard, monthRevenueCard, todayExpenseCard, monthExpenseCard, profitCard, unpaidCard}) row1.add(c);
        JPanel row2 = Theme.panel(new GridLayout(1, 6, 14, 0));
        for (StatCard c : new StatCard[]{customersCard, activeCard, availableCard, occupiedCard, maintenanceCard, endingCard}) row2.add(c);
        statRows.add(row1);
        statRows.add(row2);
        body.add(statRows, BorderLayout.NORTH);

        JPanel middle = Theme.panel(new GridLayout(1, 3, 16, 0));
        middle.add(buildSessionsCard());
        middle.add(buildBookingsCard());
        middle.add(buildEndingCard());
        body.add(middle, BorderLayout.CENTER);
        add(body, BorderLayout.CENTER);
    }

    private JPanel buildSessionsCard() {
        RoundedPanel card = new RoundedPanel(new BorderLayout(0, 10));
        card.setBorder(new EmptyBorder(16, 16, 16, 16));
        card.add(Theme.label("Active Sessions (live)", Theme.HEADING, Theme.TEXT), BorderLayout.NORTH);
        sessionList.setFont(Theme.BODY);
        sessionList.setFixedCellHeight(34);
        sessionList.setBackground(Theme.CARD);
        sessionList.setForeground(Theme.TEXT);
        sessionList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean sel, boolean f) {
                super.getListCellRendererComponent(l, v, i, sel, false);
                setBorder(new EmptyBorder(0, 10, 0, 10));
                setBackground(sel ? Theme.GREEN : (i % 2 == 0 ? Theme.CARD : Theme.PANEL));
                setForeground(Theme.TEXT);
                return this;
            }
        });
        sessionList.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) frame.showScreen(MainFrame.TABLES);
            }
        });
        card.add(Theme.scroll(sessionList), BorderLayout.CENTER);
        card.add(Theme.label("Double-click a session to open Tables", Theme.SMALL, Theme.MUTED), BorderLayout.SOUTH);
        return card;
    }

    private JPanel buildBookingsCard() {
        RoundedPanel card = new RoundedPanel(new BorderLayout(0, 10));
        card.setBorder(new EmptyBorder(16, 16, 16, 16));
        card.add(Theme.label("Upcoming Bookings", Theme.HEADING, Theme.TEXT), BorderLayout.NORTH);
        JTable t = new JTable(bookingModel);
        Theme.styleTable(t);
        card.add(Theme.scroll(t), BorderLayout.CENTER);
        return card;
    }

    /** Members whose membership ends within the next two weeks, so the staff can ask them to renew. */
    private JPanel buildEndingCard() {
        RoundedPanel card = new RoundedPanel(new BorderLayout(0, 10));
        card.setBorder(new EmptyBorder(16, 16, 16, 16));
        card.add(Theme.label("Memberships Ending Soon", Theme.HEADING, Theme.TEXT), BorderLayout.NORTH);
        JTable t = new JTable(endingModel);
        Theme.styleTable(t);
        card.add(Theme.scroll(t), BorderLayout.CENTER);
        card.add(Theme.label("Next " + ENDING_SOON_DAYS + " days - renew on Membership", Theme.SMALL, Theme.MUTED), BorderLayout.SOUTH);
        return card;
    }

    /**
     * Loads the numbers with a SwingWorker.
     * Why? Database queries can be slow, and the screen must never freeze.
     *   doInBackground() runs on another thread  -> the SQL queries
     *   done() runs on the Swing thread           -> updating labels (Swing is not thread safe)
     */
    @Override
    public void refresh() {
        new SwingWorker<DashboardData, Void>() {
            @Override
            protected DashboardData doInBackground() throws Exception {
                DashboardData d = new DashboardData();
                d.stats = reportService.loadDashboardStats();
                d.sessions = sessionDAO.findActive();
                d.bookings = bookingDAO.findUpcoming(8);
                d.ending = membershipDAO.findExpiringSoon(ENDING_SOON_DAYS);
                return d;
            }

            @Override
            protected void done() {
                try {
                    showData(get());
                } catch (Exception e) {
                    Theme.error(DashboardPanel.this, "Could not load the dashboard.\nIs the database server switched on?\n\n"
                            + (e.getCause() != null ? e.getCause().getMessage() : e.getMessage()));
                }
            }
        }.execute();
    }

    @Override
    public void autoRefresh() { refresh(); }

    private void showData(DashboardData d) {
        DashboardStats s = d.stats;
        todayRevenueCard.setValue(Theme.money0(s.todayRevenue));
        monthRevenueCard.setValue(Theme.money0(s.monthRevenue));
        todayExpenseCard.setValue(Theme.money0(s.todayExpenses));
        monthExpenseCard.setValue(Theme.money0(s.monthExpenses));
        profitCard.setValue(Theme.money0(s.getEstimatedProfit()));
        customersCard.setValue(String.valueOf(s.totalCustomers));
        activeCard.setValue(String.valueOf(s.activeSessions));
        availableCard.setValue(String.valueOf(s.availableTables));
        occupiedCard.setValue(String.valueOf(s.occupiedTables));
        maintenanceCard.setValue(String.valueOf(s.maintenanceTables));
        endingCard.setValue(String.valueOf(d.ending.size()));
        endingCard.setNote("next " + ENDING_SOON_DAYS + " days");
        unpaidCard.setValue(String.valueOf(s.unpaidBills));
        unpaidCard.setNote(s.unpaidBills == 0 ? "all bills paid" : "waiting for payment");

        endingModel.setRowCount(0);
        for (Membership m : d.ending) {
            endingModel.addRow(new Object[]{m.getCustomerName(), m.getExpiryDate(), m.getDaysLeft()});
        }

        bookingModel.setRowCount(0);
        for (Booking b : d.bookings) {
            bookingModel.addRow(new Object[]{b.getCustomerName(), b.getTableName(), b.getDate(), b.getTime()});
        }
        activeSessions = d.sessions;
        tick();
    }

    /** Called every second by the Swing Timer: updates the running time of each session (no database call). */
    @Override
    public void tick() {
        sessionModel.clear();
        LocalDateTime now = LocalDateTime.now();
        for (GameSession s : activeSessions) {
            Duration d = Duration.between(s.getStartTime(), now);
            String time = String.format("%02d:%02d:%02d", d.toHours(), d.toMinutesPart(), d.toSecondsPart());
            sessionModel.addElement(s.getTableName() + "  |  " + s.getCustomerName() + "  |  " + time
                    + "  |  " + Theme.money0(sessionService.estimateAmount(s, now)));
        }
        if (sessionModel.isEmpty()) sessionModel.addElement("No active sessions");
    }
}
