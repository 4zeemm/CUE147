package ui;

import dao.BillDAO;
import dao.StaffMemberDAO;
import exception.BusinessException;
import exception.InvalidInputException;
import model.Bill;
import model.PaymentPart;
import model.StaffMember;
import service.BillService;
import service.Checkout;
import service.Checkout.DiscountType;
import util.AppLogger;
import util.AppSettings;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * The one-screen checkout: table time, discount, payments (one or several), "Billed by"
 * and the running total are all in this window. The money rules are in service.Checkout.
 *
 * Quickest way for staff: choose "Billed by" and press Pay Cash or Pay UPI.
 * Split payment: type an amount, Add Payment, repeat, then Complete Bill.
 */
public class CheckoutDialog extends JDialog {
    private static final String[] DISCOUNTS = {"No discount", "Rs. amount", "Percent %"};
    private static final String[] OCCASIONS = {"", "Christmas", "New Year", "Diwali", "Eid", "Birthday", "Regular customer"};

    private final MainFrame frame;
    private final BillService billService = new BillService();
    private final Bill bill;
    private final Checkout checkout;
    private boolean changed = false;

    private final JComboBox<String> discountType = Theme.combo(DISCOUNTS);
    private final JSpinner discountValue = Theme.numberSpinner(0, 0, 1_000_000, 10);
    private final JComboBox<String> occasion = Theme.combo(OCCASIONS);
    private final JLabel subtotalLabel = Theme.label("", Theme.HEADING, Theme.TEXT);
    private final JLabel discountLabel = Theme.label("", Theme.HEADING, Theme.AMBER);
    private final JLabel totalLabel = Theme.label("", Theme.TITLE.deriveFont(30f), Theme.GOLD);
    private final JLabel paidLabel = Theme.label("", Theme.HEADING, Theme.TEXT);
    private final JLabel remainingLabel = Theme.label("", Theme.TITLE, Theme.RED);
    private final JLabel messageLabel = Theme.label(" ", Theme.BOLD, Theme.RED);

    private final JComboBox<String> payMethod = Theme.combo("Cash", "UPI");
    private final JSpinner payAmount = Theme.numberSpinner(0, 0, 1_000_000, 10);
    private final DefaultListModel<String> partsModel = new DefaultListModel<>();
    private final JList<String> partsList = new JList<>(partsModel);
    private final JComboBox<StaffMember> billedBy = new JComboBox<>();
    private final RoundButton completeButton = Theme.button("Complete Bill", Theme.GREEN, e -> complete());

    private CheckoutDialog(MainFrame frame, Bill bill) {
        super(frame, "Checkout - " + bill.getBillNumber(), true);
        this.frame = frame;
        this.bill = bill;
        this.checkout = billService.startCheckout(bill);

        JPanel root = Theme.panel(new BorderLayout(16, 12));
        root.setBorder(new EmptyBorder(16, 18, 14, 18));
        root.add(buildHeader(), BorderLayout.NORTH);
        JPanel middle = Theme.panel(new GridLayout(1, 2, 16, 0));
        middle.add(buildLeft());
        middle.add(buildRight());
        root.add(middle, BorderLayout.CENTER);
        root.add(buildButtons(), BorderLayout.SOUTH);
        setContentPane(root);

        loadStaff();
        showSavedDiscount();
        discountType.addActionListener(e -> applyDiscount());
        discountValue.addChangeListener(e -> applyDiscount());
        occasion.setEditable(true);
        occasion.addActionListener(e -> applyOccasion());
        updateTotals();

        setSize(900, 620);
        setMinimumSize(new Dimension(820, 560));
        setLocationRelativeTo(frame);
    }

    /** Opens the checkout for an unpaid bill. Returns true if the bill was paid or saved. */
    public static boolean open(MainFrame frame, Bill bill) {
        CheckoutDialog d = new CheckoutDialog(frame, bill);
        d.setVisible(true);
        return d.changed;
    }

    // ------------------------------------------------------------------ layout

    private JPanel buildHeader() {
        DateTimeFormatter t = DateTimeFormatter.ofPattern("HH:mm");
        JPanel p = Theme.panel(new GridLayout(2, 1));
        p.add(Theme.label("Checkout  -  " + bill.getCustomerName(), Theme.TITLE, Theme.TEXT));
        p.add(Theme.label(bill.getTableName() + "   |   " + bill.getStartTime().format(t) + " - " + bill.getEndTime().format(t)
                + "   |   Bill " + bill.getBillNumber(), Theme.BODY, Theme.MUTED));
        return p;
    }

