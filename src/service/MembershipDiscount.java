package service;

import java.math.BigDecimal;

/** For active members: the percentage stored in the member's membership. */
public class MembershipDiscount implements DiscountPolicy {
    private final BigDecimal percent;

    public MembershipDiscount(BigDecimal percent) {
        this.percent = percent;
    }

    @Override
    public BigDecimal getDiscountPercent() { return percent; }

    @Override
    public String getName() { return "Member discount " + percent + "%"; }
}
