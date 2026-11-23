package model;

/**
 * Abstract base class for people who log in. A User is either an Owner or a Staff member.
 * The subclasses decide what they are allowed to do (polymorphism).
 */
public abstract class User {
    private int id;
    private String username;
    private String fullName;
    private String passwordHash;
    private boolean active;

    public User(int id, String username, String fullName, String passwordHash, boolean active) {
        this.id = id;
        this.username = username;
        this.fullName = fullName;
        this.passwordHash = passwordHash;
        this.active = active;
    }

    public int getId() { return id; }
    public String getUsername() { return username; }
    public String getFullName() { return fullName; }
    public String getPasswordHash() { return passwordHash; }
    public boolean isActive() { return active; }

    /** "OWNER" or "STAFF" - the value stored in the database. */
    public abstract String getRole();

    // Permissions: every subclass answers these questions in its own way.
    public abstract boolean canManageTables();
    public abstract boolean canManagePricing();
    public abstract boolean canManageExpenses();
    public abstract boolean canViewReports();
    public abstract boolean canManageStaff();
    /** Cancel or refund a bill, change a payment method, cancel an old game. */
    public abstract boolean canCorrectFinancialRecords();

    @Override
    public String toString() { return fullName + " (" + getRole() + ")"; }
}
