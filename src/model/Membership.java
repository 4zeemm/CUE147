package model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** A customer's membership. Members get a percentage discount on every bill until it expires. */
public class Membership {
    private int id;
    private int customerId;
    private String customerName;
    private String phone;
    private LocalDate startDate;
    private LocalDate expiryDate;
    private BigDecimal discountPercent;
    private String status;   // ACTIVE or CANCELLED as stored in the database

    public Membership(int id, int customerId, String customerName, String phone,
                      LocalDate startDate, LocalDate expiryDate, BigDecimal discountPercent, String status) {
        this.id = id;
        this.customerId = customerId;
        this.customerName = customerName;
        this.phone = phone;
        this.startDate = startDate;
        this.expiryDate = expiryDate;
        this.discountPercent = discountPercent;
        this.status = status;
    }

    public int getId() { return id; }
    public int getCustomerId() { return customerId; }
    public String getCustomerName() { return customerName; }
    public String getPhone() { return phone; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getExpiryDate() { return expiryDate; }
    public BigDecimal getDiscountPercent() { return discountPercent; }

    /** ACTIVE, EXPIRED or CANCELLED - worked out from the dates. */
    public String getDisplayStatus() {
        if (status.equals("CANCELLED")) return "CANCELLED";
        return expiryDate.isBefore(LocalDate.now()) ? "EXPIRED" : "ACTIVE";
    }

    public long getDaysLeft() {
        return Math.max(0, ChronoUnit.DAYS.between(LocalDate.now(), expiryDate));
    }
}
