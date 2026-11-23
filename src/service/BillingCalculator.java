package service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * All billing rules in one small class:
 *   1. Minimum billing is one hour.
 *   2. The first 5 minutes after each completed hour are free.
 *   3. After that, every extra minute is charged proportionally (rate / 60).
 *   4. The final bill is rounded DOWN to the nearest Rs. 5.
 * BigDecimal is used for money because double can give errors like 0.1 + 0.2 = 0.30000000000000004.
 */
public class BillingCalculator {
    public static final int GRACE_MINUTES = 5;
    private static final BigDecimal SIXTY = BigDecimal.valueOf(60);
    private static final BigDecimal FIVE = BigDecimal.valueOf(5);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /** Step 1-3: the amount before discount and rounding. */
    public BigDecimal calculateSubtotal(long totalMinutes, BigDecimal hourlyRate) {
        // Rule 1: less than one hour is billed as one full hour.
        if (totalMinutes < 60) {
            return hourlyRate;
        }
        long completedHours = totalMinutes / 60;        // e.g. 66 minutes -> 1 hour
        long extraMinutes = totalMinutes % 60;          // e.g. 66 minutes -> 6 extra minutes

        BigDecimal amount = hourlyRate.multiply(BigDecimal.valueOf(completedHours));

        // Rule 2 and 3: the first 5 extra minutes are free, the rest are charged per minute.
        if (extraMinutes > GRACE_MINUTES) {
            long chargeableMinutes = extraMinutes - GRACE_MINUTES;
            BigDecimal extraCharge = hourlyRate.multiply(BigDecimal.valueOf(chargeableMinutes))
                    .divide(SIXTY, 2, RoundingMode.DOWN);
            amount = amount.add(extraCharge);
        }
        return amount;
    }

    /** Rule 4: 274.50 -> 270.00, 305.00 -> 305.00, 309.99 -> 305.00 */
    public BigDecimal roundDownToFive(BigDecimal amount) {
        return amount.divide(FIVE, 0, RoundingMode.DOWN).multiply(FIVE).setScale(2);
    }

    /** Discount amount for a percentage, e.g. 10% of 300 = 30. */
    public BigDecimal calculateDiscount(BigDecimal subtotal, BigDecimal discountPercent) {
        return subtotal.multiply(discountPercent).divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }

    /** The complete calculation: subtotal, minus member discount, rounded down to Rs. 5. */
    public BigDecimal calculateFinalAmount(long totalMinutes, BigDecimal hourlyRate, BigDecimal discountPercent) {
        BigDecimal subtotal = calculateSubtotal(totalMinutes, hourlyRate);
        BigDecimal afterDiscount = subtotal.subtract(calculateDiscount(subtotal, discountPercent));
        return roundDownToFive(afterDiscount);
    }

    /** Same calculation, but the discount comes from a DiscountPolicy object (interface in action). */
    public BigDecimal calculateFinalAmount(long totalMinutes, BigDecimal hourlyRate, DiscountPolicy policy) {
        return calculateFinalAmount(totalMinutes, hourlyRate, policy.getDiscountPercent());
    }
}
