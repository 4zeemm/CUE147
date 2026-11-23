package model;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Money spent by the club (rent, electricity, salaries ...). */
public class Expense implements Receiptable {
    public static final String[] CATEGORIES = {"RENT", "ELECTRICITY", "SALARIES", "MAINTENANCE", "EQUIPMENT", "FOOD_STOCK", "MISCELLANEOUS"};

    private int id;
    private String category;
    private BigDecimal amount;
    private LocalDate date;
    private String description;
    private String paymentMethod;
    private String recordedBy = "";

    public Expense(int id, String category, BigDecimal amount, LocalDate date, String description, String paymentMethod) {
        this.id = id;
        this.category = category;
        this.amount = amount;
        this.date = date;
        this.description = description;
        this.paymentMethod = paymentMethod;
    }

    public int getId() { return id; }
    public String getCategory() { return category; }
    public BigDecimal getAmount() { return amount; }
    public LocalDate getDate() { return date; }
    public String getDescription() { return description; }
    public String getPaymentMethod() { return paymentMethod; }
    public String getRecordedBy() { return recordedBy; }
    public void setRecordedBy(String recordedBy) { this.recordedBy = recordedBy; }

    /** A small payment voucher for this expense. */
    @Override
    public String toReceipt() {
        String line = "------------------------------------------\n";
        StringBuilder sb = new StringBuilder();
        sb.append(Bill.centre(util.AppSettings.getClubName().toUpperCase())).append("\n");
        sb.append(Bill.centre("EXPENSE VOUCHER")).append("\n");
        sb.append(line);
        sb.append(String.format("Voucher No  : EXP-%05d%n", id));
        sb.append(String.format("Date        : %s%n", date));
        sb.append(String.format("Category    : %s%n", category));
        sb.append(String.format("Paid by     : %s%n", paymentMethod));
        sb.append(String.format("Recorded by : %s%n", recordedBy));
        sb.append(String.format("Description : %s%n", description == null || description.isEmpty() ? "-" : description));
        sb.append(line);
        sb.append(String.format("AMOUNT (Rs.) : %.2f%n", amount));
        sb.append(line);
        return sb.toString();
    }
}
