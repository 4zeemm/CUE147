package ui;

import dao.CustomerDAO;
import dao.MembershipDAO;
import model.Customer;
import model.Membership;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Screen 7 - simple memberships: a member gets a discount on every bill until the membership expires. */
public class MembershipPanel extends ScreenPanel {
    // plan name, months, discount percent
    private static final Object[][] PLANS = {{"Monthly", 1, 5}, {"Quarterly", 3, 10}, {"Yearly", 12, 15}};

    private final MembershipDAO membershipDAO = new MembershipDAO();
    private final CustomerDAO customerDAO = new CustomerDAO();

    private final DefaultTableModel model = Theme.tableModel("Member", "Phone", "Start", "Expiry", "Discount", "Status");
    private final JTable table = new JTable(model);
    private final List<Membership> shown = new ArrayList<>();

    private final JComboBox<Customer> customerBox = new JComboBox<>();
    private final JRadioButton[] planRadios = new JRadioButton[PLANS.length];
    private final JProgressBar daysBar = new JProgressBar(0, 100);
    private final JLabel detailLabel = Theme.label("Select a member to see the days left", Theme.BODY, Theme.MUTED);

    public MembershipPanel(MainFrame frame) {
        super(frame);
        add(header("Membership", "Members get a discount on every bill until they expire"), BorderLayout.NORTH);
        customerBox.setFont(Theme.BODY);
        customerBox.setBackground(Theme.FIELD);
        customerBox.setForeground(Theme.TEXT);

        Theme.styleTable(table);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getSelectionModel().addListSelectionListener(e -> { if (!e.getValueIsAdjusting()) showSelected(); });

        JPanel centre = Theme.panel(new BorderLayout(16, 0));
        centre.add(buildForm(), BorderLayout.WEST);
        JPanel right = Theme.panel(new BorderLayout(0, 10));
        right.add(Theme.scroll(table), BorderLayout.CENTER);
        right.add(buildProgress(), BorderLayout.SOUTH);
        centre.add(right, BorderLayout.CENTER);
        add(centre, BorderLayout.CENTER);
    }

    private JPanel buildForm() {
        RoundedPanel card = new RoundedPanel(new BorderLayout(0, 12));
        card.setBorder(new EmptyBorder(16, 16, 16, 16));
        card.setPreferredSize(new Dimension(360, 0));
        card.add(Theme.label("Manage Membership", Theme.HEADING, Theme.TEXT), BorderLayout.NORTH);

        ButtonGroup group = new ButtonGroup();
        JPanel radios = Theme.panel(new GridLayout(PLANS.length, 1));
        for (int i = 0; i < PLANS.length; i++) {
            planRadios[i] = Theme.radio(PLANS[i][0] + " - " + PLANS[i][1] + " month(s), " + PLANS[i][2] + "% off");
            group.add(planRadios[i]);
            radios.add(planRadios[i]);
        }
        planRadios[0].setSelected(true);

        JPanel form = Theme.panel(new GridBagLayout());
        Theme.addRow(form, 0, "Customer", customerBox);
        Theme.addRow(form, 1, "Plan", radios);
        JPanel wrap = Theme.panel(new BorderLayout());
        wrap.add(form, BorderLayout.NORTH);
        card.add(wrap, BorderLayout.CENTER);

        JPanel buttons = Theme.panel(new GridLayout(1, 3, 8, 0));
        buttons.add(Theme.button("Add Member", Theme.GREEN, e -> addMember()));
        buttons.add(Theme.button("Renew", Theme.BLUE, e -> renew()));
        buttons.add(Theme.button("Cancel", Theme.RED, e -> cancelMembership()));
        for (Component c : buttons.getComponents()) ((JComponent) c).setBorder(new EmptyBorder(8, 4, 8, 4));
        card.add(buttons, BorderLayout.SOUTH);
        return card;
    }

    private JPanel buildProgress() {
        RoundedPanel card = new RoundedPanel(new BorderLayout(0, 6));
        card.setBorder(new EmptyBorder(12, 16, 12, 16));
        daysBar.setStringPainted(true);
        daysBar.setPreferredSize(new Dimension(100, 22));
        card.add(detailLabel, BorderLayout.NORTH);
        card.add(daysBar, BorderLayout.CENTER);
        return card;
    }

