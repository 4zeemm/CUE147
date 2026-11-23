package model;

/** Staff can run the counter (sessions, customers, bills, payments) but cannot change the setup. */
public class Staff extends User {
    public Staff(int id, String username, String fullName, String passwordHash, boolean active) {
        super(id, username, fullName, passwordHash, active);
    }

    @Override public String getRole() { return "STAFF"; }
    @Override public boolean canManageTables() { return false; }
    @Override public boolean canManagePricing() { return false; }
    @Override public boolean canManageExpenses() { return false; }
    @Override public boolean canViewReports() { return false; }
    @Override public boolean canManageStaff() { return false; }
    @Override public boolean canCorrectFinancialRecords() { return false; }
}
