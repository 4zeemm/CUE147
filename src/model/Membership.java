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
    private String planName = "";              // empty for memberships made before plans existed
    private BigDecimal pricePaid;              // null for old memberships

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

    public void setSaleDetails(String planName, BigDecimal pricePaid) {
        this.planName = planName == null ? "" : planName;
        this.pricePaid = pricePaid;
    }

    public String getPlanName() { return planName; }
    public BigDecimal getPricePaid() { return pricePaid; }

    /**
     * One line for the staff, for example "Monthly member until 22 Dec 2026 (5% off)",
     * "ENDS SOON: ...", or "EXPIRED on ...". Used on the Start Game search box and the customer record.
     */
    public String getStatusText() {
        java.time.format.DateTimeFormatter f = java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy");
        String plan = planName.isEmpty() ? "Member" : planName + " member";
        String off = discountPercent.stripTrailingZeros().toPlainString() + "% off";
        switch (getDisplayStatus()) {
            case "CANCELLED": return "No membership (last one was cancelled)";
            case "EXPIRED": return "Membership EXPIRED on " + expiryDate.format(f);
            default:
                if (getDaysLeft() <= 7) return plan + " - ENDS SOON: " + expiryDate.format(f) + " (" + getDaysLeft() + " days), " + off;
                return plan + " until " + expiryDate.format(f) + "  (" + off + ")";
        }
    }

    /** True when the staff should be warned: expired, or ending within 7 days. */
    public boolean needsWarning() {
        return getDisplayStatus().equals("EXPIRED") || (getDisplayStatus().equals("ACTIVE") && getDaysLeft() <= 7);
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
