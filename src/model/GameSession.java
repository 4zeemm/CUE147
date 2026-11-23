package model;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

/** One game played on a table (a row of the sessions table). */
public class GameSession {
    private int id;
    private int tableId;
    private String tableName;
    private int customerId;
    private String customerName;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private BigDecimal hourlyRate;
    private String status;   // ACTIVE, COMPLETED or CANCELLED

    public GameSession(int id, int tableId, String tableName, int customerId, String customerName,
                       LocalDateTime startTime, LocalDateTime endTime, BigDecimal hourlyRate, String status) {
        this.id = id;
        this.tableId = tableId;
        this.tableName = tableName;
        this.customerId = customerId;
        this.customerName = customerName;
        this.startTime = startTime;
        this.endTime = endTime;
        this.hourlyRate = hourlyRate;
        this.status = status;
    }

    public int getId() { return id; }
    public int getTableId() { return tableId; }
    public String getTableName() { return tableName; }
    public int getCustomerId() { return customerId; }
    public String getCustomerName() { return customerName; }
    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public BigDecimal getHourlyRate() { return hourlyRate; }
    public String getStatus() { return status; }
    public boolean isActive() { return status.equals("ACTIVE"); }

    /** Minutes played so far (or in total for a finished session). Never negative. */
    public long getMinutes(LocalDateTime now) {
        LocalDateTime end = endTime != null ? endTime : now;
        return Math.max(0, Duration.between(startTime, end).toMinutes());
    }
}
