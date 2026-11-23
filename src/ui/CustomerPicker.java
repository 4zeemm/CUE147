package ui;

import dao.CustomerDAO;
import dao.MembershipDAO;
import exception.InvalidInputException;
import model.Customer;
import model.Membership;
import util.AppLogger;
import util.Validator;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * One search box for customers: type a name or a phone number and pick the person from the list.
 * When nobody matches, an "Add" button appears that needs only the name and the phone number.
 * The chosen customer's membership is shown at once, so nobody has to look it up.
 */
public class CustomerPicker extends JPanel {
    private final CustomerDAO customerDAO = new CustomerDAO();
    private final MembershipDAO membershipDAO = new MembershipDAO();

    private final JTextField search = Theme.field(22);
    private final DefaultListModel<Customer> results = new DefaultListModel<>();
    private final JList<Customer> list = new JList<>(results);
    private final RoundButton addButton = Theme.button("Add new customer", Theme.GREEN, e -> addCustomer());
    private final JLabel membership = Theme.label(" ", Theme.BOLD, Theme.MUTED);

    public CustomerPicker() {
        super(new BorderLayout(0, 8));
        setOpaque(false);
        setBorder(new EmptyBorder(4, 0, 4, 0));

        list.setVisibleRowCount(7);
        list.setFont(Theme.BODY);
        list.setBackground(Theme.FIELD);
        list.setForeground(Theme.TEXT);
        list.setSelectionBackground(Theme.ACCENT.darker());
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.addListSelectionListener(e -> { if (!e.getValueIsAdjusting()) showMembership(); });

        JPanel top = Theme.panel(new BorderLayout(0, 4));
        top.add(Theme.label("Customer name or phone number", Theme.BOLD, Theme.MUTED), BorderLayout.NORTH);
        top.add(search, BorderLayout.CENTER);

        JPanel bottom = Theme.panel(new BorderLayout(0, 6));
        bottom.add(membership, BorderLayout.NORTH);
        JPanel addRow = Theme.panel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        addRow.add(addButton);
        bottom.add(addRow, BorderLayout.SOUTH);

        add(top, BorderLayout.NORTH);
        JScrollPane scroll = Theme.scroll(list);
        scroll.setPreferredSize(new Dimension(380, 190));
        add(scroll, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);

        search.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { runSearch(); }
            public void removeUpdate(DocumentEvent e) { runSearch(); }
            public void changedUpdate(DocumentEvent e) { runSearch(); }
        });
        search.addActionListener(e -> { if (results.size() >= 1) list.setSelectedIndex(0); });   // Enter = take the first match
        // put the cursor in the search box as soon as the window opens
        addHierarchyListener(e -> { if (isShowing()) SwingUtilities.invokeLater(search::requestFocusInWindow); });
        runSearch();
    }

    /** The customer chosen in the list, or null. */
    public Customer getSelectedCustomer() { return list.getSelectedValue(); }

    public JTextField getSearchField() { return search; }

    private void runSearch() {
        String text = search.getText().trim();
        results.clear();
        try {
            List<Customer> found = customerDAO.search(text);
            for (Customer c : found) {
                if (text.isEmpty() && !c.isWalkIn()) continue;   // empty box: only the walk-in guests
                if (results.size() < 50) results.addElement(c);
            }
        } catch (SQLException e) {
            AppLogger.error("Customer search failed", e);
            membership.setText("Search failed: " + e.getMessage());
        }
        if (results.size() == 1 && !text.isEmpty()) list.setSelectedIndex(0);   // exactly one match: choose it
        addButton.setVisible(!text.isEmpty() && results.isEmpty());
        if (!text.isEmpty() && results.isEmpty()) membership.setText("No customer found. Press \"Add new customer\".");
        showMembership();
        revalidate();
    }

    /** Shows the membership of the chosen customer (end date, discount, or a warning). */
    private void showMembership() {
        Customer c = list.getSelectedValue();
        if (c == null) {
            if (results.size() > 0) { membership.setText("Choose the customer in the list."); membership.setForeground(Theme.MUTED); }
            return;
        }
        if (c.isWalkIn()) { membership.setText("Walk-in guest (no membership)"); membership.setForeground(Theme.MUTED); return; }
        try {
            Membership m = membershipDAO.findLatestForCustomer(c.getId());
            DateTimeFormatter f = DateTimeFormatter.ofPattern("dd MMM yyyy");
            if (m == null || m.getDisplayStatus().equals("CANCELLED")) {
                membership.setText("No membership");
                membership.setForeground(Theme.MUTED);
            } else if (m.getDisplayStatus().equals("EXPIRED")) {
                membership.setText("Membership EXPIRED on " + m.getExpiryDate().format(f));
                membership.setForeground(Theme.RED);
            } else if (m.getDaysLeft() <= 7) {
                membership.setText("Member, " + m.getDiscountPercent().stripTrailingZeros().toPlainString() + "% off - ENDS SOON: "
                        + m.getExpiryDate().format(f) + " (" + m.getDaysLeft() + " days)");
                membership.setForeground(Theme.AMBER);
            } else {
                membership.setText("Member until " + m.getExpiryDate().format(f) + "  (" + m.getDiscountPercent().stripTrailingZeros().toPlainString() + "% off)");
                membership.setForeground(Theme.GREEN);
            }
        } catch (SQLException e) {
            AppLogger.error("Could not load the membership", e);
            membership.setText("Membership could not be loaded.");
        }
    }

    /** Adds a new customer: only the name and phone are needed (Men is the default price group). */
    private void addCustomer() {
        String typed = search.getText().trim();
        JTextField name = Theme.field(18);
        JTextField phone = Theme.field(18);
        if (typed.matches("\\d+")) phone.setText(typed); else name.setText(typed);
        JRadioButton men = Theme.radio("Men"), ladies = Theme.radio("Ladies");
        ButtonGroup g = new ButtonGroup();
        g.add(men);
        g.add(ladies);
        men.setSelected(true);
        JPanel gender = Theme.panel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        gender.add(men);
        gender.add(ladies);
        Object[] form = {"Name:", name, "Phone (10 digits):", phone, "Price group:", gender};
        while (true) {
            int answer = JOptionPane.showConfirmDialog(this, form, "Add Customer", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (answer != JOptionPane.OK_OPTION) return;
            try {
                Customer c = new Customer(Validator.requireName(name.getText()), Validator.requirePhone(phone.getText()),
                        ladies.isSelected() ? Customer.LADIES : Customer.MEN);
                customerDAO.add(c);
                search.setText(c.getPhone());   // shows (and selects) the new customer
                return;
            } catch (InvalidInputException e) {
                Theme.error(this, e.getMessage());
            } catch (SQLException e) {
                if (e.getErrorCode() == 1062) Theme.error(this, "This phone number is already registered.");
                else Theme.error(this, "Could not save the customer:\n" + e.getMessage());
            }
        }
    }
}
