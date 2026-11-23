package ui;

import dao.MembershipPlanDAO;
import dao.StaffMemberDAO;
import exception.BusinessException;
import model.Customer;
import model.Membership;
import model.MembershipPlan;
import model.StaffMember;
import service.Checkout;
import service.MembershipService;
import util.AppLogger;
import util.AppSettings;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Sell Membership: choose the customer, choose the plan, take the payment (it may be split).
 * The start and end dates are worked out automatically.
 */
public class MembershipSaleDialog extends JDialog {
    private final MainFrame frame;
    private final MembershipService service = new MembershipService();
    private final CustomerPicker picker = new CustomerPicker();
    private final JComboBox<MembershipPlan> planBox = new JComboBox<>();
    private final JComboBox<StaffMember> billedBy = new JComboBox<>();
    private final JLabel datesLabel = Theme.label(" ", Theme.BOLD, Theme.TEXT);
    private final JLabel totalLabel = Theme.label("", Theme.TITLE, Theme.GOLD);
    private final JLabel remainingLabel = Theme.label("", Theme.HEADING, Theme.RED);
    private final JLabel messageLabel = Theme.label(" ", Theme.BOLD, Theme.RED);
    private final JPanel paymentHolder = Theme.panel(new BorderLayout());
    private final RoundButton completeButton = Theme.button("Complete Sale", Theme.GREEN, e -> complete());
    private Checkout payment = new Checkout(java.math.BigDecimal.ZERO);
    private PaymentPanel paymentPanel;
    private boolean sold = false;

    private MembershipSaleDialog(MainFrame frame) {
        super(frame, "Sell Membership", true);
        this.frame = frame;
        planBox.setName("plan");
        billedBy.setName("billedBy");
        planBox.setFont(Theme.BODY);
        try {
            for (MembershipPlan p : new MembershipPlanDAO().findAll()) planBox.addItem(p);
            StaffMember last = null;
            for (StaffMember s : new StaffMemberDAO().findActive()) {
                billedBy.addItem(s);
                if (s.getId() == AppSettings.getLastBilledById()) last = s;
            }
            if (last != null) billedBy.setSelectedItem(last);            // the last person at this counter
            else if (billedBy.getItemCount() != 1) billedBy.setSelectedIndex(-1);   // make the person choose
        } catch (SQLException e) {
            AppLogger.error("Could not load plans or staff", e);
        }

        JPanel left = Theme.panel(new BorderLayout(0, 10));
        left.add(picker, BorderLayout.CENTER);
        JPanel planForm = Theme.panel(new GridBagLayout());
        Theme.addRow(planForm, 0, "Plan", planBox);
        Theme.addRow(planForm, 1, "Dates", datesLabel);
        Theme.addRow(planForm, 2, "Billed by", billedBy);
        left.add(planForm, BorderLayout.SOUTH);

        JPanel totals = Theme.panel(new GridBagLayout());
        Theme.addRow(totals, 0, "To pay", totalLabel);
        Theme.addRow(totals, 1, "Remaining", remainingLabel);
        RoundedPanel right = new RoundedPanel(new BorderLayout(0, 10));
        right.setBorder(new EmptyBorder(14, 14, 14, 14));
        right.add(totals, BorderLayout.NORTH);
        right.add(paymentHolder, BorderLayout.CENTER);
        right.add(messageLabel, BorderLayout.SOUTH);

        JPanel middle = Theme.panel(new GridLayout(1, 2, 16, 0));
        middle.add(left);
        middle.add(right);

        JPanel buttons = Theme.panel(new BorderLayout());
        JPanel quick = Theme.panel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        quick.add(Theme.button("Pay Cash", Theme.GREEN, e -> payRest("CASH")));
        quick.add(Theme.button("Pay UPI", Theme.BLUE, e -> payRest("UPI")));
        JPanel end = Theme.panel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        end.add(completeButton);
        end.add(Theme.button("Close", Theme.NEUTRAL, e -> dispose()));
        buttons.add(quick, BorderLayout.WEST);
        buttons.add(end, BorderLayout.EAST);

        JPanel root = Theme.panel(new BorderLayout(0, 12));
        root.setBorder(new EmptyBorder(16, 18, 14, 18));
        root.add(Theme.label("Sell Membership", Theme.TITLE, Theme.TEXT), BorderLayout.NORTH);
        root.add(middle, BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);
        setContentPane(root);

        planBox.addActionListener(e -> newPayment());
        picker.onSelect(this::showDates);
        newPayment();
        setSize(900, 600);
        setLocationRelativeTo(frame);
    }

    /** Opens the window. Returns true when a membership was sold. */
    public static boolean open(MainFrame frame) {
        MembershipSaleDialog d = new MembershipSaleDialog(frame);
        d.setVisible(true);
        return d.sold;
    }

    /** A new (empty) payment for the price of the chosen plan. */
    private void newPayment() {
        MembershipPlan plan = (MembershipPlan) planBox.getSelectedItem();
        payment = new Checkout(plan == null ? java.math.BigDecimal.ZERO : plan.getPrice());
        paymentPanel = new PaymentPanel(payment, messageLabel, this::update);
        paymentHolder.removeAll();
        paymentHolder.add(paymentPanel);
        messageLabel.setText(plan != null && plan.getPrice().signum() <= 0
                ? "The price of this plan is not set yet (Settings > Membership Plans)." : " ");
        update();
        paymentHolder.revalidate();
    }

    private void update() {
        totalLabel.setText(Theme.money(payment.getFinalTotal()));
        remainingLabel.setText(Theme.money(payment.getRemaining()));
        remainingLabel.setForeground(payment.getRemaining().signum() == 0 ? Theme.GREEN : Theme.RED);
        paymentPanel.refresh();
        completeButton.setEnabled(payment.isFullyPaid() && payment.getFinalTotal().signum() > 0);
        showDates();
    }

    /** "Starts 23 Nov 2026, ends 22 Dec 2026" - starts after the current membership if the customer is still a member. */
    private void showDates() {
        MembershipPlan plan = (MembershipPlan) planBox.getSelectedItem();
        if (plan == null) return;
        try {
            LocalDate start = service.startDateFor(picker.getSelectedCustomer());
            DateTimeFormatter f = DateTimeFormatter.ofPattern("dd MMM yyyy");
            datesLabel.setText(start.format(f) + "  to  " + plan.endDateFor(start).format(f));
        } catch (SQLException e) {
            AppLogger.error("Could not work out the dates", e);
        }
    }

    private void payRest(String method) {
        if (!paymentPanel.payRest(method)) return;
        update();
        complete();
    }

    private void complete() {
        Customer c = picker.getSelectedCustomer();
        StaffMember staff = (StaffMember) billedBy.getSelectedItem();
        try {
            Membership m = service.sell(c, (MembershipPlan) planBox.getSelectedItem(), payment, staff, frame.getUser());
            AppSettings.setLastBilledById(staff.getId());
            sold = true;
            Theme.info(this, c.getName() + ": " + m.getStatusText() + "\nPaid " + Theme.money(payment.getFinalTotal())
                    + " by " + payment.getMethodLabel() + ".", "Membership Sold");
            dispose();
        } catch (BusinessException e) {
            Theme.error(this, e.getMessage());
        } catch (SQLException e) {
            AppLogger.error("Could not sell the membership", e);
            Theme.error(this, "Could not save the membership:\n" + e.getMessage());
        }
    }
}
