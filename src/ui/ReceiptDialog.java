package ui;

import dao.BillDAO;
import exception.BusinessException;
import model.Bill;
import model.Receiptable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.print.PrinterException;
import java.sql.SQLException;
import service.BillService;
import util.AppLogger;

/**
 * A window that shows a Receiptable (a bill or an expense voucher) and lets the user print it.
 * For an unpaid bill it also shows the payment buttons (Cash / UPI).
 */
public class ReceiptDialog extends JDialog {
    private final BillDAO billDAO = new BillDAO();
    private final BillService billService = new BillService();
    private final MainFrame frame;
    private final JTextArea area = new JTextArea();
    private final JPanel payPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
    private Receiptable document;   // what is shown (any class that implements Receiptable)
    private Bill bill;              // only set when the document is a bill
    private boolean changed;

    private ReceiptDialog(MainFrame frame, String title, Receiptable document, Bill bill) {
        super(frame, title, true);
        this.frame = frame;
        this.document = document;
        this.bill = bill;

        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        area.setBackground(new Color(0xFAF6E9));
        area.setForeground(new Color(0x222222));
        area.setBorder(new EmptyBorder(16, 20, 16, 20));

        payPanel.setBackground(Theme.PANEL);
        payPanel.add(Theme.button("Pay Cash", Theme.GREEN, e -> pay("CASH")));
        payPanel.add(Theme.button("Pay UPI", Theme.BLUE, e -> pay("UPI")));

        JPanel buttons = new JPanel(new BorderLayout());
        buttons.setBackground(Theme.PANEL);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        right.setBackground(Theme.PANEL);
        right.add(Theme.button("Print", Theme.NEUTRAL, e -> print()));
        right.add(Theme.button("Close", Theme.NEUTRAL, e -> dispose()));
        buttons.add(payPanel, BorderLayout.WEST);
        buttons.add(right, BorderLayout.EAST);

        add(new JScrollPane(area), BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
        showDocument();
        setSize(480, 560);
        setLocationRelativeTo(frame);
    }

    /** Shows a bill (with payment buttons if unpaid). Returns true if a payment was recorded. */
    public static boolean show(MainFrame frame, Bill bill) {
        ReceiptDialog dialog = new ReceiptDialog(frame, "Bill - " + bill.getBillNumber(), bill, bill);
        dialog.setVisible(true);
        return dialog.changed;
    }

    /** Shows any Receiptable (for example an expense voucher). Works for every class that implements the interface. */
    public static void showText(MainFrame frame, String title, Receiptable document) {
        new ReceiptDialog(frame, title, document, null).setVisible(true);
    }

    private void showDocument() {
        area.setText(document.toReceipt());
        area.setCaretPosition(0);
        payPanel.setVisible(bill != null && !bill.isPaid());
    }

    private void pay(String method) {
        try {
            billService.recordPayment(bill, method, frame.getUser());   // also remembers who took the money
            bill = billDAO.findById(bill.getId());
            document = bill;
            changed = true;
            showDocument();
            Theme.info(this, "Payment of " + Theme.money(bill.getFinalAmount()) + " received by " + method + ".", "Payment Saved");
        } catch (BusinessException e) {
            Theme.error(this, e.getMessage());
        } catch (SQLException e) {
            AppLogger.error("Could not save payment", e);
            Theme.error(this, "Could not save the payment:\n" + e.getMessage());
        }
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
