package ui;

import dao.BillDAO;
import exception.BusinessException;
import exception.ClubException;
import model.Bill;
import service.BillService;
import service.SessionService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Screen 6 - bill history, payments and bill printing. Bills are created when a game ends. */
public class BillingPanel extends ScreenPanel {
    private final BillDAO billDAO = new BillDAO();
    private final BillService billService = new BillService();
    private final SessionService sessionService = new SessionService();
    private final DefaultTableModel model = Theme.tableModel("Bill No", "Date", "Customer", "Table", "Duration", "Amount", "Payment", "Method", "Billed by");
    private final JTable table = new JTable(model);
    private final List<Bill> shown = new ArrayList<>();

    private final JTextField searchField = Theme.field(14);
    private final JCheckBox unpaidBox = Theme.checkBox("Unpaid only");
    private final JComboBox<Object> staffBox = new JComboBox<>();   // "All staff" or one StaffMember

    private final JLabel billNoLabel = Theme.label("-", Theme.HEADING, Theme.GOLD);
    private final JLabel customerLabel = Theme.label("-", Theme.BODY, Theme.TEXT);
    private final JLabel tableLabel = Theme.label("-", Theme.BODY, Theme.TEXT);
    private final JLabel timeLabel = Theme.label("-", Theme.BODY, Theme.TEXT);
    private final JLabel durationLabel = Theme.label("-", Theme.BODY, Theme.TEXT);
    private final JLabel rateLabel = Theme.label("-", Theme.BODY, Theme.TEXT);
    private final JLabel subtotalLabel = Theme.label("-", Theme.BODY, Theme.TEXT);
    private final JLabel discountLabel = Theme.label("-", Theme.BODY, Theme.AMBER);
    private final JLabel totalLabel = Theme.label("-", new Font("SansSerif", Font.BOLD, 26), Theme.GOLD);
    private final JLabel statusLabel = Theme.label("-", Theme.HEADING, Theme.GREEN);
    private final JLabel staffLabel = Theme.label("-", Theme.BODY, Theme.TEXT);

    public BillingPanel(MainFrame frame) {
        super(frame);
        add(header("Billing", "Bill history and payments (a bill is created when a game ends)",
                Theme.label("Search:", Theme.BODY, Theme.MUTED), searchField, staffBox, unpaidBox,
                Theme.button("Search", Theme.BLUE, e -> refresh()),
                Theme.button("Export CSV", Theme.AMBER, e -> exportTable("bills", model)),
                Theme.button("Refresh", Theme.NEUTRAL, e -> { searchField.setText(""); unpaidBox.setSelected(false); staffBox.setSelectedIndex(0); refresh(); })), BorderLayout.NORTH);

        Theme.styleTable(table);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getSelectionModel().addListSelectionListener(e -> { if (!e.getValueIsAdjusting()) showSelected(); });
        // Duration and Method are shown in the details card, so we hide those two columns here.
        // (The data stays in the model, so "Export CSV" still saves every column.)
        table.removeColumn(table.getColumnModel().getColumn(7));   // Method
        table.removeColumn(table.getColumnModel().getColumn(4));   // Duration
        table.removeColumn(table.getColumnModel().getColumn(1));   // Date (the bill number contains the date)
        int[] widths = {165, 110, 110, 85, 75, 90};
        for (int i = 0; i < widths.length; i++) table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        searchField.addActionListener(e -> refresh());
        unpaidBox.addActionListener(e -> refresh());
        staffBox.setName("billedByFilter");
        staffBox.setFont(Theme.BODY);
        staffBox.setToolTipText("Show only the bills of one person (Billed by)");
        staffBox.addItem("All staff");
        staffBox.addActionListener(e -> { if (isShowing()) refresh(); });

        JPanel centre = Theme.panel(new BorderLayout(16, 0));
        centre.add(Theme.scroll(table), BorderLayout.CENTER);
        centre.add(buildDetails(), BorderLayout.EAST);
        add(centre, BorderLayout.CENTER);
    }

