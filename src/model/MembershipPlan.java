package model;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Monthly, Quarterly or Yearly. The owner sets the price, the months and the member discount in Settings. */
public class MembershipPlan {
    private final int id;
    private final String name;
    private final int months;
    private final BigDecimal price;
    private final BigDecimal discountPercent;

    public MembershipPlan(int id, String name, int months, BigDecimal price, BigDecimal discountPercent) {
        this.id = id;
        this.name = name;
        this.months = months;
        this.price = price;
        this.discountPercent = discountPercent;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public int getMonths() { return months; }
    public BigDecimal getPrice() { return price; }
    public BigDecimal getDiscountPercent() { return discountPercent; }

    /** Last day of a membership that starts on the given day: 1 month from 23 Nov = until 22 Dec. */
    public LocalDate endDateFor(LocalDate start) { return start.plusMonths(months).minusDays(1); }

    @Override
    public String toString() {
        return name + "  -  " + months + (months == 1 ? " month" : " months") + ", "
                + String.format("₹%,.0f", price) + ", " + discountPercent.stripTrailingZeros().toPlainString() + "% off games";
    }
}