    private JPanel buildLeft() {
        // what is charged
        DefaultTableModel items = Theme.tableModel("Item", "Amount");
        String tableLine = "Table time  " + bill.getDurationText() + " @ " + Theme.money0(bill.getHourlyRate()) + "/h";
        if (bill.getDiscountPercent().signum() > 0) tableLine += "  (member " + bill.getDiscountPercent().stripTrailingZeros().toPlainString() + "% off)";
        items.addRow(new Object[]{tableLine, Theme.money(bill.getTableAmount())});
        JTable itemsTable = new JTable(items);
        Theme.styleTable(itemsTable);
        itemsTable.getColumnModel().getColumn(0).setPreferredWidth(310);

        // discount
        JPanel discount = Theme.panel(new GridBagLayout());
        Theme.addRow(discount, 0, "Discount", discountType);
        Theme.addRow(discount, 1, "Value", discountValue);
        Theme.addRow(discount, 2, "Occasion", occasion);

        // billed by
        JPanel staff = Theme.panel(new GridBagLayout());
        Theme.addRow(staff, 0, "Billed by", billedBy);

        RoundedPanel card = new RoundedPanel(new BorderLayout(0, 12));
        card.setBorder(new EmptyBorder(14, 14, 14, 14));
        card.add(Theme.scroll(itemsTable), BorderLayout.CENTER);
        JPanel south = Theme.panel(new BorderLayout(0, 8));
        south.add(discount, BorderLayout.NORTH);
        south.add(staff, BorderLayout.SOUTH);
        card.add(south, BorderLayout.SOUTH);
        return card;
    }

    private JPanel buildRight() {
        JPanel totals = Theme.panel(new GridBagLayout());
        Theme.addRow(totals, 0, "Subtotal", subtotalLabel);
        Theme.addRow(totals, 1, "Discount", discountLabel);
        Theme.addRow(totals, 2, "FINAL TOTAL", totalLabel);
        Theme.addRow(totals, 3, "Paid", paidLabel);
        Theme.addRow(totals, 4, "Remaining", remainingLabel);

        JPanel addRow = Theme.panel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        payAmount.setPreferredSize(new Dimension(120, 34));
        addRow.add(payMethod);
        addRow.add(payAmount);
        addRow.add(Theme.button("Add Payment", Theme.BLUE, e -> addPayment()));
        addRow.add(Theme.button("Remove", Theme.NEUTRAL, e -> removePayment()));

        partsList.setVisibleRowCount(3);
        partsList.setBackground(Theme.FIELD);
        partsList.setForeground(Theme.TEXT);
        partsList.setFont(Theme.BODY);

        JPanel payments = Theme.panel(new BorderLayout(0, 6));
        payments.add(Theme.label("Payments (for a split bill add each part)", Theme.BOLD, Theme.MUTED), BorderLayout.NORTH);
        payments.add(addRow, BorderLayout.CENTER);
        payments.add(Theme.scroll(partsList), BorderLayout.SOUTH);

        RoundedPanel card = new RoundedPanel(new BorderLayout(0, 10));
        card.setBorder(new EmptyBorder(14, 14, 14, 14));
        card.add(totals, BorderLayout.NORTH);
        card.add(payments, BorderLayout.CENTER);
        card.add(messageLabel, BorderLayout.SOUTH);
        return card;
    }

    private JPanel buildButtons() {
        JPanel left = Theme.panel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        RoundButton cash = Theme.button("Pay Cash", Theme.GREEN, e -> payRest("CASH"));
        RoundButton upi = Theme.button("Pay UPI", Theme.BLUE, e -> payRest("UPI"));
        cash.setToolTipText("The remaining amount is paid in cash and the bill is closed");
        upi.setToolTipText("The remaining amount is paid by UPI and the bill is closed");
        left.add(cash);
        left.add(upi);
        JPanel right = Theme.panel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.add(completeButton);
        right.add(Theme.button("Pay Later", Theme.AMBER, e -> payLater()));
        right.add(Theme.button("Close", Theme.NEUTRAL, e -> dispose()));
        JPanel p = Theme.panel(new BorderLayout());
        p.add(left, BorderLayout.WEST);
        p.add(right, BorderLayout.EAST);
        return p;
    }

    // ------------------------------------------------------------------ behaviour

    private void loadStaff() {
        try {
            List<StaffMember> staff = new StaffMemberDAO().findActive();
            StaffMember choose = null;
            for (StaffMember s : staff) {
                billedBy.addItem(s);
                if (bill.getBilledByStaffId() != null && s.getId() == bill.getBilledByStaffId()) choose = s;
                if (choose == null && s.getId() == AppSettings.getLastBilledById()) choose = s;   // last person at this counter
            }
            if (choose != null) billedBy.setSelectedItem(choose);
            else if (staff.size() != 1) billedBy.setSelectedIndex(-1);   // make the person choose
            if (staff.isEmpty()) messageLabel.setText("Add staff names in Settings > Staff List first.");
        } catch (SQLException e) {
            AppLogger.error("Could not load the staff list", e);
            messageLabel.setText("Could not load the staff list.");
        }
    }

    private void showSavedDiscount() {
        DiscountType type = checkout.getDiscountType();
        discountType.setSelectedIndex(type == DiscountType.FLAT ? 1 : type == DiscountType.PERCENT ? 2 : 0);
        discountValue.setValue(checkout.getDiscountValue().doubleValue());
        occasion.setSelectedItem(checkout.getOccasion());
    }