    private JPanel buildDetails() {
        RoundedPanel card = new RoundedPanel(new BorderLayout(0, 10));
        card.setBorder(new EmptyBorder(16, 16, 16, 16));
        card.setPreferredSize(new Dimension(355, 0));
        card.add(Theme.label("Selected Bill", Theme.HEADING, Theme.TEXT), BorderLayout.NORTH);

        JPanel info = Theme.panel(new GridBagLayout());
        Theme.addRow(info, 0, "Bill no", billNoLabel);
        Theme.addRow(info, 1, "Customer", customerLabel);
        Theme.addRow(info, 2, "Table", tableLabel);
        Theme.addRow(info, 3, "Start - End", timeLabel);
        Theme.addRow(info, 4, "Duration", durationLabel);
        Theme.addRow(info, 5, "Hourly rate", rateLabel);
        Theme.addRow(info, 6, "Subtotal", subtotalLabel);
        Theme.addRow(info, 7, "Discount", discountLabel);
        Theme.addRow(info, 8, "TOTAL", totalLabel);
        Theme.addRow(info, 9, "Payment", statusLabel);
        Theme.addRow(info, 10, "Billed by", staffLabel);
        JPanel wrap = Theme.panel(new BorderLayout());
        wrap.add(info, BorderLayout.NORTH);
        card.add(wrap, BorderLayout.CENTER);

        JPanel row1 = Theme.panel(new GridLayout(1, 2, 8, 0));
        row1.add(Theme.button("Checkout / Pay", Theme.GREEN, e -> openCheckout()));
        row1.add(Theme.button("View / Print Bill", Theme.BLUE, e -> viewBill()));
        // corrections: everybody may reopen a game, only the owner may cancel / refund or change a payment method
        boolean owner = frame.getUser().canCorrectFinancialRecords();
        JPanel row2 = Theme.panel(new GridLayout(1, owner ? 3 : 1, 8, 0));
        row2.add(Theme.button("Reopen Game", Theme.AMBER, e -> reopenGame()));
        if (owner) {
            row2.add(Theme.button("Cancel Bill", Theme.RED, e -> cancelBill()));
            row2.add(Theme.button("Change Method", Theme.NEUTRAL, e -> changeMethod()));
        }
        for (JPanel row : new JPanel[]{row1, row2}) {
            for (Component c : row.getComponents()) {
                ((JComponent) c).setBorder(new EmptyBorder(8, 2, 8, 2));
                ((JComponent) c).setFont(Theme.SMALL.deriveFont(Font.BOLD));
            }
        }
        JPanel buttons = Theme.panel(new GridLayout(2, 1, 0, 8));
        buttons.add(row1);
        buttons.add(row2);
        card.add(buttons, BorderLayout.SOUTH);
        return card;
    }

    @Override
    public void refresh() {
        try {
            shown.clear();
            loadStaffFilter();
            Object who = staffBox.getSelectedItem();
            Integer staffId = who instanceof model.StaffMember ? ((model.StaffMember) who).getId() : null;
            shown.addAll(billDAO.search(searchField.getText().trim(), unpaidBox.isSelected(), staffId));
        } catch (SQLException e) {
            showDbError(e);
            return;
        }
        model.setRowCount(0);
        DateTimeFormatter f = DateTimeFormatter.ofPattern("dd MMM");   // short date, the bill number has the full date
        for (Bill b : shown) {
            model.addRow(new Object[]{b.getBillNumber(), b.getCreatedAt().format(f), b.getCustomerName(), b.getTableName(),
                    b.getDurationText(), Theme.money(b.getFinalAmount()), b.getPaymentLabel(),
                    b.getPaymentMethod(), b.getBilledBy()});
        }
        showSelected();
        frame.setStatus(shown.size() + " bill(s) found");
    }

    /** Fills the Billed-by filter with every name (also switched-off people, for old bills). */
    private void loadStaffFilter() throws SQLException {
        java.util.List<model.StaffMember> staff = new dao.StaffMemberDAO().findAll();
        if (staffBox.getItemCount() == staff.size() + 1) return;
        Object chosen = staffBox.getSelectedItem();
        staffBox.removeAllItems();
        staffBox.addItem("All staff");
        for (model.StaffMember m : staff) {
            staffBox.addItem(m);
            if (chosen instanceof model.StaffMember && ((model.StaffMember) chosen).getId() == m.getId()) staffBox.setSelectedItem(m);
        }
    }

    private Bill selectedBill() {
        int row = table.getSelectedRow();
        return row >= 0 && row < shown.size() ? shown.get(row) : null;
    }

    private void showSelected() {
        Bill b = selectedBill();
        if (b == null) {
            for (JLabel l : new JLabel[]{billNoLabel, customerLabel, tableLabel, timeLabel, durationLabel, rateLabel,
                    subtotalLabel, discountLabel, totalLabel, statusLabel, staffLabel}) l.setText("-");
            return;
        }
        DateTimeFormatter t = DateTimeFormatter.ofPattern("HH:mm");
        billNoLabel.setText(b.getBillNumber());
        customerLabel.setText(b.getCustomerName());
        tableLabel.setText(b.getTableName());
        timeLabel.setText(b.getStartTime().format(t) + " - " + b.getEndTime().format(t));
        durationLabel.setText(b.getDurationText());
        rateLabel.setText(Theme.money0(b.getHourlyRate()) + " / hour");
        subtotalLabel.setText(Theme.money(b.getChargesSubtotal()));
        String discount = b.getDiscountLabel().isEmpty() ? "" : "- " + Theme.money(b.getBillDiscountAmount()) + "  " + b.getDiscountLabel();
        if (b.getDiscountPercent().signum() > 0) discount = (discount.isEmpty() ? "" : discount + ", ") + "member " + b.getDiscountPercent().stripTrailingZeros().toPlainString() + "%";
        discountLabel.setText(discount.isEmpty() ? "none" : discount);
        totalLabel.setText(Theme.money(b.getFinalAmount()));
        String label = b.getPaymentLabel();
        statusLabel.setText(b.isPaid() ? "PAID (" + b.getPaymentMethod() + ")" : label);
        statusLabel.setForeground(b.isPaid() ? Theme.GREEN : (label.equals("UNPAID") ? Theme.RED : Theme.AMBER));
        staffLabel.setText(b.getBilledBy());
    }