    @Override
    public void refresh() {
        try {
            int old = customerBox.getSelectedItem() == null ? -1 : ((Customer) customerBox.getSelectedItem()).getId();
            customerBox.removeAllItems();
            for (Customer c : customerDAO.findAll()) {
                if (c.isWalkIn()) continue;   // walk-in guests cannot be members
                customerBox.addItem(c);
                if (c.getId() == old) customerBox.setSelectedItem(c);
            }
            shown.clear();
            shown.addAll(membershipDAO.findAll());
        } catch (SQLException e) {
            showDbError(e);
            return;
        }
        model.setRowCount(0);
        for (Membership m : shown) {
            model.addRow(new Object[]{m.getCustomerName(), m.getPhone(), m.getStartDate(), m.getExpiryDate(),
                    m.getDiscountPercent() + "%", m.getDisplayStatus().equals("ACTIVE") ? "ACTIVE" : m.getDisplayStatus()});
        }
        showSelected();
    }

    private int selectedPlan() {
        for (int i = 0; i < planRadios.length; i++) if (planRadios[i].isSelected()) return i;
        return 0;
    }

    private Membership selectedMembership() {
        int row = table.getSelectedRow();
        return row >= 0 && row < shown.size() ? shown.get(row) : null;
    }

    private void showSelected() {
        Membership m = selectedMembership();
        if (m == null) {
            detailLabel.setText("Select a member to see the days left");
            daysBar.setValue(0);
            daysBar.setString("");
            return;
        }
        long total = java.time.temporal.ChronoUnit.DAYS.between(m.getStartDate(), m.getExpiryDate());
        long left = m.getDaysLeft();
        detailLabel.setText(m.getCustomerName() + " - " + m.getDisplayStatus() + ", " + m.getDiscountPercent() + "% member pricing");
        daysBar.setValue(total <= 0 || m.getDisplayStatus().equals("CANCELLED") ? 0 : (int) Math.min(100, left * 100 / total));
        daysBar.setString(left + " day(s) left");
    }

    private void addMember() {
        Customer c = (Customer) customerBox.getSelectedItem();
        if (c == null) { Theme.error(this, "Choose a customer first."); return; }
        try {
            Membership current = membershipDAO.findLatestForCustomer(c.getId());
            if (current != null && current.getDisplayStatus().equals("ACTIVE")) {
                Theme.error(this, c.getName() + " is already an active member. Use Renew to extend it.");
                return;
            }
            int plan = selectedPlan();
            LocalDate start = LocalDate.now();
            membershipDAO.add(c.getId(), start, start.plusMonths((Integer) PLANS[plan][1]), BigDecimal.valueOf((Integer) PLANS[plan][2]));
            refresh();
            frame.setStatus(c.getName() + " is now a member");
            Theme.info(this, c.getName() + " is now a " + PLANS[plan][0] + " member (" + PLANS[plan][2] + "% off).", "Member Added");
        } catch (SQLException e) {
            showDbError(e);
        }
    }

    private void renew() {
        Membership m = selectedMembership();
        if (m == null) { Theme.error(this, "Select a member in the table first."); return; }
        int plan = selectedPlan();
        // extend from the old expiry date if it is still in the future, otherwise from today
        LocalDate from = m.getExpiryDate().isAfter(LocalDate.now()) ? m.getExpiryDate() : LocalDate.now();
        LocalDate newExpiry = from.plusMonths((Integer) PLANS[plan][1]);
        try {
            membershipDAO.renew(m.getId(), newExpiry, BigDecimal.valueOf((Integer) PLANS[plan][2]));
            refresh();
            frame.setStatus("Membership renewed until " + newExpiry);
            Theme.info(this, m.getCustomerName() + "'s membership now runs until " + newExpiry + ".", "Renewed");
        } catch (SQLException e) {
            showDbError(e);
        }
    }

    private void cancelMembership() {
        Membership m = selectedMembership();
        if (m == null) { Theme.error(this, "Select a member in the table first."); return; }
        if (Theme.confirm(this, "Cancel the membership of " + m.getCustomerName() + "?", "Cancel Membership")) {
            try {
                membershipDAO.cancel(m.getId());
                refresh();
                frame.setStatus("Membership cancelled");
            } catch (SQLException e) {
                showDbError(e);
            }
        }
    }
}
