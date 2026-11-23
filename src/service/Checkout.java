package service;

import exception.InvalidInputException;
import model.OrderItem;
import model.PaymentPart;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The money side of the checkout screen (no screen code, no SQL, so it is easy to test).
 *
 *   Subtotal      = table time + food and drinks
 *   Discount      = none, a flat amount in Rs., or a percentage of the subtotal
 *   Final total   = Subtotal - Discount   (never below Rs. 0)
 *   Payments      = one or more parts, e.g. Rs. 200 UPI + Rs. 300 cash
 *   Remaining     = Final total - payments; the bill can be closed only when it is Rs. 0
 */
public class Checkout {
    public enum DiscountType { NONE, FLAT, PERCENT }

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final BigDecimal tableAmount;
    private final List<OrderItem> items = new ArrayList<>();
    private DiscountType discountType = DiscountType.NONE;
    private BigDecimal discountValue = BigDecimal.ZERO;
    private String occasion = "";
    private final List<PaymentPart> parts = new ArrayList<>();

    /** tableAmount = table time (or the price of what is sold, e.g. a membership). */
    public Checkout(BigDecimal tableAmount) {
        this.tableAmount = tableAmount;
    }

    /** Adds food or drinks. The same menu item twice becomes one line with a bigger quantity. */
    public void addItem(OrderItem item) throws InvalidInputException {
        if (item.getQuantity() < 1 || item.getQuantity() > 99) throw new InvalidInputException("Quantity must be between 1 and 99.");
        for (int i = 0; i < items.size(); i++) {
            OrderItem old = items.get(i);
            if (old.getMenuItemId() == item.getMenuItemId() && old.getUnitPrice().compareTo(item.getUnitPrice()) == 0) {
                int total = old.getQuantity() + item.getQuantity();
                if (total > 99) throw new InvalidInputException("Quantity must be between 1 and 99.");
                items.set(i, old.withQuantity(total));
                return;
            }
        }
        items.add(item);
    }

    /** Removes a food/drink line - not allowed if the payments already entered would be more than the new total. */
    public void removeItem(int index) throws InvalidInputException {
        if (index < 0 || index >= items.size()) return;
        OrderItem removed = items.remove(index);
        BigDecimal newSubtotal = getSubtotal();
        boolean flatTooBig = discountType == DiscountType.FLAT && discountValue.compareTo(newSubtotal) > 0;
        if (flatTooBig || getPaid().compareTo(getFinalTotal()) > 0) {
            items.add(index, removed);   // undo
            throw new InvalidInputException(flatTooBig ? "The discount would be more than the new subtotal. Change the discount first."
                    : "The payments already entered are more than the new total. Remove a payment first.");
        }
    }

    /** Sets the discount. Checks the rules: not negative, at most 100%, not more than the subtotal. */
    public void setDiscount(DiscountType type, BigDecimal value) throws InvalidInputException {
        BigDecimal v = (type == DiscountType.NONE || value == null) ? BigDecimal.ZERO : value;
        if (v.signum() < 0) throw new InvalidInputException("The discount cannot be negative.");
        if (type == DiscountType.PERCENT && v.compareTo(HUNDRED) > 0) {
            throw new InvalidInputException("A percentage discount cannot be more than 100%.");
        }
        BigDecimal subtotal = getSubtotal();
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

    public BigDecimal getTableAmount() { return tableAmount; }
    public List<OrderItem> getItems() { return Collections.unmodifiableList(items); }

    public BigDecimal getItemsAmount() {
        BigDecimal sum = BigDecimal.ZERO;
        for (OrderItem i : items) sum = sum.add(i.getAmount());
        return sum;
    }

    public BigDecimal getSubtotal() { return tableAmount.add(getItemsAmount()); }
    public DiscountType getDiscountType() { return discountType; }
    public BigDecimal getDiscountValue() { return discountValue; }
    /** The occasion is only kept when there really is a discount. */
    public String getOccasion() { return discountType == DiscountType.NONE ? "" : occasion; }
    public List<PaymentPart> getParts() { return Collections.unmodifiableList(parts); }

    public BigDecimal getDiscountAmount() { return discountOf(discountType, discountValue); }

    public BigDecimal getFinalTotal() { return getSubtotal().subtract(getDiscountAmount()).max(BigDecimal.ZERO); }

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
        BigDecimal subtotal = getSubtotal();
        if (type == DiscountType.FLAT) return value.min(subtotal);
        if (type == DiscountType.PERCENT) return subtotal.multiply(value).divide(HUNDRED, 2, RoundingMode.HALF_UP);
        return BigDecimal.ZERO;
    }

    static String rupees(BigDecimal v) { return String.format("Rs. %,.2f", v); }
}
