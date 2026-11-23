package ui;

import dao.MembershipDAO;
import exception.BusinessException;
import model.Membership;
import service.MembershipService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Screen 7 - memberships. "Sell Membership" sells a plan (Monthly / Quarterly / Yearly) with payment;
 * a member gets the plan's discount on every game until the end date.
 */
public class MembershipPanel extends ScreenPanel {
    private final MembershipDAO membershipDAO = new MembershipDAO();
    private final MembershipService membershipService = new MembershipService();

    private final DefaultTableModel model = Theme.tableModel("Member", "Phone", "Plan", "Start", "End", "Paid", "Status");
    private final JTable table = new JTable(model);
    private final List<Membership> shown = new ArrayList<>();

    private final JProgressBar daysBar = new JProgressBar(0, 100);
    private final JLabel detailLabel = Theme.label("Select a member to see the days left", Theme.BODY, Theme.MUTED);

    public MembershipPanel(MainFrame frame) {
        super(frame);
        List<Component> top = new ArrayList<>();
        top.add(Theme.button("Sell Membership", Theme.GREEN, e -> sell()));
        if (frame.getUser().canCorrectFinancialRecords()) top.add(Theme.button("Cancel Membership", Theme.RED, e -> cancelMembership()));
        top.add(Theme.button("Refresh", Theme.NEUTRAL, e -> refresh()));
        add(header("Membership", "Sell Monthly, Quarterly or Yearly plans; members get a discount on every game",
                top.toArray(new Component[0])), BorderLayout.NORTH);

        Theme.styleTable(table);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getSelectionModel().addListSelectionListener(e -> { if (!e.getValueIsAdjusting()) showSelected(); });

        JPanel centre = Theme.panel(new BorderLayout(0, 10));
        centre.add(Theme.scroll(table), BorderLayout.CENTER);
        centre.add(buildProgress(), BorderLayout.SOUTH);
        add(centre, BorderLayout.CENTER);
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
            shown.clear();
            shown.addAll(membershipDAO.findAll());
        } catch (SQLException e) {
            showDbError(e);
            return;
        }
        model.setRowCount(0);
        for (Membership m : shown) {
            model.addRow(new Object[]{m.getCustomerName(), m.getPhone(), m.getPlanName().isEmpty() ? "(old)" : m.getPlanName(),
                    m.getStartDate(), m.getExpiryDate(), m.getPricePaid() == null ? "-" : Theme.money0(m.getPricePaid()),
                    m.needsWarning() && m.getDisplayStatus().equals("ACTIVE") ? "ENDS SOON" : m.getDisplayStatus()});
        }
        showSelected();
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
        detailLabel.setText(m.getCustomerName() + ": " + m.getStatusText());
        daysBar.setValue(total <= 0 || m.getDisplayStatus().equals("CANCELLED") ? 0 : (int) Math.min(100, left * 100 / total));
        daysBar.setString(left + " day(s) left");
    }

    private void sell() {
        if (MembershipSaleDialog.open(frame)) {
            refresh();
            frame.refreshNotifications();
            frame.setStatus("Membership sold");
        }
    }

    private void cancelMembership() {
        Membership m = selectedMembership();
        if (m == null) { Theme.error(this, "Select a member in the table first."); return; }
        if (!Theme.confirm(this, "Cancel the membership of " + m.getCustomerName() + "?\n"
                + "The discount stops now. Money already paid stays in the revenue (no refund).", "Cancel Membership")) return;
        try {
            membershipService.cancel(m, frame.getUser());
            refresh();
            frame.setStatus("Membership cancelled");
        } catch (BusinessException e) {
            Theme.error(this, e.getMessage());
        } catch (SQLException e) {
            showDbError(e);
        }
    }
}
