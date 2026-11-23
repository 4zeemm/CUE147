package model;

/**
 * Contract for anything that can be shown or printed as a small paper slip.
 * Both a Bill and an Expense voucher implement it, so one window can show either of them.
 */
public interface Receiptable {
    /** The text of the slip, ready to display or print. */
    String toReceipt();
}
