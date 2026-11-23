package service;

import java.math.BigDecimal;

/** For customers who are not members: no discount. */
public class NoDiscount implements DiscountPolicy {
    @Override
    public BigDecimal getDiscountPercent() { return BigDecimal.ZERO; }

    @Override
    public String getName() { return "No discount"; }
}
