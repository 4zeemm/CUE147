package ui;

import dao.GameTableDAO;
import dao.PriceDAO;
import model.Bill;
import model.Customer;
import model.GameTable;
import model.Price;
import exception.BusinessException;
import exception.ClubException;
import exception.InvalidInputException;
import util.Validator;
import service.SessionService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Screen 3 - table cards, overview JTable, right-click menu. Start/End sessions, owner can add/edit/remove tables. */
public class TablesPanel extends ScreenPanel {
    private final GameTableDAO tableDAO = new GameTableDAO();
    private final PriceDAO priceDAO = new PriceDAO();
    private final SessionService sessionService = new SessionService();

    private final JPanel cardGrid = Theme.panel(new GridLayout(0, 3, 16, 16));
    private final Map<Integer, RoundedPanel> cardMap = new HashMap<>();       // table id -> card
    private final Map<Integer, JLabel> timerLabels = new HashMap<>();         // table id -> running time label
    private final DefaultTableModel model = Theme.tableModel("Name", "Type", "Status", "Men / hr", "Ladies / hr", "Player");
    private final JTable overview = new JTable(model);
    private final JComboBox<String> filterBox = Theme.combo("All", "AVAILABLE", "OCCUPIED", "MAINTENANCE");
    private final JPopupMenu popup = new JPopupMenu();
    private final List<GameTable> shown = new ArrayList<>();
    private final Map<String, BigDecimal> rates = new HashMap<>();             // "POOL/MEN" -> 270
    private int selectedId = -1;
    private String lastSignature = "";     // what the tables looked like at the last reload
    private boolean syncing;

