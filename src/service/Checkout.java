package service;

import exception.InvalidInputException;
import model.PaymentPart;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The money side of the checkout screen (no screen code, no SQL, so it is easy to test).
 *
 *   Subtotal      = table time (+ food and drinks later)
 *   Discount      = none, a flat amount in Rs., or a percentage of the subtotal
 *   Final total   = Subtotal - Discount   (never below Rs. 0)
 *   Payments      = one or more parts, e.g. Rs. 200 UPI + Rs. 300 cash
 *   Remaining     = Final total - payments; the bill can be closed only when it is Rs. 0
 */
public class Checkout {
    public enum DiscountType { NONE, FLAT, PERCENT }

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final BigDecimal subtotal;
    private DiscountType discountType = DiscountType.NONE;
    private BigDecimal discountValue = BigDecimal.ZERO;
    private String occasion = "";
    private final List<PaymentPart> parts = new ArrayList<>();

    public Checkout(BigDecimal subtotal) {
        this.subtotal = subtotal;
    }

    /** Sets the discount. Checks the rules: not negative, at most 100%, not more than the subtotal. */
    public void setDiscount(DiscountType type, BigDecimal value) throws InvalidInputException {
        BigDecimal v = (type == DiscountType.NONE || value == null) ? BigDecimal.ZERO : value;
        if (v.signum() < 0) throw new InvalidInputException("The discount cannot be negative.");
        if (type == DiscountType.PERCENT && v.compareTo(HUNDRED) > 0) {
            throw new InvalidInputException("A percentage discount cannot be more than 100%.");
        }
        if (type == DiscountType.FLAT && v.compareTo(subtotal) > 0) {
            throw new InvalidInputException("The discount (" + rupees(v) + ") cannot be more than the subtotal (" + rupees(subtotal) + ").");
        }
        BigDecimal newTotal = subtotal.subtract(discountOf(type, v)).max(BigDecimal.ZERO);
        if (getPaid().compareTo(newTotal) > 0) {
            throw new InvalidInputException("Payments of " + rupees(getPaid()) + " are already entered, more than the new total "
                    + rupees(newTotal) + ".\nRemove a payment first.");
        }
        discountType = type;
        discountValue = v;
    }

    /** Optional label such as "Christmas", so reports can total the discounts per occasion. */
    public void setOccasion(String text) throws InvalidInputException {
        String clean = text == null ? "" : text.trim();
        if (clean.length() > 40) throw new InvalidInputException("The occasion can have at most 40 characters.");
        occasion = clean;
    }

    /** Adds one part of the payment. It may not be more than what is still to be paid. */
    public void addPayment(String method, BigDecimal amount) throws InvalidInputException {
        if (!"CASH".equals(method) && !"UPI".equals(method)) throw new InvalidInputException("Payment method must be Cash or UPI.");
        if (amount == null || amount.signum() <= 0) throw new InvalidInputException("Enter an amount of more than Rs. 0.");
        if (amount.stripTrailingZeros().scale() > 2) throw new InvalidInputException("An amount can have at most 2 decimal places (paise).");
        if (amount.compareTo(getRemaining()) > 0) {
            throw new InvalidInputException(rupees(amount) + " is more than the remaining " + rupees(getRemaining()) + ".");
        }
        parts.add(new PaymentPart(method, amount));
    }

    public void removePayment(int index) {
        if (index >= 0 && index < parts.size()) parts.remove(index);
    }

    public BigDecimal getSubtotal() { return subtotal; }
    public DiscountType getDiscountType() { return discountType; }
    public BigDecimal getDiscountValue() { return discountValue; }
    /** The occasion is only kept when there really is a discount. */
    public String getOccasion() { return discountType == DiscountType.NONE ? "" : occasion; }
    public List<PaymentPart> getParts() { return Collections.unmodifiableList(parts); }

    public BigDecimal getDiscountAmount() { return discountOf(discountType, discountValue); }

    public BigDecimal getFinalTotal() { return subtotal.subtract(getDiscountAmount()).max(BigDecimal.ZERO); }

    public BigDecimal getPaid() {
        BigDecimal sum = BigDecimal.ZERO;
        for (PaymentPart p : parts) sum = sum.add(p.getAmount());
        return sum;
    }

    public BigDecimal getRemaining() { return getFinalTotal().subtract(getPaid()); }

    /** True when the payments are exactly the final total (then the bill can be closed). */
    public boolean isFullyPaid() { return getRemaining().signum() == 0; }

    /** CASH or UPI when only one method was used, SPLIT when both, null when nothing was paid. */
    public String getMethodLabel() {
        String label = null;
        for (PaymentPart p : parts) {
            if (label == null) label = p.getMethod();
            else if (!label.equals(p.getMethod())) return "SPLIT";
        }
        return label;
    }

    private BigDecimal discountOf(DiscountType type, BigDecimal value) {
        if (type == DiscountType.FLAT) return value.min(subtotal);
        if (type == DiscountType.PERCENT) return subtotal.multiply(value).divide(HUNDRED, 2, RoundingMode.HALF_UP);
        return BigDecimal.ZERO;
    }

    static String rupees(BigDecimal v) { return String.format("Rs. %,.2f", v); }
}
