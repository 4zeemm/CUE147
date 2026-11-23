package model;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A club customer (a row of the customers table). */
public class Customer extends Person {
    public static final String MEN = "MEN";
    public static final String LADIES = "LADIES";

    private int id;
    private String gender;
    private LocalDate registeredOn;
    private int totalVisits;
    private BigDecimal totalSpent = BigDecimal.ZERO;
    private boolean walkIn;

    /** Used when the user fills the "Add customer" form (no id yet). */
    public Customer(String name, String phone, String gender) {
        super(name, phone);
        this.gender = gender;
        this.registeredOn = LocalDate.now();
    }

    /** Used when we read a row from the database. */
    public Customer(int id, String name, String phone, String gender, LocalDate registeredOn,
                    int totalVisits, BigDecimal totalSpent, boolean walkIn) {
        super(name, phone);
        this.id = id;
        this.gender = gender;
        this.registeredOn = registeredOn;
        this.totalVisits = totalVisits;
        this.totalSpent = totalSpent;
        this.walkIn = walkIn;
    }

    public int getId() { return id; }
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }
    public LocalDate getRegisteredOn() { return registeredOn; }
    public int getTotalVisits() { return totalVisits; }
    public BigDecimal getTotalSpent() { return totalSpent; }
    public boolean isWalkIn() { return walkIn; }
    public String getDisplayId() { return String.format("C%03d", id); }
    public String getGenderLabel() { return gender.equals(MEN) ? "Men" : "Ladies"; }

    /** Shown in combo boxes. */
    @Override
    public String toString() { return isWalkIn() ? getName() : getName() + " (" + getPhone() + ")"; }
}
