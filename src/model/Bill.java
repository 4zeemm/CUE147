package model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** A bill for one finished session, together with its payment details. */
public class Bill implements Receiptable {
    private int id;
    private String billNumber;
    private int sessionId;
    private int billedMinutes;
    private BigDecimal hourlyRate;
    private BigDecimal subtotal;
    private BigDecimal discountPercent;
    private BigDecimal finalAmount;
    private LocalDateTime createdAt;

    // extra details joined from other tables (for display)
    private String customerName = "";
    private String tableName = "";
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String billStatus = "ACTIVE";      // ACTIVE or CANCELLED
    private String cancelReason = "";
    private String servedBy = "-";              // staff member who ended the game
    private String receivedBy = "-";            // staff member who took the payment
    private String paymentStatus = "PENDING";   // PENDING, PAID, CANCELLED or REFUNDED
    private String paymentMethod = "-";
    private LocalDateTime paidAt;

    // checkout details (database version 2)
    private BigDecimal tableAmount;                     // table time after member discount, rounded
    private String billDiscountType = "NONE";           // NONE, FLAT or PERCENT
    private BigDecimal billDiscountValue = BigDecimal.ZERO;
    private BigDecimal billDiscountAmount = BigDecimal.ZERO;
    private String discountOccasion = "";
    private Integer billedByStaffId;                    // null for bills made before the "Billed by" list
    private String billedBy = "-";
    private List<PaymentPart> paymentParts = new ArrayList<>();
    private BigDecimal itemsAmount = BigDecimal.ZERO;   // food and drinks (database version 3)
    private List<OrderItem> items = new ArrayList<>();

    public Bill(int id, String billNumber, int sessionId, int billedMinutes, BigDecimal hourlyRate,
                BigDecimal subtotal, BigDecimal discountPercent, BigDecimal finalAmount, LocalDateTime createdAt) {
        this.id = id;
        this.billNumber = billNumber;
        this.sessionId = sessionId;
        this.billedMinutes = billedMinutes;
        this.hourlyRate = hourlyRate;
        this.subtotal = subtotal;
        this.discountPercent = discountPercent;
        this.finalAmount = finalAmount;
        this.createdAt = createdAt;
        this.tableAmount = finalAmount;
    }

    public void setCheckoutDetails(BigDecimal tableAmount, String discountType, BigDecimal discountValue, BigDecimal discountAmount,
                                   String occasion, Integer billedByStaffId, String billedBy) {
        this.tableAmount = tableAmount;
        this.billDiscountType = discountType;
        this.billDiscountValue = discountValue;
        this.billDiscountAmount = discountAmount;
        this.discountOccasion = occasion == null ? "" : occasion;
        this.billedByStaffId = billedByStaffId;
        this.billedBy = billedBy == null ? "-" : billedBy;
    }

    public void setPaymentParts(List<PaymentPart> parts) { this.paymentParts = new ArrayList<>(parts); }
    public void setItemsAmount(BigDecimal itemsAmount) { this.itemsAmount = itemsAmount; }
    public void setItems(List<OrderItem> items) { this.items = new ArrayList<>(items); }
    public BigDecimal getItemsAmount() { return itemsAmount; }
    public List<OrderItem> getItems() { return items; }

    public void setSessionDetails(String customerName, String tableName, LocalDateTime start, LocalDateTime end) {
        this.customerName = customerName;
        this.tableName = tableName;
        this.startTime = start;
        this.endTime = end;
    }

    public void setPaymentDetails(String status, String method, LocalDateTime paidAt) {
        this.paymentStatus = status;
        this.paymentMethod = method == null ? "-" : method;
        this.paidAt = paidAt;
    }

    public void setCorrectionDetails(String billStatus, String cancelReason) {
        this.billStatus = billStatus;
        this.cancelReason = cancelReason == null ? "" : cancelReason;
    }

    public void setStaffDetails(String servedBy, String receivedBy) {
        this.servedBy = servedBy == null ? "-" : servedBy;
        this.receivedBy = receivedBy == null ? "-" : receivedBy;
    }

    public boolean isCancelled() { return billStatus.equals("CANCELLED"); }
    public String getCancelReason() { return cancelReason; }
    public String getServedBy() { return servedBy; }
    public String getReceivedBy() { return receivedBy; }

    /** The word shown in lists: PAID, UNPAID, CANCELLED or REFUNDED. */
    public String getPaymentLabel() { return paymentStatus.equals("PENDING") ? "UNPAID" : paymentStatus; }

