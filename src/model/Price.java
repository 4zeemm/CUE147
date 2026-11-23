package model;

import java.math.BigDecimal;

/** The hourly price for one table type and one gender. */
public class Price {
    private String tableType;
    private String gender;
    private BigDecimal hourlyRate;

    public Price(String tableType, String gender, BigDecimal hourlyRate) {
        this.tableType = tableType;
        this.gender = gender;
        this.hourlyRate = hourlyRate;
    }

    public String getTableType() { return tableType; }
    public String getGender() { return gender; }
    public BigDecimal getHourlyRate() { return hourlyRate; }
}
