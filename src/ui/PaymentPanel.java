package ui;

import exception.InvalidInputException;
import model.PaymentPart;
import service.Checkout;

import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Taking a payment in one or more parts: [Cash/UPI] [amount] [Add Payment] [Remove] and the list of parts.
 * The amount box always suggests what is still to be paid. Used by the checkout and by Sell Membership.
 */
public class PaymentPanel extends JPanel {
    private final Checkout checkout;
    private final Runnable onChange;
    private final JLabel message;
    private final JComboBox<String> method = Theme.combo("Cash", "UPI");
    private final JSpinner amount = Theme.numberSpinner(0, 0, 1_000_000, 10);
    private final DefaultListModel<String> partsModel = new DefaultListModel<>();
    private final JList<String> parts = new JList<>(partsModel);

    /** message = the red label where mistakes are shown; onChange = runs after every change (to update totals). */
    public PaymentPanel(Checkout checkout, JLabel message, Runnable onChange) {
        super(new BorderLayout(0, 6));
        setOpaque(false);
        this.checkout = checkout;
        this.message = message;
        this.onChange = onChange;
        method.setName("payMethod");
        amount.setName("payAmount");

        JPanel row = Theme.panel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        amount.setPreferredSize(new Dimension(120, 34));
        row.add(method);
        row.add(amount);
        row.add(Theme.button("Add Payment", Theme.BLUE, e -> addPayment()));
        row.add(Theme.button("Remove", Theme.NEUTRAL, e -> removePayment()));

        parts.setVisibleRowCount(3);
        parts.setBackground(Theme.FIELD);
        parts.setForeground(Theme.TEXT);
        parts.setFont(Theme.BODY);

        add(Theme.label("Payments (for a split bill add each part)", Theme.BOLD, Theme.MUTED), BorderLayout.NORTH);
        add(row, BorderLayout.CENTER);
        add(Theme.scroll(parts), BorderLayout.SOUTH);
        refresh();
    }

    private void addPayment() {
        String m = method.getSelectedIndex() == 0 ? "CASH" : "UPI";
        BigDecimal a = BigDecimal.valueOf(((Number) amount.getValue()).doubleValue()).setScale(2, RoundingMode.HALF_UP);
        try {
            checkout.addPayment(m, a);
            message.setText(" ");
        } catch (InvalidInputException e) {
            message.setText(e.getMessage().split("\n")[0]);
        }
        onChange.run();
    }

    private void removePayment() {
        int i = parts.getSelectedIndex();
        if (i < 0) i = partsModel.size() - 1;   // nothing selected: remove the last one
        checkout.removePayment(i);
        onChange.run();
    }

    /** Shows the parts and suggests the remaining amount. Call after anything changed. */
    public void refresh() {
        partsModel.clear();
        for (PaymentPart p : checkout.getParts()) {
            partsModel.addElement((p.getMethod().equals("CASH") ? "Cash" : "UPI") + "   " + Theme.money(p.getAmount()));
        }
        amount.setValue(checkout.getRemaining().doubleValue());
    }

    /** Pay Cash / Pay UPI buttons: the rest is paid with this method. Returns false (with a message) if that is not possible. */
    public boolean payRest(String m) {
        if (checkout.getRemaining().signum() <= 0) return true;
        try {
            checkout.addPayment(m, checkout.getRemaining());
            return true;
        } catch (InvalidInputException e) {
            message.setText(e.getMessage().split("\n")[0]);
            return false;
        }
    }
}
