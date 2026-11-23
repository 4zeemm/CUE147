package service;

import java.math.BigDecimal;

/**
 * Contract for "how much discount does this customer get?".
 * Different classes answer in different ways (see NoDiscount and MembershipDiscount),
 * and BillingCalculator works with any of them.
 */
public interface DiscountPolicy {
    /** Discount in percent, for example 10 means 10% off. */
    BigDecimal getDiscountPercent();

    /** A short name for messages and reports. */
    String getName();
}