    /** Brings an accidentally ended game back (only while its bill is unpaid). */
    private void reopenGame() {
        Bill b = selectedBill();
        if (b == null) { Theme.error(this, "Select a bill in the table first."); return; }
        if (!Theme.confirm(this, "Reopen the game of " + b.getCustomerName() + " on " + b.getTableName() + "?\n\n"
                + "Bill " + b.getBillNumber() + " will be cancelled and the table will be OCCUPIED again.\n"
                + "A new bill is made when you end the game again.", "Reopen Game")) return;
        try {
            sessionService.reopenSession(b, frame.getUser());
            refresh();
            frame.refreshNotifications();
            frame.setStatus("Game on " + b.getTableName() + " reopened");
            Theme.info(this, "The game is running again on " + b.getTableName() + ".\nSee the Tables screen.", "Game Reopened");
        } catch (BusinessException e) {
            Theme.error(this, e.getMessage());
        } catch (SQLException e) {
            showDbError(e);
        }
    }

    /** Owner only: cancels a wrong bill. A paid bill is refunded. The bill stays in the database. */
    private void cancelBill() {
        Bill b = selectedBill();
        if (b == null) { Theme.error(this, "Select a bill in the table first."); return; }
        if (b.isCancelled()) { Theme.info(this, "This bill is already cancelled.", "Cancel Bill"); return; }
        String warning = b.isPaid() ? "This bill is PAID. Cancelling it REFUNDS " + Theme.money(b.getFinalAmount()) + "."
                                    : "This bill is not paid yet.";
        String reason = JOptionPane.showInputDialog(this, warning + "\nWhy is bill " + b.getBillNumber() + " being cancelled?",
                "Cancel Bill", JOptionPane.QUESTION_MESSAGE);
        if (reason == null) return;
        try {
            billService.cancelBill(b, frame.getUser(), reason);
            refresh();
            frame.refreshNotifications();
            frame.setStatus("Bill " + b.getBillNumber() + (b.isPaid() ? " refunded" : " cancelled"));
        } catch (ClubException e) {
            Theme.error(this, e.getMessage());
        } catch (SQLException e) {
            showDbError(e);
        }
    }

    /** Owner only: corrects Cash/UPI of a bill that was already paid. */
    private void changeMethod() {
        Bill b = selectedBill();
        if (b == null) { Theme.error(this, "Select a bill in the table first."); return; }
        if (!b.isPaid()) { Theme.error(this, "Only a PAID bill has a payment method to change."); return; }
        Object[] methods = {"CASH", "UPI"};
        Object choice = JOptionPane.showInputDialog(this, "Payment method of bill " + b.getBillNumber() + ":", "Change Payment Method",
                JOptionPane.QUESTION_MESSAGE, null, methods, b.getPaymentMethod());
        if (choice == null) return;
        try {
            billService.changePaymentMethod(b, (String) choice, frame.getUser());
            refresh();
            frame.setStatus("Payment method changed to " + choice);
        } catch (BusinessException e) {
            Theme.error(this, e.getMessage());
        } catch (SQLException e) {
            showDbError(e);
        }
    }

    /** Opens the checkout of an unpaid bill (discount, payments, Billed by). */
    private void openCheckout() {
        Bill b = selectedBill();
        if (b == null) { Theme.error(this, "Select a bill in the table first."); return; }
        if (b.isCancelled()) { Theme.error(this, "This bill is cancelled."); return; }
        if (b.isPaid()) { Theme.info(this, "Bill " + b.getBillNumber() + " is already paid.", "Checkout"); return; }
        if (CheckoutDialog.open(frame, b)) {
            refresh();
            frame.refreshNotifications();
        }
    }

    /** Shows the bill text so it can be printed. */
    private void viewBill() {
        Bill b = selectedBill();
        if (b == null) { Theme.error(this, "Select a bill in the table first."); return; }
        try {
            ReceiptDialog.showText(frame, "Bill - " + b.getBillNumber(), billDAO.findById(b.getId()));   // with the payment parts
        } catch (SQLException e) {
            showDbError(e);
        }
    }
}
