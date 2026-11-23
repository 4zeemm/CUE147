package model;

/** The owner can do everything. */
public class Owner extends User {
    public Owner(int id, String username, String fullName, String passwordHash, boolean active) {
        super(id, username, fullName, passwordHash, active);
    }

    @Override public String getRole() { return "OWNER"; }
    @Override public boolean canManageTables() { return true; }
    @Override public boolean canManagePricing() { return true; }
    @Override public boolean canManageExpenses() { return true; }
    @Override public boolean canViewReports() { return true; }
    @Override public boolean canManageStaff() { return true; }
    @Override public boolean canCorrectFinancialRecords() { return true; }
}