    private void applyDiscount() {
        int i = discountType.getSelectedIndex();
        DiscountType type = i == 1 ? DiscountType.FLAT : i == 2 ? DiscountType.PERCENT : DiscountType.NONE;
        discountValue.setEnabled(type != DiscountType.NONE);
        occasion.setEnabled(type != DiscountType.NONE);
        BigDecimal value = BigDecimal.valueOf(((Number) discountValue.getValue()).doubleValue()).setScale(2, RoundingMode.HALF_UP);
        try {
            checkout.setDiscount(type, value);
            messageLabel.setText(" ");
        } catch (InvalidInputException e) {
            messageLabel.setText(firstLine(e.getMessage()));   // shown in red; the old discount stays
        }
        updateTotals();
    }

    private void applyOccasion() {
        try {
            checkout.setOccasion(String.valueOf(occasion.getSelectedItem() == null ? "" : occasion.getSelectedItem()));
        } catch (InvalidInputException e) {
            messageLabel.setText(e.getMessage());
        }
    }

    private void addPayment() {
        String method = payMethod.getSelectedIndex() == 0 ? "CASH" : "UPI";
        BigDecimal amount = BigDecimal.valueOf(((Number) payAmount.getValue()).doubleValue()).setScale(2, RoundingMode.HALF_UP);
        try {
            checkout.addPayment(method, amount);
            messageLabel.setText(" ");
        } catch (InvalidInputException e) {
            messageLabel.setText(firstLine(e.getMessage()));
        }
        updateTotals();
    }

    private void removePayment() {
        int i = partsList.getSelectedIndex();
        if (i < 0) i = partsModel.size() - 1;   // nothing selected: remove the last one
        checkout.removePayment(i);
        updateTotals();
    }

    /** Pay Cash / Pay UPI: the rest of the bill is paid with this method and the bill is closed. */
    private void payRest(String method) {
        if (checkout.getRemaining().signum() > 0) {
            try {
                checkout.addPayment(method, checkout.getRemaining());
            } catch (InvalidInputException e) {
                messageLabel.setText(firstLine(e.getMessage()));
                return;
            }
        }
        updateTotals();
        complete();
    }

    private void complete() {
        applyOccasion();
        StaffMember staff = (StaffMember) billedBy.getSelectedItem();
        try {
            billService.completeCheckout(bill, checkout, staff, frame.getUser());
        } catch (BusinessException e) {
            Theme.error(this, e.getMessage());
            return;
        } catch (SQLException e) {
            AppLogger.error("Could not save the checkout", e);
            Theme.error(this, "Could not save the payment:\n" + e.getMessage());
            return;
        }
        AppSettings.setLastBilledById(staff.getId());
        changed = true;
        String how = checkout.getMethodLabel() == null ? "(no payment needed)" : checkout.getMethodLabel();
        Theme.info(this, "Payment of " + Theme.money(checkout.getFinalTotal()) + " received by " + how + ".", "Payment Saved");
        dispose();
        try {
            ReceiptDialog.showText(frame, "Bill - " + bill.getBillNumber(), new BillDAO().findById(bill.getId()));   // to print it
        } catch (SQLException e) {
            AppLogger.error("Could not reload the bill", e);
        }
    }

    private void payLater() {
        applyOccasion();
        StaffMember staff = (StaffMember) billedBy.getSelectedItem();
        try {
            billService.saveForLater(bill, checkout, staff);
        } catch (BusinessException e) {
            Theme.error(this, e.getMessage());
            return;
        } catch (SQLException e) {
            AppLogger.error("Could not save the bill for later", e);
            Theme.error(this, "Could not save the bill:\n" + e.getMessage());
            return;
        }
        AppSettings.setLastBilledById(staff.getId());
        changed = true;
        frame.setStatus("Bill " + bill.getBillNumber() + " saved as UNPAID (" + Theme.money(checkout.getFinalTotal()) + ")");
        dispose();
    }

    /** Shows the running totals and the payment parts; the amount box always suggests the remaining amount. */
    private void updateTotals() {
        subtotalLabel.setText(Theme.money(checkout.getSubtotal()));
        discountLabel.setText("- " + Theme.money(checkout.getDiscountAmount()));
        totalLabel.setText(Theme.money(checkout.getFinalTotal()));
        paidLabel.setText(Theme.money(checkout.getPaid()));
        BigDecimal remaining = checkout.getRemaining();
        remainingLabel.setText(Theme.money(remaining));
        remainingLabel.setForeground(remaining.signum() == 0 ? Theme.GREEN : Theme.RED);
        partsModel.clear();
        for (PaymentPart p : checkout.getParts()) {
            partsModel.addElement((p.getMethod().equals("CASH") ? "Cash" : "UPI") + "   " + Theme.money(p.getAmount()));
        }
        payAmount.setValue(remaining.doubleValue());
        completeButton.setEnabled(checkout.isFullyPaid());
    }

    private static String firstLine(String text) {
        int n = text.indexOf('\n');
        return n < 0 ? text : text.substring(0, n);
    }
}