    public TablesPanel(MainFrame frame) {
        super(frame);
        List<Component> top = new ArrayList<>();
        top.add(Theme.label("Show:", Theme.BODY, Theme.MUTED));
        top.add(filterBox);
        if (frame.getUser().canManageTables()) {
            top.add(Theme.button("Add Table", Theme.GREEN, e -> showTableDialog(null)));
            top.add(Theme.button("Edit Table", Theme.BLUE, e -> editSelected()));
            top.add(Theme.button("Remove Table", Theme.RED, e -> removeSelected()));
            top.add(Theme.button("Maintenance", Theme.NEUTRAL, e -> toggleMaintenance()));
        }
        add(header("Table Management", "Click a card to select it, right-click for more options",
                top.toArray(new Component[0])), BorderLayout.NORTH);

        JPanel wrap = Theme.panel(new BorderLayout());
        wrap.add(cardGrid, BorderLayout.NORTH);
        JScrollPane cardScroll = Theme.scroll(wrap);
        cardScroll.getViewport().setBackground(Theme.BG);
        cardScroll.setBorder(null);
        cardScroll.getVerticalScrollBar().setUnitIncrement(16);

        Theme.styleTable(overview);
        overview.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane tableScroll = Theme.scroll(overview);
        tableScroll.setPreferredSize(new Dimension(0, 130));

        JPanel centre = Theme.panel(new BorderLayout(0, 12));
        centre.add(cardScroll, BorderLayout.CENTER);
        centre.add(tableScroll, BorderLayout.SOUTH);
        add(centre, BorderLayout.CENTER);

        buildPopup();
        filterBox.addActionListener(e -> refresh());
        overview.getSelectionModel().addListSelectionListener(e -> {
            if (syncing || e.getValueIsAdjusting()) return;
            int row = overview.getSelectedRow();
            selectedId = row >= 0 && row < shown.size() ? shown.get(row).getId() : -1;
            highlightCards();
        });
        overview.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { tablePopup(e); }
            @Override public void mouseReleased(MouseEvent e) { tablePopup(e); }
        });
    }

    // ---------------- popup menu ----------------

    private void buildPopup() {
        popup.add(menuItem("Start Game", () -> { GameTable t = need(); if (t != null) startGame(t); }));
        popup.add(menuItem("End Game", () -> { GameTable t = need(); if (t != null) endGame(t); }));
        popup.add(menuItem("Food & Drinks", () -> { GameTable t = need(); if (t != null) foodAndDrinks(t); }));
        popup.addSeparator();
        popup.add(menuItem("Cancel Game (started by mistake)", () -> { GameTable t = need(); if (t != null) cancelGame(t); }));
        popup.add(menuItem("Adjust Start Time", () -> { GameTable t = need(); if (t != null) adjustStart(t); }));
        popup.addSeparator();
        popup.add(menuItem("View Details", () -> { GameTable t = need(); if (t != null) showDetails(t); }));
    }

    private JMenuItem menuItem(String text, Runnable action) {
        JMenuItem item = new JMenuItem(text);
        item.addActionListener(e -> action.run());
        return item;
    }

    /** Returns the selected table, or shows a message and returns null. */
    private GameTable need() {
        for (GameTable t : shown) if (t.getId() == selectedId) return t;
        Theme.error(this, "Please select a table first.");
        return null;
    }

    private void tablePopup(MouseEvent e) {
        if (!e.isPopupTrigger()) return;
        int row = overview.rowAtPoint(e.getPoint());
        if (row >= 0) overview.setRowSelectionInterval(row, row);
        popup.show(overview, e.getX(), e.getY());
    }

    // ---------------- loading and building the cards ----------------

    @Override
    public void refresh() {
        try {
            rates.clear();
            for (Price p : priceDAO.findAll()) rates.put(p.getTableType() + "/" + p.getGender(), p.getHourlyRate());
            String filter = (String) filterBox.getSelectedItem();
            shown.clear();
            List<GameTable> allTables = tableDAO.findAll();
            lastSignature = signatureOf(allTables);
            for (GameTable t : allTables) {
                if ("All".equals(filter) || t.getStatus().equals(filter)) shown.add(t);
            }
        } catch (SQLException e) {
            showDbError(e);
            return;
        }

        cardGrid.removeAll();
        cardMap.clear();
        timerLabels.clear();
        syncing = true;
        model.setRowCount(0);
        for (GameTable t : shown) {
            cardGrid.add(buildCard(t));
            model.addRow(new Object[]{t.getName(), t.getTypeLabel(), t.getStatus(), Theme.money0(rate(t, "MEN")),
                    Theme.money0(rate(t, "LADIES")), t.getActivePlayer()});
        }
        while (cardGrid.getComponentCount() % 3 != 0) cardGrid.add(Theme.panel(null));
        for (int i = 0; i < shown.size(); i++) {
            if (shown.get(i).getId() == selectedId) overview.setRowSelectionInterval(i, i);
        }
        syncing = false;
        highlightCards();
        tick();
        cardGrid.revalidate();
        cardGrid.repaint();
    }

    private BigDecimal rate(GameTable t, String gender) {
        BigDecimal r = rates.get(t.getType() + "/" + gender);
        return r == null ? BigDecimal.ZERO : r;
    }

    private RoundedPanel buildCard(GameTable t) {
        RoundedPanel card = new RoundedPanel(new BorderLayout(0, 8));
        card.setBorder(new EmptyBorder(14, 16, 14, 16));
        cardMap.put(t.getId(), card);
        Color statusColor = t.isOccupied() ? Theme.RED : (t.isUnderMaintenance() ? Theme.AMBER : Theme.GREEN);

        JPanel top = Theme.panel(new BorderLayout());
        top.add(Theme.label(t.getName(), Theme.HEADING, Theme.TEXT), BorderLayout.WEST);
        top.add(Theme.label(" " + t.getStatus() + " ", Theme.BOLD, statusColor), BorderLayout.EAST);
        card.add(top, BorderLayout.NORTH);

        JLabel timer = Theme.label("Session: -", Theme.BODY, Theme.TEXT);
        timerLabels.put(t.getId(), timer);
        JPanel info = Theme.panel(new GridLayout(4, 1, 0, 2));
        info.add(Theme.label("Type: " + t.getTypeLabel(), Theme.BODY, Theme.MUTED));
        info.add(Theme.label("Men " + Theme.money0(rate(t, "MEN")) + "  |  Ladies " + Theme.money0(rate(t, "LADIES")), Theme.BODY, Theme.MUTED));
        info.add(Theme.label("Player: " + t.getActivePlayer(), Theme.BODY, Theme.TEXT));
        info.add(timer);
        card.add(info, BorderLayout.CENTER);

        JPanel buttons = Theme.panel(new GridLayout(1, 2, 8, 0));
        RoundButton start = t.isOccupied() ? Theme.button("End Game", Theme.RED, e -> endGame(t))
                : Theme.button("Start Game", Theme.GREEN, e -> startGame(t));
        if (t.isUnderMaintenance()) {
            start.setEnabled(false);
            start.setToolTipText("This table is under maintenance");
        }
        // while a game runs, the second button adds food & drinks; otherwise it shows the details
        RoundButton details = t.isOccupied() ? Theme.button("Food & Drinks", Theme.BLUE, e -> foodAndDrinks(t))
                : Theme.button("Details", Theme.BLUE, e -> showDetails(t));
        for (RoundButton b : new RoundButton[]{start, details}) {
            b.setFont(Theme.SMALL.deriveFont(Font.BOLD));
            b.setBorder(new EmptyBorder(7, 4, 7, 4));
            buttons.add(b);
        }
        card.add(buttons, BorderLayout.SOUTH);

        // click = select, right-click = popup menu
        card.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { cardClicked(t, e); }
            @Override public void mouseReleased(MouseEvent e) { cardClicked(t, e); }
        });
        return card;
    }

    private void cardClicked(GameTable t, MouseEvent e) {
        if (e.getID() == MouseEvent.MOUSE_PRESSED) {
            selectedId = t.getId();
            syncing = true;
            int row = shown.indexOf(t);
            if (row >= 0) overview.setRowSelectionInterval(row, row);
            syncing = false;
            highlightCards();
        }
        if (e.isPopupTrigger()) popup.show(e.getComponent(), e.getX(), e.getY());
    }

    private void highlightCards() {
        for (Map.Entry<Integer, RoundedPanel> e : cardMap.entrySet()) {
            e.getValue().setOutline(e.getKey() == selectedId ? Theme.GOLD : Theme.BORDER);
        }
    }

    /** Called every second by the Swing Timer: updates the running timer on each occupied card. */
    @Override
    public void tick() {
        LocalDateTime now = LocalDateTime.now();
        for (GameTable t : shown) {
            JLabel label = timerLabels.get(t.getId());
            if (label == null) continue;
            if (t.isOccupied()) {
                Duration d = Duration.between(t.getActiveStart(), now);
                label.setText(String.format("Running: %02d:%02d:%02d", d.toHours(), d.toMinutesPart(), d.toSecondsPart()));
            } else {
                label.setText("Session: -");
            }
        }
    }

    // ---------------- actions ----------------

    /** Used by "New Session" (menu / dashboard): starts a game on the first free table. */
    public void startOnFirstFreeTable() {
        refresh();
        for (GameTable t : shown) {
            if (!t.isOccupied()) { startGame(t); return; }
        }
        Theme.info(this, "No table is free right now.", "New Session");
    }

    private void startGame(GameTable t) {
        if (t.isUnderMaintenance()) {
            Theme.info(this, t.getName() + " is under maintenance. Games cannot be started on it.", "Start Game");
            return;
        }
        if (t.isOccupied()) {
            Theme.info(this, t.getName() + " is already in use by " + t.getActivePlayer() + ".", "Start Game");
            return;
        }
        try {
            CustomerPicker picker = new CustomerPicker();
            Customer customer = null;
            while (customer == null) {
                int answer = JOptionPane.showConfirmDialog(this, new Object[]{"Who is playing on " + t.getName() + "?", picker},
                        "Start Game", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
                if (answer != JOptionPane.OK_OPTION) return;
                customer = picker.getSelectedCustomer();
                if (customer == null) Theme.error(this, "Choose the customer in the list (or add a new one).");
            }
            sessionService.startSession(t, customer, frame.getUser());
            selectedId = t.getId();
            refresh();
            frame.setStatus(t.getName() + " is now OCCUPIED");
            frame.refreshNotifications();
            Theme.info(this, "Game started on " + t.getName() + "\nPlayer: " + customer.getName() + "\nRate: "
                    + Theme.money0(rate(t, customer.getGender())) + " / hour", "Game Started");
        } catch (BusinessException e) {
            Theme.error(this, e.getMessage());
            refresh();
        } catch (SQLException e) {
            showDbError(e);
        }
    }

    /** Food & drinks for the running game on this table (they go on the same bill). */
    private void foodAndDrinks(GameTable t) {
        if (!t.isOccupied()) {
            Theme.info(this, t.getName() + " has no running game. Start a game first.", "Food & Drinks");
            return;
        }
        FoodDrinksDialog.open(frame, t.getName(), t.getActivePlayer(), t.getActiveSessionId());
        frame.setStatus("Food & drinks updated for " + t.getName());
    }

    private void endGame(GameTable t) {
        if (!t.isOccupied()) {
            Theme.info(this, t.getName() + " has no running game.", "End Game");
            return;
        }
        if (!Theme.confirm(this, "End the game on " + t.getName() + " (" + t.getActivePlayer() + ") and create the bill?", "End Game")) return;
        try {
            Bill bill = sessionService.endSession(t.getActiveSessionId(), frame.getUser());
            selectedId = t.getId();
            refresh();
            frame.setStatus(t.getName() + " is now AVAILABLE - bill " + bill.getBillNumber() + " created");
            CheckoutDialog.open(frame, bill);   // one-screen checkout: discount, payments, Billed by
            frame.refreshNotifications();
        } catch (BusinessException e) {
            Theme.error(this, e.getMessage());
            refresh();
        } catch (SQLException e) {
            showDbError(e);
        }
    }

    private void showDetails(GameTable t) {
        String text = t.getName() + "\n\nType: " + t.getTypeLabel() + "\nStatus: " + t.getStatus()
                + "\nPrice (Men): " + Theme.money0(rate(t, "MEN")) + " / hour"
                + "\nPrice (Ladies): " + Theme.money0(rate(t, "LADIES")) + " / hour";
        if (t.isUnderMaintenance()) text += "\n\nThis table is under maintenance.";
        if (t.isOccupied()) {
            text += "\n\nPlayer: " + t.getActivePlayer() + "\nStarted: "
                    + t.getActiveStart().format(DateTimeFormatter.ofPattern("dd MMM, HH:mm"));
        }
        Theme.info(this, text, "Table Details");
    }

    /** Cancels a game that was started by mistake (no bill is made). */
    private void cancelGame(GameTable t) {
        if (!t.isOccupied()) {
            Theme.info(this, t.getName() + " has no running game.", "Cancel Game");
            return;
        }
        String reason = JOptionPane.showInputDialog(this, "Why is the game on " + t.getName() + " being cancelled?\n"
                + "(No bill will be made. Allowed for the first " + SessionService.MISTAKE_MINUTES + " minutes; after that only the owner.)",
                "Cancel Game", JOptionPane.QUESTION_MESSAGE);
        if (reason == null) return;
        try {
            sessionService.cancelSession(t.getActiveSessionId(), frame.getUser(), reason);
            selectedId = t.getId();
            refresh();
            frame.setStatus("Game on " + t.getName() + " cancelled");
            frame.refreshNotifications();
        } catch (ClubException e) {
            Theme.error(this, e.getMessage());
            refresh();
        } catch (SQLException e) {
            showDbError(e);
        }
    }

    /** Changes the start time of a running game (for example the staff forgot to press Start). */
    private void adjustStart(GameTable t) {
        if (!t.isOccupied()) {
            Theme.info(this, t.getName() + " has no running game.", "Adjust Start Time");
            return;
        }
        JSpinner timeSpinner = Theme.timeSpinner(t.getActiveStart().getHour(), t.getActiveStart().getMinute());
        int answer = JOptionPane.showConfirmDialog(this, new Object[]{"New start time for " + t.getName() + " (24-hour, HH:mm):", timeSpinner},
                "Adjust Start Time", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (answer != JOptionPane.OK_OPTION) return;
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime newStart = now.toLocalDate().atTime(Theme.timeOf(timeSpinner));
        if (newStart.isAfter(now)) newStart = newStart.minusDays(1);   // for example a game that started before midnight
        try {
            sessionService.adjustStartTime(t.getActiveSessionId(), newStart, frame.getUser());
            selectedId = t.getId();
            refresh();
            frame.setStatus("Start time of " + t.getName() + " changed to " + newStart.format(DateTimeFormatter.ofPattern("HH:mm")));
        } catch (ClubException e) {
            Theme.error(this, e.getMessage());
        } catch (SQLException e) {
            showDbError(e);
        }
    }

    /** Owner only: puts the selected table under maintenance, or back in service. */
    private void toggleMaintenance() {
        GameTable t = need();
        if (t == null) return;
        if (t.isOccupied()) {
            Theme.error(this, "A game is running on " + t.getName() + ". End the game first.");
            return;
        }
        try {
            tableDAO.setMaintenance(t.getId(), !t.isUnderMaintenance());
            refresh();
            frame.setStatus(t.getName() + (t.isUnderMaintenance() ? " is back in service" : " is under maintenance"));
        } catch (SQLException e) {
            showDbError(e);
        }
    }

    /** Called every 15 seconds: reloads the cards only if something changed (another counter started or ended a game). */
    @Override
    public void autoRefresh() {
        try {
            if (!signatureOf(tableDAO.findAll()).equals(lastSignature)) refresh();
        } catch (SQLException e) {
            // ignore: the next automatic check will try again
        }
    }

    private String signatureOf(List<GameTable> tables) {
        StringBuilder sb = new StringBuilder();
        for (GameTable t : tables) {
            sb.append(t.getId()).append(t.getName()).append(t.getType()).append(t.getStatus()).append(t.getActivePlayer()).append(t.getActiveStart()).append(';');
        }
        return sb.toString();
    }

    private void editSelected() {
        GameTable t = need();
        if (t != null) showTableDialog(t);
    }

    private void removeSelected() {
        GameTable t = need();
        if (t == null) return;
        if (t.isOccupied()) {
            Theme.error(this, "Cannot remove a table while a game is running.");
            return;
        }
        if (!Theme.confirm(this, "Remove " + t.getName() + "?", "Remove Table")) return;
        try {
            boolean deleted = tableDAO.remove(t.getId());
            selectedId = -1;
            refresh();
            frame.setStatus(t.getName() + " removed");
            if (!deleted) {
                Theme.info(this, t.getName() + " has game history, so it was switched off (hidden)\ninstead of deleted. Old bills stay safe.", "Table Removed");
            }
        } catch (SQLException e) {
            showDbError(e);
        }
    }

    /** One dialog used for both Add (existing == null) and Edit. */
    private void showTableDialog(GameTable existing) {
        JDialog dialog = new JDialog(frame, existing == null ? "Add Table" : "Edit Table", true);
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(new EmptyBorder(20, 24, 10, 24));
        form.setBackground(Theme.PANEL);

        JComboBox<String> typeBox = Theme.combo("POOL", "SNOOKER", "PS5");
        JTextField nameField = Theme.field(18);
        if (existing != null) {
            typeBox.setSelectedItem(existing.getType());
            nameField.setText(existing.getName());
        }
        Theme.addRow(form, 0, "Table type", typeBox);
        Theme.addRow(form, 1, "Table name", nameField);
        Theme.addRow(form, 2, "", Theme.label("Hourly prices are set in Settings > Pricing", Theme.SMALL, Theme.MUTED));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        buttons.setBackground(Theme.PANEL);
        buttons.add(Theme.button("Cancel", Theme.NEUTRAL, e -> dialog.dispose()));
        buttons.add(Theme.button(existing == null ? "Add" : "Save", Theme.GREEN, e -> {
            String name;
            try {
                name = Validator.requireLabel(nameField.getText(), "Table name");
            } catch (InvalidInputException ex) {
                Theme.error(dialog, ex.getMessage());
                return;
            }
            if (existing != null && existing.isOccupied() && !existing.getType().equals(typeBox.getSelectedItem())) {
                Theme.error(dialog, "Cannot change the type while a game is running.");
                return;
            }
            try {
                String type = (String) typeBox.getSelectedItem();
                if (existing == null) {
                    selectedId = tableDAO.add(name, type);
                    frame.setStatus(name + " added");
                } else {
                    tableDAO.update(existing.getId(), name, type);
                    frame.setStatus(name + " updated");
                }
                dialog.dispose();
                refresh();
            } catch (SQLException ex) {
                if (ex.getErrorCode() == 1062) Theme.error(dialog, "A table with this name already exists.");
                else showDbError(ex);
            }
        }));
        dialog.setLayout(new BorderLayout());
        dialog.add(form, BorderLayout.CENTER);
        dialog.add(buttons, BorderLayout.SOUTH);
        dialog.getContentPane().setBackground(Theme.PANEL);
        dialog.pack();
        dialog.setMinimumSize(new Dimension(400, 0));
        dialog.setLocationRelativeTo(frame);
        dialog.setVisible(true);
    }
}
