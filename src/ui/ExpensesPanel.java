package ui;

import dao.ExpenseDAO;
import exception.InvalidInputException;
import model.Expense;
import util.AppLogger;
import util.Validator;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Screen (owner only) - record and view club expenses. Expenses are never deleted. */
public class ExpensesPanel extends ScreenPanel {
    private final ExpenseDAO expenseDAO = new ExpenseDAO();
    private final DefaultTableModel model = Theme.tableModel("Date", "Category", "Amount", "Method", "Description", "Recorded by");
    private final JTable table = new JTable(model);
    private final List<Expense> shown = new ArrayList<>();

    private final JComboBox<String> categoryBox = Theme.combo(Expense.CATEGORIES);
    private final JSpinner amountSpinner = Theme.numberSpinner(0, 0, 10000000, 100);
    private final JTextField dateField = Theme.field(12);
    private final JRadioButton cashRadio = Theme.radio("Cash");
    private final JRadioButton upiRadio = Theme.radio("UPI");
    private final JTextArea descriptionArea = new JTextArea(3, 20);
    private final JLabel totalLabel = Theme.label("", Theme.HEADING, Theme.GOLD);

    public ExpensesPanel(MainFrame frame) {
        super(frame);
        add(header("Expenses", "Rent, electricity, salaries, maintenance and more",
                Theme.button("Edit", Theme.GREEN, e -> editExpense()),
                Theme.button("Delete", Theme.RED, e -> deleteExpense()),
                Theme.button("View Voucher", Theme.BLUE, e -> showVoucher()),
                Theme.button("Export CSV", Theme.AMBER, e -> exportTable("expenses", model)),
                Theme.button("Refresh", Theme.NEUTRAL, e -> refresh())), BorderLayout.NORTH);

        Theme.styleTable(table);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JPanel left = Theme.panel(new BorderLayout(0, 8));
        left.add(Theme.scroll(table), BorderLayout.CENTER);
        left.add(totalLabel, BorderLayout.SOUTH);

        JPanel centre = Theme.panel(new BorderLayout(16, 0));
        centre.add(buildForm(), BorderLayout.WEST);
        centre.add(left, BorderLayout.CENTER);
        add(centre, BorderLayout.CENTER);
        clearForm();
    }

    private JPanel buildForm() {
        RoundedPanel card = new RoundedPanel(new BorderLayout(0, 10));
        card.setBorder(new EmptyBorder(16, 16, 16, 16));
        card.setPreferredSize(new Dimension(360, 0));
        card.add(Theme.label("New Expense", Theme.HEADING, Theme.TEXT), BorderLayout.NORTH);

        ButtonGroup group = new ButtonGroup();
        group.add(cashRadio);
        group.add(upiRadio);
        JPanel method = Theme.panel(new GridLayout(1, 2));
        method.add(cashRadio);
        method.add(upiRadio);

        descriptionArea.setFont(Theme.BODY);
        descriptionArea.setBackground(Theme.FIELD);
        descriptionArea.setForeground(Theme.TEXT);
        descriptionArea.setCaretColor(Theme.TEXT);
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);
        descriptionArea.setBorder(new EmptyBorder(6, 8, 6, 8));

        JPanel form = Theme.panel(new GridBagLayout());
        Theme.addRow(form, 0, "Category", categoryBox);
        Theme.addRow(form, 1, "Amount (₹)", amountSpinner);
        Theme.addRow(form, 2, "Date", dateField);
        Theme.addRow(form, 3, "Paid by", method);
        Theme.addRow(form, 4, "Description", Theme.scroll(descriptionArea));
        JPanel wrap = Theme.panel(new BorderLayout());
        wrap.add(form, BorderLayout.NORTH);
        card.add(wrap, BorderLayout.CENTER);

