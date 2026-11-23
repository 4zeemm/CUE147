package ui;

import model.Receiptable;
import util.AppLogger;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.print.PrinterException;

/**
 * A window that shows a Receiptable (a bill or an expense voucher) and lets the user print it.
 * Payments are taken in the CheckoutDialog, not here.
 */
public class ReceiptDialog extends JDialog {
    private final JTextArea area = new JTextArea();

    private ReceiptDialog(MainFrame frame, String title, Receiptable document) {
        super(frame, title, true);
        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        area.setBackground(new Color(0xFAF6E9));
        area.setForeground(new Color(0x222222));
        area.setBorder(new EmptyBorder(16, 20, 16, 20));
        area.setText(document.toReceipt());
        area.setCaretPosition(0);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        buttons.setBackground(Theme.PANEL);
        buttons.add(Theme.button("Print", Theme.NEUTRAL, e -> print()));
        buttons.add(Theme.button("Close", Theme.NEUTRAL, e -> dispose()));

        add(new JScrollPane(area), BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
        setSize(480, 600);
        setLocationRelativeTo(frame);
    }

    /** Shows any Receiptable (a bill or an expense voucher). Works for every class that implements the interface. */
    public static void showText(MainFrame frame, String title, Receiptable document) {
        new ReceiptDialog(frame, title, document).setVisible(true);
    }

    private void print() {
        try {
            area.print();   // opens the normal print window of the computer
        } catch (PrinterException e) {
            AppLogger.error("Printing failed", e);
            Theme.error(this, "Could not print: " + e.getMessage());
        }
    }
}
