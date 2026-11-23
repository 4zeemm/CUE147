package model;

import java.math.BigDecimal;

/** One part of a payment, for example Rs. 200 by UPI. A split payment has several parts. */
public class PaymentPart {
    private final String method;      // CASH or UPI
    private final BigDecimal amount;

    public PaymentPart(String method, BigDecimal amount) {
        this.method = method;
        this.amount = amount;
    }

    public String getMethod() { return method; }
    public BigDecimal getAmount() { return amount; }
}