        JPanel buttons = Theme.panel(new GridLayout(1, 2, 10, 0));
        buttons.add(Theme.button("Add Expense", Theme.GREEN, e -> addExpense()));
        buttons.add(Theme.button("Clear", Theme.NEUTRAL, e -> clearForm()));
        card.add(buttons, BorderLayout.SOUTH);
        return card;
    }

    @Override
    public void refresh() {
        try {
            shown.clear();
            shown.addAll(expenseDAO.findAll());
            model.setRowCount(0);
            for (Expense e : shown) {
                model.addRow(new Object[]{e.getDate(), e.getCategory(), Theme.money(e.getAmount()), e.getPaymentMethod(),
                        e.getDescription(), e.getRecordedBy()});
            }
            LocalDate today = LocalDate.now();
            totalLabel.setText("Today: " + Theme.money(expenseDAO.totalBetween(today, today))
                    + "     This month: " + Theme.money(expenseDAO.totalBetween(today.withDayOfMonth(1), today)));
        } catch (SQLException e) {
            showDbError(e);
        }
    }

    private void addExpense() {
        try {
            BigDecimal amount = Validator.requirePositive(
                    BigDecimal.valueOf(((Number) amountSpinner.getValue()).doubleValue()).setScale(2, RoundingMode.HALF_UP), "Amount");
            LocalDate date = Validator.requireDate(dateField.getText());
            Validator.requireNotFuture(date);
            Expense expense = new Expense(0, (String) categoryBox.getSelectedItem(), amount, date,
                    descriptionArea.getText().trim(), cashRadio.isSelected() ? "CASH" : "UPI");
            expenseDAO.add(expense, frame.getUser().getId());
            clearForm();
            refresh();
            frame.setStatus("Expense of " + Theme.money(amount) + " recorded");
            Theme.info(this, "Expense saved: " + expense.getCategory() + " " + Theme.money(amount), "Expense Added");
        } catch (InvalidInputException e) {
            Theme.error(this, e.getMessage());
        } catch (SQLException e) {
            showDbError(e);
        }
    }

    /** Opens a small window to change the selected expense (owner only). */
    private void editExpense() {
        if (!frame.getUser().canManageExpenses()) {
            Theme.error(this, "Only the owner can edit expenses.");
            return;
        }
        int row = table.getSelectedRow();
        if (row < 0 || row >= shown.size()) {
            Theme.error(this, "Select an expense in the table first.");
            return;
        }
        Expense old = shown.get(row);     // keeps the real database id
        JDialog dialog = new JDialog(frame, "Edit Expense", true);
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(new EmptyBorder(20, 24, 10, 24));
        form.setBackground(Theme.PANEL);
        JComboBox<String> categoryEdit = Theme.combo(Expense.CATEGORIES);
        categoryEdit.setSelectedItem(old.getCategory());
        JSpinner amountEdit = Theme.numberSpinner(old.getAmount().doubleValue(), 0, 10000000, 100);
        JTextField dateEdit = Theme.field(14);
        dateEdit.setText(old.getDate().toString());
        JRadioButton cashEdit = Theme.radio("Cash");
        JRadioButton upiEdit = Theme.radio("UPI");
        ButtonGroup group = new ButtonGroup();
        group.add(cashEdit);
        group.add(upiEdit);
        (old.getPaymentMethod().equals("CASH") ? cashEdit : upiEdit).setSelected(true);
        JPanel method = Theme.panel(new GridLayout(1, 2));
        method.add(cashEdit);
        method.add(upiEdit);
        JTextField descriptionEdit = Theme.field(14);
        descriptionEdit.setText(old.getDescription());
        Theme.addRow(form, 0, "Category", categoryEdit);
        Theme.addRow(form, 1, "Amount (\u20B9)", amountEdit);
        Theme.addRow(form, 2, "Date", dateEdit);
        Theme.addRow(form, 3, "Paid by", method);
        Theme.addRow(form, 4, "Description", descriptionEdit);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        buttons.setBackground(Theme.PANEL);
        buttons.add(Theme.button("Cancel", Theme.NEUTRAL, e -> dialog.dispose()));
        buttons.add(Theme.button("Save", Theme.GREEN, e -> {
            try {
                BigDecimal amount = Validator.requirePositive(
                        BigDecimal.valueOf(((Number) amountEdit.getValue()).doubleValue()).setScale(2, RoundingMode.HALF_UP), "Amount");
                LocalDate date = Validator.requireDate(dateEdit.getText());
                Validator.requireNotFuture(date);
                Expense changed = new Expense(old.getId(), (String) categoryEdit.getSelectedItem(), amount, date,
                        descriptionEdit.getText().trim(), cashEdit.isSelected() ? "CASH" : "UPI");
                boolean saved = expenseDAO.update(changed);       // UPDATE ... WHERE expense_id = ?
                dialog.dispose();
                refresh();
                if (saved) frame.setStatus("Expense updated");
                else Theme.info(this, "This expense no longer exists.", "Edit Expense");
            } catch (InvalidInputException ex) {
                Theme.error(dialog, ex.getMessage());
            } catch (SQLException ex) {
                showDbError(ex);
            }
        }));
        dialog.setLayout(new BorderLayout());
        dialog.add(form, BorderLayout.CENTER);
        dialog.add(buttons, BorderLayout.SOUTH);
        dialog.getContentPane().setBackground(Theme.PANEL);
        dialog.pack();
        dialog.setMinimumSize(new Dimension(420, 0));
        dialog.setLocationRelativeTo(frame);
        dialog.setVisible(true);
    }

    /** Deletes the selected expense after the owner confirms. */
    private void deleteExpense() {
        // Only the owner may delete (Staff cannot even open this screen, this is a second safety check)
        if (!frame.getUser().canManageExpenses()) {
            Theme.error(this, "Only the owner can delete expenses.");
            return;
        }
        int row = table.getSelectedRow();
        if (row < 0 || row >= shown.size()) {
            Theme.error(this, "Select an expense in the table first.");
            return;
        }
        Expense expense = shown.get(row);   // the Expense object keeps the real database id
        Object[] choices = {"Yes", "Cancel"};
        int answer = JOptionPane.showOptionDialog(this, "Are you sure you want to delete this expense?\n\n"
                + expense.getCategory() + "  " + Theme.money(expense.getAmount()) + "  on " + expense.getDate(),
                "Delete Expense", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null, choices, choices[1]);
        if (answer != 0) return;            // Cancel (or closing the window): nothing is deleted
        try {
            boolean deleted = expenseDAO.delete(expense.getId());   // delete by id, not by row position
            refresh();                                              // the list and the totals are reloaded
            if (deleted) {
                AppLogger.info("Expense " + expense.getId() + " deleted by " + frame.getUser().getUsername());
                frame.setStatus("Expense deleted");
            } else {
                Theme.info(this, "This expense was already deleted.", "Delete Expense");
            }
        } catch (SQLException e) {
            showDbError(e);
        }
    }

    /** Opens the voucher of the selected expense. Expense implements Receiptable, just like Bill. */
    private void showVoucher() {
        int row = table.getSelectedRow();
        if (row < 0 || row >= shown.size()) {
            Theme.error(this, "Select an expense in the table first.");
            return;
        }
        Expense e = shown.get(row);
        ReceiptDialog.showText(frame, "Expense voucher", e);
    }

    private void clearForm() {
        categoryBox.setSelectedIndex(0);
        amountSpinner.setValue(0.0);
        dateField.setText(LocalDate.now().toString());
        cashRadio.setSelected(true);
        descriptionArea.setText("");
    }
}
