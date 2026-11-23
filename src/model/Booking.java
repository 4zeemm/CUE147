package model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/** An advance booking of a table. */
public class Booking {
    private int id;
    private int customerId;
    private String customerName;
    private int tableId;
    private String tableName;
    private LocalDate date;
    private LocalTime time;
    private BigDecimal durationHours;
    private String source;
    private String status;

    public Booking(int id, int customerId, String customerName, int tableId, String tableName,
                   LocalDate date, LocalTime time, BigDecimal durationHours, String source, String status) {
        this.id = id;
        this.customerId = customerId;
        this.customerName = customerName;
        this.tableId = tableId;
        this.tableName = tableName;
        this.date = date;
        this.time = time;
        this.durationHours = durationHours;
        this.source = source;
        this.status = status;
    }

    public int getId() { return id; }
    public String getDisplayId() { return String.format("B%03d", id); }
    public int getCustomerId() { return customerId; }
    public String getCustomerName() { return customerName; }
    public int getTableId() { return tableId; }
    public String getTableName() { return tableName; }
    public LocalDate getDate() { return date; }
    public LocalTime getTime() { return time; }
    public BigDecimal getDurationHours() { return durationHours; }
    public String getSource() { return source; }
    public String getStatus() { return status; }
}
