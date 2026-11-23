package ui;

import model.User;
import service.ReportService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The main window after login. It owns the menu bar, toolbar, sidebar,
 * header and a CardLayout that switches between the 8 screens.
 */
public class MainFrame extends JFrame {
    public static final String DASHBOARD = "Dashboard";
    public static final String TABLES = "Tables";
    public static final String CUSTOMERS = "Customers";
    public static final String BOOKINGS = "Bookings";
    public static final String BILLING = "Billing";
    public static final String MEMBERSHIP = "Membership";
    public static final String EXPENSES = "Expenses";
    public static final String REPORTS = "Reports";
    public static final String STAFF = "Staff";
    public static final String SETTINGS = "Settings";

    private final User user;
    private final ReportService reportService = new ReportService();

    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);
    private final Map<String, ScreenPanel> screens = new LinkedHashMap<>();
    private final Map<String, RoundButton> navButtons = new LinkedHashMap<>();
    /** Screens that only the owner uses for setting things up; they sit under "ADMIN" in the sidebar. */
    private final java.util.Set<String> adminScreens = new java.util.LinkedHashSet<>();
    private final JLabel clockLabel = Theme.label("", Theme.HEADING, Theme.GOLD);
    private final JLabel statusLabel = Theme.label("Ready", Theme.SMALL, Theme.MUTED);
    private final RoundButton notifyButton;
    private String current = DASHBOARD;

    private DashboardPanel dashboard;
    private TablesPanel tables;
    private CustomersPanel customers;
    private ReportsPanel reports;
    private int secondsCounter;
    private Timer clock;

    public MainFrame(User user) {
        super("CUE 147 - Snooker & Pool Club");
        setIconImages(util.AppInfo.icons());
        this.user = user;
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1280, 800);
        setMinimumSize(new Dimension(1100, 700));
        setLocationRelativeTo(null);

        notifyButton = Theme.button("Notifications", Theme.NEUTRAL, e -> showNotifications());

        createScreens();
        setJMenuBar(buildMenuBar());

        JPanel north = new JPanel(new BorderLayout());
        north.add(buildToolBar(), BorderLayout.NORTH);
        north.add(buildHeader(), BorderLayout.CENTER);

        JPanel centre = new JPanel(new BorderLayout());
        centre.add(north, BorderLayout.NORTH);
        centre.add(content, BorderLayout.CENTER);
        JPanel status = new JPanel(new BorderLayout());
        status.setBackground(Theme.SIDEBAR);
        status.setBorder(new EmptyBorder(4, 14, 4, 14));
        status.add(statusLabel, BorderLayout.WEST);
        centre.add(status, BorderLayout.SOUTH);

        add(buildSidebar(), BorderLayout.WEST);
        add(centre, BorderLayout.CENTER);

        showScreen(DASHBOARD);
        startClock();
    }

    // ---------------- screens ----------------

    private void createScreens() {
        dashboard = new DashboardPanel(this);
        tables = new TablesPanel(this);
        customers = new CustomersPanel(this);
        reports = new ReportsPanel(this);
        screens.put(DASHBOARD, dashboard);
        screens.put(TABLES, tables);
        screens.put(CUSTOMERS, customers);
        screens.put(BOOKINGS, new BookingsPanel(this));
        screens.put(BILLING, new BillingPanel(this));
        screens.put(MEMBERSHIP, new MembershipPanel(this));
        // screens that only the owner may open
        if (user.canManageExpenses()) admin(EXPENSES, new ExpensesPanel(this));
        if (user.canViewReports()) admin(REPORTS, reports);
        admin(STAFF, new StaffPanel(this));
        if (user.canManagePricing()) admin(SETTINGS, new SettingsPanel(this));
        for (Map.Entry<String, ScreenPanel> e : screens.entrySet()) content.add(e.getValue(), e.getKey());
    }

    /** Adds a screen that belongs under the "ADMIN" heading of the sidebar. */
    private void admin(String name, ScreenPanel panel) {
        screens.put(name, panel);
        adminScreens.add(name);
    }

    public User getUser() { return user; }

    /** Switch to a screen. Every navigation button/menu/toolbar item ends up here. */
    public void showScreen(String name) {
        ScreenPanel p = screens.get(name);
        p.refresh();
        cards.show(content, name);
        current = name;
        for (Map.Entry<String, RoundButton> e : navButtons.entrySet()) {
            e.getValue().setBase(e.getKey().equals(name) ? Theme.ACCENT : Theme.SIDEBAR);
        }
        setStatus("Viewing: " + name);
        refreshNotifications();
    }

    public void setStatus(String text) { statusLabel.setText(text); }

    public void refreshCurrent() {
        screens.get(current).refresh();
        setStatus("Refreshed " + current + " at " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
    }

    // ---------------- actions shared by menu / toolbar / dashboard ----------------

    public void newSession() {
        showScreen(TABLES);
        tables.startOnFirstFreeTable();
    }

    public void newCustomer() {
        showScreen(CUSTOMERS);
        customers.focusForm();
    }

    public void showReport(boolean weekly) {
        if (!user.canViewReports()) {
            Theme.error(this, "Only the owner can view reports.");
            return;
        }
        showScreen(REPORTS);
        if (weekly) reports.showWeekly(); else reports.showDaily();
    }

    /** The daily automatic backup runs on a background thread, so the counter never waits for it. */
    private void startAutomaticBackup() {
        Thread t = new Thread(() -> {
            String problem = new service.BackupService().automaticBackupIfDue();
            if (problem != null) SwingUtilities.invokeLater(() -> setStatus(problem + " (see Settings > Backups)"));
        }, "cue147-automatic-backup");
        t.setDaemon(true);
        t.start();
    }

    /** Closing this window also stops its timer (clock, auto refresh, automatic backup). */
    @Override
    public void dispose() {
        if (clock != null) clock.stop();
        super.dispose();
    }

    public void logout() {
        if (Theme.confirm(this, "Do you want to log out, " + user.getFullName() + "?", "Logout")) {
            dispose();
            new LoginFrame().setVisible(true);
        }
    }

    /** Updates the number on the notification button from the database. */
    public void refreshNotifications() {
        if (!util.AppSettings.isNotificationsOn()) {   // switched off in Settings > Appearance
            notifyButton.setText("Notifications (off)");
            return;
        }
        try {
            java.util.List<String> list = reportService.getNotifications();
            int count = list.get(0).startsWith("No new") ? 0 : list.size();
            notifyButton.setText("Notifications (" + count + ")");
        } catch (SQLException e) {
            notifyButton.setText("Notifications (?)");
        }
    }

    private void showNotifications() {
        if (!util.AppSettings.isNotificationsOn()) {
            Theme.info(this, "Notifications are switched off.\nTurn them on in Settings > Appearance.", "Notifications");
            return;
        }
        try {
            StringBuilder sb = new StringBuilder();
            for (String n : reportService.getNotifications()) sb.append("\u2022 ").append(n).append("\n");
            Theme.info(this, sb.toString(), "Notifications");
        } catch (SQLException e) {
            Theme.error(this, "Database problem:\n" + e.getMessage());
        }
    }

    private void exitApp() {
        if (Theme.confirm(this, "Exit CUE 147?", "Exit")) System.exit(0);
    }

    private void about() {
        Theme.info(this, "CUE 147 - Snooker & Pool Club Management System\nBreak. Pot. Repeat.\n\nVersion " + util.AppInfo.getVersion()
                + "\nDatabase: " + database.DBConnection.getConfig().describe()
                + "\n\nJava Swing + JDBC + MySQL.", "About Cue 147");
    }

    // ---------------- Swing Timer ----------------

    private void startClock() {
        DateTimeFormatter f = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy  |  HH:mm:ss");
        clockLabel.setText(LocalDateTime.now().format(f));
        // A Swing Timer fires an ActionEvent every 1000 ms on the Event Dispatch Thread.
        clock = new Timer(1000, e -> {
            clockLabel.setText(LocalDateTime.now().format(f));
            screens.get(current).tick();          // the visible screen updates its running timers
            secondsCounter++;
            if (secondsCounter % 15 == 0) screens.get(current).autoRefresh();   // keeps two counters in step
            if (secondsCounter % 30 == 0) refreshNotifications();
            if (secondsCounter % 1800 == 10) startAutomaticBackup();   // 10 s after login, then every 30 minutes
        });
        clock.start();
    }

    // ---------------- building the pieces ----------------

    private JPanel buildSidebar() {
        JPanel side = new JPanel();
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setBackground(Theme.SIDEBAR);
        side.setBorder(new EmptyBorder(18, 14, 18, 14));
        side.setPreferredSize(new Dimension(224, 0));

        JLabel logo = Theme.label("CUE 147", new Font("Serif", Font.BOLD, 30), Theme.GOLD);
        JLabel tag = Theme.label("Break. Pot. Repeat.", Theme.SMALL, Theme.MUTED);
        logo.setAlignmentX(Component.LEFT_ALIGNMENT);
        tag.setAlignmentX(Component.LEFT_ALIGNMENT);
        side.add(logo);
        side.add(tag);
        side.add(Box.createVerticalStrut(24));

        // the screens used every day first, then one "ADMIN" group with the setting-up screens
        boolean adminHeadingAdded = false;
        for (String name : screens.keySet()) {
            if (adminScreens.contains(name) && !adminHeadingAdded) {
                adminHeadingAdded = true;
                side.add(Box.createVerticalStrut(14));
                JLabel heading = Theme.label("ADMIN", Theme.SMALL, Theme.MUTED);
                heading.setAlignmentX(Component.LEFT_ALIGNMENT);
                heading.setBorder(new EmptyBorder(0, 10, 4, 0));
                side.add(heading);
            }
            RoundButton b = Theme.button("   " + name, Theme.SIDEBAR, e -> showScreen(name));
            style(b);
            navButtons.put(name, b);
            side.add(b);
            side.add(Box.createVerticalStrut(4));
        }
        side.add(Box.createVerticalGlue());
        JSeparator sep = new JSeparator();
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 2));
        side.add(sep);
        side.add(Box.createVerticalStrut(10));
        RoundButton logout = Theme.button("   Logout", new Color(0x5A2A2A), e -> logout());
        style(logout);
        side.add(logout);
        return side;
    }

    private void style(RoundButton b) {
        b.setHorizontalAlignment(SwingConstants.LEFT);
        b.setAlignmentX(Component.LEFT_ALIGNMENT);
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        b.setPreferredSize(new Dimension(190, 42));
        b.setFont(Theme.BODY);
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(Theme.PANEL);
        header.setBorder(new EmptyBorder(12, 24, 12, 24));

        JPanel left = Theme.panel(new GridLayout(2, 1));
        left.add(Theme.label("Welcome back, " + user.getFullName() + "!", Theme.HEADING, Theme.TEXT));
        left.add(Theme.label("Logged in as: " + user, Theme.SMALL, Theme.MUTED));
        header.add(left, BorderLayout.WEST);

        JPanel right = Theme.panel(new FlowLayout(FlowLayout.RIGHT, 16, 0));
        right.add(clockLabel);
        right.add(notifyButton);
        header.add(right, BorderLayout.EAST);
        return header;
    }

    private JToolBar buildToolBar() {
        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        bar.setBackground(Theme.SIDEBAR);
        bar.setBorder(new EmptyBorder(4, 10, 4, 10));
        bar.add(toolButton("Dashboard", e -> showScreen(DASHBOARD)));
        bar.add(toolButton("New Customer", e -> newCustomer()));
        bar.add(toolButton("New Booking", e -> showScreen(BOOKINGS)));
        bar.add(toolButton("Billing", e -> showScreen(BILLING)));
        bar.addSeparator();
        bar.add(toolButton("Refresh", e -> refreshCurrent()));
        return bar;
    }

    private JButton toolButton(String text, java.awt.event.ActionListener a) {
        JButton b = new JButton(text);
        b.setFocusPainted(false);
        b.setContentAreaFilled(false);
        b.setBorder(new EmptyBorder(6, 12, 6, 12));
        b.setForeground(Theme.TEXT);
        b.setFont(Theme.BODY);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setToolTipText(text);
        b.addActionListener(a);
        return b;
    }

    private JMenuBar buildMenuBar() {
        JMenuBar bar = new JMenuBar();
        bar.setBackground(Theme.SIDEBAR);

        JMenu file = new JMenu("File");
        file.add(item("New Session", e -> newSession()));
        file.add(item("Logout", e -> logout()));
        file.addSeparator();
        file.add(item("Exit", e -> exitApp()));

        JMenu manage = new JMenu("Manage");
        manage.add(item("Tables", e -> showScreen(TABLES)));
        manage.add(item("Customers", e -> showScreen(CUSTOMERS)));
        manage.add(item("Bookings", e -> showScreen(BOOKINGS)));
        if (user.canManageExpenses()) manage.add(item("Expenses", e -> showScreen(EXPENSES)));
        manage.add(item("Staff", e -> showScreen(STAFF)));

        JMenu report = new JMenu("Reports");
        report.add(item("Daily Report", e -> showReport(false)));
        report.add(item("Weekly Report", e -> showReport(true)));
        report.setEnabled(user.canViewReports());   // staff cannot open reports

        JMenu help = new JMenu("Help");
        help.add(item("About Cue 147", e -> about()));

        for (JMenu m : new JMenu[]{file, manage, report, help}) {
            m.setForeground(Theme.TEXT);
            bar.add(m);
        }
        return bar;
    }

    private JMenuItem item(String text, java.awt.event.ActionListener a) {
        JMenuItem i = new JMenuItem(text);
        i.addActionListener(a);
        return i;
    }
}
