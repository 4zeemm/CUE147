package ui;

import dao.BillDAO;
import dao.CustomerDAO;
import model.Bill;
import model.Customer;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import exception.InvalidInputException;
import java.sql.SQLException;
import util.Validator;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Screen 4 - customers saved in MySQL: add, edit, search and view history. */
public class CustomersPanel extends ScreenPanel {
    private final CustomerDAO customerDAO = new CustomerDAO();
    private final BillDAO billDAO = new BillDAO();

    private final DefaultTableModel model = Theme.tableModel("ID", "Name", "Phone", "Gender", "Visits", "Total Spent", "Registered");
    private final JTable table = new JTable(model);
    private final List<Customer> shown = new ArrayList<>();

    private final JTextField searchField = Theme.field(16);
    private final JTextField nameField = Theme.field(16);
    private final JTextField phoneField = Theme.field(16);
    private final JComboBox<String> genderBox = Theme.combo("Men", "Ladies");
    private final JLabel countLabel = Theme.label("", Theme.SMALL, Theme.MUTED);

    public CustomersPanel(MainFrame frame) {
        super(frame);

        searchField.setToolTipText("Search by name or phone");
        java.util.List<Component> top = new ArrayList<>();
        top.add(Theme.label("Search:", Theme.BODY, Theme.MUTED));
        top.add(searchField);
        top.add(Theme.button("Search", Theme.BLUE, e -> loadTable(searchField.getText().trim())));
        top.add(Theme.button("Show All", Theme.NEUTRAL, e -> { searchField.setText(""); loadTable(""); }));
        top.add(Theme.button("Add Customer", Theme.GREEN, e -> focusForm()));
        if (frame.getUser().canCorrectFinancialRecords()) {   // owner only
            top.add(Theme.button("Delete", Theme.RED, e -> deleteCustomer()));
        }
        add(header("Customers", "Search, register and manage customers", top.toArray(new Component[0])), BorderLayout.NORTH);

        Theme.styleTable(table);
        int[] widths = {50, 160, 110, 75, 55, 105, 110};   // wider columns so the text is not cut off
        for (int i = 0; i < widths.length; i++) table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) fillFormFromSelection();
        });
        table.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) showHistory();
            }
        });
        searchField.addActionListener(e -> loadTable(searchField.getText().trim()));

        JPanel left = Theme.panel(new BorderLayout(0, 6));
        left.add(Theme.scroll(table), BorderLayout.CENTER);
        left.add(countLabel, BorderLayout.SOUTH);

        JPanel centre = Theme.panel(new BorderLayout(16, 0));
        centre.add(left, BorderLayout.CENTER);
        centre.add(buildForm(), BorderLayout.EAST);
        add(centre, BorderLayout.CENTER);
    }

    private JPanel buildForm() {
        RoundedPanel card = new RoundedPanel(new BorderLayout(0, 10));
        card.setBorder(new EmptyBorder(16, 16, 16, 16));
        card.setPreferredSize(new Dimension(300, 0));
        card.add(Theme.label("Customer Details", Theme.HEADING, Theme.TEXT), BorderLayout.NORTH);

        JPanel form = Theme.panel(new GridBagLayout());
        Theme.addRow(form, 0, "Name", nameField);
        Theme.addRow(form, 1, "Phone", phoneField);
        Theme.addRow(form, 2, "Gender", genderBox);
        Theme.addRow(form, 3, "", Theme.label("Gender decides the hourly price", Theme.SMALL, Theme.MUTED));
        JPanel formWrap = Theme.panel(new BorderLayout());
        formWrap.add(form, BorderLayout.NORTH);
        card.add(formWrap, BorderLayout.CENTER);

        JPanel buttons = Theme.panel(new GridLayout(2, 2, 10, 10));
        buttons.add(Theme.button("Add", Theme.GREEN, e -> addCustomer()));
        buttons.add(Theme.button("Update", Theme.BLUE, e -> updateCustomer()));
        buttons.add(Theme.button("History", Theme.AMBER, e -> showHistory()));
        buttons.add(Theme.button("Clear", Theme.NEUTRAL, e -> clearForm()));
        card.add(buttons, BorderLayout.SOUTH);
        return card;
    }

    @Override
    public void refresh() { loadTable(searchField.getText().trim()); }

    /** Asks the database for customers matching the text and fills the JTable. */
    private void loadTable(String text) {
        try {
            shown.clear();
            shown.addAll(customerDAO.search(text));
        } catch (SQLException e) {
            showDbError(e);
            return;
        }
        model.setRowCount(0);
        for (Customer c : shown) {
            model.addRow(new Object[]{c.getDisplayId(), c.getName(), c.getPhone(), c.getGenderLabel(), c.getTotalVisits(),
                    Theme.money0(c.getTotalSpent()), c.getRegisteredOn()});
        }
        countLabel.setText("Showing " + shown.size() + " customer(s)" + (text.isEmpty() ? "" : " for \"" + text + "\""));
        if (!text.isEmpty() && shown.isEmpty()) frame.setStatus("No customer found for \"" + text + "\"");
    }

    public void focusForm() {
        clearForm();
        nameField.requestFocusInWindow();
        frame.setStatus("Fill in the form on the right and press Add");
    }

    private void fillFormFromSelection() {
        Customer c = selectedCustomer(false);
        if (c == null) return;
        nameField.setText(c.getName());
        phoneField.setText(c.getPhone());
        genderBox.setSelectedItem(c.getGenderLabel());
    }

    private Customer selectedCustomer(boolean showMessage) {
        int row = table.getSelectedRow();
        if (row < 0 || row >= shown.size()) {
            if (showMessage) Theme.error(this, "Select a customer in the table first.");
            return null;
        }
        return shown.get(row);
    }

    private void clearForm() {
        table.clearSelection();
        nameField.setText("");
        phoneField.setText("");
        genderBox.setSelectedIndex(0);
    }

    /** Checks the form with the Validator class. Returns true if everything is fine. */
    private boolean validateForm() {
        try {
            Validator.requireName(nameField.getText());
            Validator.requirePhone(phoneField.getText());
            return true;
        } catch (InvalidInputException e) {
            Theme.error(this, e.getMessage());
            return false;
        }
    }

    private String selectedGender() {
        return genderBox.getSelectedItem().equals("Men") ? Customer.MEN : Customer.LADIES;
    }

    private void addCustomer() {
        if (!validateForm()) return;
        Customer c = new Customer(nameField.getText().trim(), phoneField.getText().trim(), selectedGender());
        try {
            customerDAO.add(c);
            searchField.setText("");
            loadTable("");
            clearForm();
            frame.setStatus("Added customer " + c.getName());
            Theme.info(this, "Customer " + c.getName() + " registered.", "Customer Added");
        } catch (SQLException e) {
            if (e.getErrorCode() == 1062) Theme.error(this, "This phone number is already registered.");
            else showDbError(e);
        }
    }

    private void updateCustomer() {
        Customer c = selectedCustomer(true);
        if (c == null || !validateForm()) return;
        if (c.isWalkIn()) {
            Theme.error(this, "The built-in walk-in guest cannot be edited.");
            return;
        }
        c.setName(nameField.getText().trim());
        c.setPhone(phoneField.getText().trim());
        c.setGender(selectedGender());
        try {
            customerDAO.update(c);
            loadTable(searchField.getText().trim());
            frame.setStatus("Updated " + c.getName());
            Theme.info(this, "Customer " + c.getName() + " updated.", "Customer Updated");
        } catch (SQLException e) {
            if (e.getErrorCode() == 1062) Theme.error(this, "This phone number belongs to another customer.");
            else showDbError(e);
        }
    }

    /** Owner only: deletes a customer who has no games, bookings or membership. */
    private void deleteCustomer() {
        Customer c = selectedCustomer(true);
        if (c == null) return;
        if (c.isWalkIn()) {
            Theme.error(this, "The built-in walk-in guest cannot be deleted.");
            return;
        }
        if (!Theme.confirm(this, "Delete customer " + c.getName() + "? This cannot be undone.", "Delete Customer")) return;
        try {
            if (customerDAO.deleteIfNoHistory(c.getId())) {
                loadTable(searchField.getText().trim());
                clearForm();
                frame.setStatus("Deleted customer " + c.getName());
            } else {
                Theme.info(this, c.getName() + " has games, bookings or a membership, so the record is kept\n"
                        + "(the bills must stay). Nothing was deleted.", "Delete Customer");
            }
        } catch (SQLException e) {
            showDbError(e);
        }
    }

    /** Shows the previous bills of the selected customer in a dialog. */
    private void showHistory() {
        Customer c = selectedCustomer(true);
        if (c == null) return;
        List<Bill> bills;
        try {
            bills = billDAO.findByCustomer(c.getId());
        } catch (SQLException e) {
            showDbError(e);
            return;
        }
        DefaultTableModel m = Theme.tableModel("Bill No", "Date", "Table", "Duration", "Amount", "Payment");
        for (Bill b : bills) {
            m.addRow(new Object[]{b.getBillNumber(), b.getCreatedAt().format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm")),
                    b.getTableName(), b.getDurationText(), Theme.money(b.getFinalAmount()), b.getPaymentLabel()});
        }
        JTable historyTable = new JTable(m);
        Theme.styleTable(historyTable);

        JDialog dialog = new JDialog(frame, "History - " + c.getName(), true);
        JPanel top = new JPanel(new GridLayout(2, 1));
        top.setBackground(Theme.PANEL);
        top.setBorder(new EmptyBorder(12, 16, 8, 16));
        top.add(Theme.label(c.getName() + "  (" + c.getPhone() + ")", Theme.HEADING, Theme.TEXT));
        top.add(Theme.label("Visits: " + c.getTotalVisits() + "   |   Total spent: " + Theme.money(c.getTotalSpent())
                + "   |   Since " + c.getRegisteredOn(), Theme.BODY, Theme.MUTED));
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottom.setBackground(Theme.PANEL);
        bottom.add(Theme.button("Close", Theme.NEUTRAL, e -> dialog.dispose()));
        dialog.add(top, BorderLayout.NORTH);
        dialog.add(Theme.scroll(historyTable), BorderLayout.CENTER);
        dialog.add(bottom, BorderLayout.SOUTH);
        dialog.setSize(720, 400);
        dialog.setLocationRelativeTo(frame);
        dialog.setVisible(true);
    }
}