    public int getId() { return id; }
    public String getBillNumber() { return billNumber; }
    public void setBillNumber(String billNumber) { this.billNumber = billNumber; }
    public int getSessionId() { return sessionId; }
    public int getBilledMinutes() { return billedMinutes; }
    public BigDecimal getHourlyRate() { return hourlyRate; }
    public BigDecimal getSubtotal() { return subtotal; }
    public BigDecimal getDiscountPercent() { return discountPercent; }
    public BigDecimal getFinalAmount() { return finalAmount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public String getCustomerName() { return customerName; }
    public String getTableName() { return tableName; }
    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public String getPaymentStatus() { return paymentStatus; }
    public String getPaymentMethod() { return paymentMethod; }
    public boolean isPaid() { return paymentStatus.equals("PAID"); }
    public BigDecimal getTableAmount() { return tableAmount; }
    /** Everything before the bill discount: table time + food and drinks. */
    public BigDecimal getChargesSubtotal() { return tableAmount.add(itemsAmount); }
    public String getBillDiscountType() { return billDiscountType; }
    public BigDecimal getBillDiscountValue() { return billDiscountValue; }
    public BigDecimal getBillDiscountAmount() { return billDiscountAmount; }
    public String getDiscountOccasion() { return discountOccasion; }
    public Integer getBilledByStaffId() { return billedByStaffId; }
    /** Who billed it; for bills made before the "Billed by" list, the user who ended the game. */
    public String getBilledBy() { return billedBy.equals("-") ? servedBy : billedBy; }
    public List<PaymentPart> getPaymentParts() { return paymentParts; }

    /** For example "10% (Christmas)" or "Rs. 50.00". Empty when there is no discount. */
    public String getDiscountLabel() {
        if (billDiscountType.equals("NONE")) return "";
        String what = billDiscountType.equals("PERCENT") ? billDiscountValue.stripTrailingZeros().toPlainString() + "%"
                : String.format("Rs. %.2f", billDiscountValue);
        return discountOccasion.isEmpty() ? what : what + " (" + discountOccasion + ")";
    }

    /** Time played, for example "1 h 6 m". Games under one hour are billed as one full hour (rule 1). */
    public String getDurationText() {
        String played = (billedMinutes / 60) + " h " + (billedMinutes % 60) + " m";
        if (billedMinutes < 60) return played + " (billed as 1 h)";
        return played;
    }

    /** Puts spaces in front of a line so it sits in the middle of the 42-character slip. */
    static String centre(String text) {
        int spaces = Math.max(0, (42 - text.length()) / 2);
        return " ".repeat(spaces) + text;
    }

    /** Plain-text bill used for the receipt window and for printing. */
    @Override
    public String toReceipt() {
        DateTimeFormatter dateTime = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");
        DateTimeFormatter timeOnly = DateTimeFormatter.ofPattern("HH:mm");
        String line = "------------------------------------------\n";
        StringBuilder sb = new StringBuilder();
        sb.append(centre(util.AppSettings.getClubName().toUpperCase())).append("\n");
        if (!util.AppSettings.getClubPhone().isEmpty()) sb.append(centre("Phone: " + util.AppSettings.getClubPhone())).append("\n");
        sb.append("             Break. Pot. Repeat.\n");
        sb.append(line);
        sb.append(String.format("Bill No   : %s%n", billNumber));
        sb.append(String.format("Date      : %s%n", createdAt.format(dateTime)));
        sb.append(String.format("Customer  : %s%n", customerName));
        sb.append(String.format("Table     : %s%n", tableName));
        sb.append(String.format("Time      : %s - %s%n", startTime.format(timeOnly), endTime.format(timeOnly)));
        sb.append(String.format("Duration  : %s%n", getDurationText()));
        sb.append(String.format("Rate/hour : Rs. %.2f%n", hourlyRate));
        sb.append(line);
        sb.append(String.format("Table time           Rs. %10.2f%n", tableAmount));
        if (discountPercent.signum() > 0) {
            sb.append(String.format("  (member discount %.1f%% included)%n", discountPercent));
        }
        for (OrderItem item : items) {
            String text = item.getName() + " x" + item.getQuantity();
            if (text.length() > 20) text = text.substring(0, 20);
            sb.append(String.format("%-20s Rs. %10.2f%n", text, item.getAmount()));
        }
        sb.append(String.format("Subtotal             Rs. %10.2f%n", getChargesSubtotal()));
        sb.append(String.format("Discount           - Rs. %10.2f%n", billDiscountAmount));
        if (!getDiscountLabel().isEmpty()) sb.append(String.format("  %s%n", getDiscountLabel()));
        sb.append(String.format("FINAL TOTAL          Rs. %10.2f%n", finalAmount));
        sb.append(line);
        sb.append(String.format("Payment   : %s (%s)%n", getPaymentLabel(), paymentMethod));
        for (PaymentPart part : paymentParts) {
            sb.append(String.format("  %-4s               Rs. %10.2f%n", part.getMethod(), part.getAmount()));
        }
        sb.append(String.format("Billed by : %s%n", getBilledBy()));
        if (receivedBy != null && !receivedBy.equals("-")) sb.append(String.format("Received  : %s%n", receivedBy));
        if (isCancelled()) sb.append(String.format("*** BILL CANCELLED: %s%n", cancelReason));
        sb.append("        Thank you! Visit again.\n");
        return sb.toString();
    }
}
