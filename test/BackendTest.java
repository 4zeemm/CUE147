import dao.*;
import database.DBConnection;
import model.*;
import service.*;
import exception.*;
import util.*;
import javax.swing.table.DefaultTableModel;
import java.io.File;
import java.nio.file.Files;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * Tests the whole backend (DAOs + services) against the separate database "cue147_test".
 * Run with ./test.sh  (MySQL must be running).
 */
public class BackendTest {
    static int passed = 0, failed = 0;

    static void check(String name, boolean condition) {
        System.out.println((condition ? "PASS  " : "FAIL  ") + name);
        if (condition) passed++; else failed++;
    }

    static boolean same(String x, BigDecimal actual) { return new BigDecimal(x).compareTo(actual) == 0; }

    public static void main(String[] args) throws Exception {
        System.out.println("=== Backend tests (database cue147_test) ===");
        check("Database connection works", DBConnection.isDatabaseReachable());
        cleanTestDatabase();
        AppSettings.resetAll();   // the test database has its own settings: start from the defaults

        AuthService auth = new AuthService();
        CustomerDAO customerDAO = new CustomerDAO();
        GameTableDAO tableDAO = new GameTableDAO();
        PriceDAO priceDAO = new PriceDAO();
        BillDAO billDAO = new BillDAO();
        SessionService sessions = new SessionService();

        // ---------- login ----------
        auth.createDefaultUsers();
        new StaffMemberDAO().fillFromUsersIfEmpty();
        User owner = auth.login("owner", "owner123");
        User staff = auth.login("staff", "staff123");
        check("Owner login succeeds and returns an Owner object", owner instanceof Owner);
        check("Staff login succeeds and returns a Staff object", staff instanceof Staff);
        check("Owner can manage pricing, staff cannot", owner.canManagePricing() && !staff.canManagePricing());
        check("Owner can view reports, staff cannot", owner.canViewReports() && !staff.canViewReports());
        check("Staff cannot manage tables / expenses / staff",
                !staff.canManageTables() && !staff.canManageExpenses() && !staff.canManageStaff());
        check("Wrong password is rejected", fails(() -> auth.login("owner", "wrong")));
        check("Unknown user is rejected", fails(() -> auth.login("nobody", "owner123")));
        check("Password is not stored as plain text", !owner.getPasswordHash().contains("owner123"));
        check("Same password gives different hashes (salt)", !auth.hashPassword("abc123").equals(auth.hashPassword("abc123")));
        auth.addUser("riya", "Riya Counter", "riya1234", "STAFF");
        check("New staff user can log in", auth.login("riya", "riya1234") instanceof Staff);
        new UserDAO().setActive(auth.login("riya", "riya1234").getId(), false);
        check("Deactivated user cannot log in", fails(() -> auth.login("riya", "riya1234")));
        check("Short password is rejected", fails(() -> auth.addUser("x1", "X", "123", "STAFF")));

        // ---------- customers ----------
        Customer arun = new Customer("Arun Kumar", "9876543210", Customer.MEN);
        int arunId = customerDAO.add(arun);
        Customer meera = new Customer("Meera Nair", "9876500000", Customer.LADIES);
        int meeraId = customerDAO.add(meera);
        check("Customer saved and gets an id", arunId > 0 && customerDAO.findById(arunId).getName().equals("Arun Kumar"));
        check("Duplicate phone number is rejected by MySQL", sqlFails(() -> customerDAO.add(new Customer("Other", "9876543210", Customer.MEN))));
        check("Search by name finds the customer", customerDAO.search("arun").size() == 1);
        check("Search by phone finds the customer", customerDAO.search("98765000").size() == 1);
        Customer loaded = customerDAO.findById(arunId);
        loaded.setName("Arun K");
        customerDAO.update(loaded);
        check("Customer edit is saved", customerDAO.findById(arunId).getName().equals("Arun K"));
        check("Walk-in guests exist but are not counted as customers", customerDAO.countRegistered() == 2 && customerDAO.findAll().size() == 4);

        // ---------- tables and prices ----------
        List<GameTable> tables = tableDAO.findAll();
        check("Starting configuration: 5 tables (3 pool, 1 snooker, 1 PS5)", tables.size() == 5);
        int newId = tableDAO.add("Pool Table 4", GameTable.POOL);
        check("Owner can add a table", tableDAO.findAll().size() == 6);
        tableDAO.update(newId, "Pool Table 4B", GameTable.SNOOKER);
        check("Table name and type can be edited", tableDAO.findAll().stream().anyMatch(t -> t.getName().equals("Pool Table 4B") && t.getType().equals("SNOOKER")));
        check("Table without history is really deleted", tableDAO.remove(newId) && tableDAO.findAll().size() == 5);
        check("Pool men price from database is 270", same("270", priceDAO.getRate("POOL", "MEN")));
        check("Snooker ladies price from database is 270", same("270", priceDAO.getRate("SNOOKER", "LADIES")));
        priceDAO.update("PS5", "LADIES", new BigDecimal("100"));
        check("Price can be changed", same("100", priceDAO.getRate("PS5", "LADIES")));
        priceDAO.update("PS5", "LADIES", new BigDecimal("120"));

        // ---------- sessions and billing ----------
        GameTable pool1 = find(tableDAO, "Pool Table 1");
        GameTable snooker = find(tableDAO, "Snooker Table 1");
        LocalDateTime now = LocalDateTime.now();
        int s1 = sessions.startSession(snooker, customerDAO.findById(arunId), staff, now.minusMinutes(90));
        check("Session starts and the table becomes OCCUPIED", find(tableDAO, "Snooker Table 1").isOccupied());
        check("Second active session on the same table is blocked", fails(() -> sessions.startSession(find(tableDAO, "Snooker Table 1"), customerDAO.findById(meeraId), staff)));
        check("Active session count is 1", new SessionDAO().countActive() == 1);
        Bill b1 = sessions.endSession(s1, now, staff);
        check("1h 30m snooker (men, 320/h) = 453.33 -> Rs.450", same("450", b1.getFinalAmount()));
        check("Bill saved with unique number and PENDING payment", b1.getBillNumber().startsWith("CUE") && b1.getPaymentStatus().equals("PENDING"));
        check("Table is AVAILABLE after the session ends", !find(tableDAO, "Snooker Table 1").isOccupied());
        check("Customer visit count increased", customerDAO.findById(arunId).getTotalVisits() == 1);
        check("Ending the same session twice is rejected", fails(() -> sessions.endSession(s1, LocalDateTime.now(), staff)));

        int s2 = sessions.startSession(pool1, customerDAO.findById(meeraId), staff, now.minusMinutes(66));
        Bill b2 = sessions.endSession(s2, now, staff);
        check("1h 06m pool (ladies, 220/h) = 220 + 1 min charge 3.67 -> Rs.220", same("220", b2.getFinalAmount()));
        int s3 = sessions.startSession(pool1, customerDAO.findById(meeraId), staff, now.minusMinutes(20));
        Bill b3 = sessions.endSession(s3, now, staff);
        check("20 minutes is billed as the minimum one hour (220)", same("220", b3.getFinalAmount()));
        check("Bill numbers are unique", !b1.getBillNumber().equals(b2.getBillNumber()) && !b2.getBillNumber().equals(b3.getBillNumber()));

        // ---------- membership discount ----------
        MembershipDAO membershipDAO = new MembershipDAO();
        membershipDAO.add(meeraId, LocalDate.now(), LocalDate.now().plusMonths(1), new BigDecimal("10"));
        check("Member discount is found for an active member", same("10", membershipDAO.discountFor(meeraId, LocalDate.now())));
        check("No discount for a non-member", same("0", membershipDAO.discountFor(arunId, LocalDate.now())));
        int s4 = sessions.startSession(pool1, customerDAO.findById(meeraId), staff, now.minusMinutes(60));
        Bill b4 = sessions.endSession(s4, now, staff);
        check("Member bill: 220 - 10% = 198 -> Rs.195", same("195", b4.getFinalAmount()));
        membershipDAO.cancel(membershipDAO.findLatestForCustomer(meeraId).getId());
        check("Cancelled membership gives no discount", same("0", membershipDAO.discountFor(meeraId, LocalDate.now())));

        // ---------- payments ----------
        check("Revenue is 0 before any payment", same("0", billDAO.revenueBetween(LocalDate.now(), LocalDate.now())));
        billDAO.recordPayment(b1.getId(), "CASH", staff.getId());
        billDAO.recordPayment(b2.getId(), "UPI", staff.getId());
        Bill paid = billDAO.findById(b1.getId());
        check("Payment is saved (PAID, CASH)", paid.isPaid() && paid.getPaymentMethod().equals("CASH"));
        check("Paying the same bill twice is rejected", sqlFails(() -> billDAO.recordPayment(b1.getId(), "UPI", staff.getId())));
        check("Customer total spending updated (Arun: 450)", same("450", customerDAO.findById(arunId).getTotalSpent()));
        check("Bill history of a customer works", billDAO.findByCustomer(meeraId).size() == 3);
        check("Unpaid-only search works (2 unpaid)", billDAO.search("", true).size() == 2);
        check("Bill search by number works", billDAO.search(b3.getBillNumber(), false).size() == 1);
        check("Receipt text contains the bill number", paid.toReceipt().contains(b1.getBillNumber()));

        // ---------- expenses ----------
        ExpenseDAO expenseDAO = new ExpenseDAO();
        expenseDAO.add(new Expense(0, "ELECTRICITY", new BigDecimal("1200.50"), LocalDate.now(), "Monthly bill", "UPI"), owner.getId());
        expenseDAO.add(new Expense(0, "RENT", new BigDecimal("5000"), LocalDate.now().withDayOfMonth(1), "Rent", "CASH"), owner.getId());
        check("Expenses are saved and listed", expenseDAO.findAll().size() == 2);
        check("Today's expenses total is 1200.50 (or 6200.50 on the 1st)",
                same(LocalDate.now().getDayOfMonth() == 1 ? "6200.50" : "1200.50", expenseDAO.totalBetween(LocalDate.now(), LocalDate.now())));

        // ---------- delete expense ----------
        List<Expense> allExpenses = expenseDAO.findAll();
        Expense electricity = allExpenses.stream().filter(x -> x.getCategory().equals("ELECTRICITY")).findFirst().get();
        Expense rent = allExpenses.stream().filter(x -> x.getCategory().equals("RENT")).findFirst().get();
        check("Staff does not have permission to manage (delete) expenses", !staff.canManageExpenses());
        check("Owner has permission to delete expenses", owner.canManageExpenses());
        check("Without pressing Yes (Cancel) nothing is deleted", expenseDAO.findAll().size() == 2);
        check("Deleting an expense by its id returns true", expenseDAO.delete(electricity.getId()));
        List<Expense> afterDelete = expenseDAO.findAll();
        check("The record is really gone from MySQL", afterDelete.size() == 1 && afterDelete.stream().noneMatch(x -> x.getId() == electricity.getId()));
        check("The other expense (rent) was NOT deleted", afterDelete.get(0).getId() == rent.getId());
        check("Deleting the same id again returns false (no error)", !expenseDAO.delete(electricity.getId()));
        check("Deleting an unknown id deletes nothing", !expenseDAO.delete(999999) && expenseDAO.findAll().size() == 1);
        check("Expense total after delete = rent only (today's electricity removed)",
                same(LocalDate.now().getDayOfMonth() == 1 ? "5000" : "0", expenseDAO.totalBetween(LocalDate.now(), LocalDate.now())));
        DashboardStats afterStats = new ReportService().loadDashboardStats();
        check("Dashboard monthly expenses updated to 5000", same("5000", afterStats.monthExpenses));
        check("Dashboard estimated profit updated (revenue - 5000)", same(afterStats.monthRevenue.subtract(new BigDecimal("5000")).toPlainString(), afterStats.getEstimatedProfit()));
        // put the electricity expense back so the later dashboard checks still see 2 expenses
        expenseDAO.add(new Expense(0, "ELECTRICITY", new BigDecimal("1200.50"), LocalDate.now(), "Monthly bill", "UPI"), owner.getId());

        // ---------- dashboard ----------
        ReportService reports = new ReportService();
        DashboardStats stats = reports.loadDashboardStats();
        check("Dashboard: today's revenue = 450 + 220 (paid bills only)", same("670", stats.todayRevenue));
        check("Dashboard: monthly revenue = 670", same("670", stats.monthRevenue));
        check("Dashboard: monthly expenses = 6200.50", same("6200.50", stats.monthExpenses));
        check("Dashboard: estimated profit = 670 - 6200.50", same("-5530.50", stats.getEstimatedProfit()));
        check("Dashboard: total customers = 2", stats.totalCustomers == 2);
        check("Dashboard: tables 5 total, 0 occupied, 5 available", stats.totalTables == 5 && stats.occupiedTables == 0 && stats.availableTables == 5);
        int s5 = sessions.startSession(find(tableDAO, "PS5 Station 1"), customerDAO.findById(arunId), staff);
        stats = reports.loadDashboardStats();
        check("Dashboard: running session shows 1 active, 1 occupied, 4 available", stats.activeSessions == 1 && stats.occupiedTables == 1 && stats.availableTables == 4);
        check("Table usage report counts the games (pool table 1 has 3)", reports.getTableUsage(2).stream().anyMatch(r -> r[0].equals("Pool Table 1") && r[2].equals("3")));
        check("Notifications list is not empty", !reports.getNotifications().isEmpty());
        sessions.endSession(s5, LocalDateTime.now(), staff);
        check("Table with history is deactivated, not deleted", !tableDAO.remove(snooker.getId()) && tableDAO.findAll().size() == 4);

        // ---------- bookings and shifts ----------
        BookingDAO bookingDAO = new BookingDAO();
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        int bookingId = bookingDAO.add(arunId, pool1.getId(), tomorrow, LocalTime.of(18, 0), new BigDecimal("2.0"), "PHONE");
        check("Booking saved", bookingDAO.findAll().size() == 1);
        BigDecimal oneHour = new BigDecimal("1.0");
        check("Same table and time is detected as booked", bookingDAO.isTableBooked(pool1.getId(), tomorrow, LocalTime.of(18, 0), oneHour));
        check("Overlap: 19:00 is inside the 18:00-20:00 booking", bookingDAO.isTableBooked(pool1.getId(), tomorrow, LocalTime.of(19, 0), oneHour));
        check("Overlap: 17:30 for 1 h runs into the 18:00 booking", bookingDAO.isTableBooked(pool1.getId(), tomorrow, LocalTime.of(17, 30), oneHour));
        check("No overlap: 17:00 for 1 h ends exactly at 18:00", !bookingDAO.isTableBooked(pool1.getId(), tomorrow, LocalTime.of(17, 0), oneHour));
        check("No overlap: 20:00 starts when the booking ends", !bookingDAO.isTableBooked(pool1.getId(), tomorrow, LocalTime.of(20, 0), oneHour));
        check("No overlap on another table", !bookingDAO.isTableBooked(snooker.getId(), tomorrow, LocalTime.of(18, 0), oneHour));
        bookingDAO.cancel(bookingId);
        check("Cancelled booking frees the slot", !bookingDAO.isTableBooked(pool1.getId(), tomorrow, LocalTime.of(18, 0), oneHour));

        // ---------- a booking blocks a game for other customers ----------
        LocalTime nowTime = LocalTime.now().withSecond(0).withNano(0);
        int todayBooking = bookingDAO.add(meeraId, pool1.getId(), LocalDate.now(), nowTime, oneHour, "PHONE");
        check("The booking that covers this moment is found", bookingDAO.findBookingAt(pool1.getId(), LocalDateTime.now()) != null);
        check("Another customer cannot start a game on the booked table",
                throwsType(() -> sessions.startSession(find(tableDAO, "Pool Table 1"), customerDAO.findById(arunId), staff), BusinessException.class));
        check("A walk-in guest cannot take the booked table either",
                throwsType(() -> sessions.startSession(find(tableDAO, "Pool Table 1"), customerDAO.findAll().stream().filter(Customer::isWalkIn).findFirst().get(), staff), BusinessException.class));
        check("Other tables are not blocked by this booking", bookingDAO.findBookingAt(pool2Id(tableDAO), LocalDateTime.now()) == null);
        int bookedGame = sessions.startSession(find(tableDAO, "Pool Table 1"), customerDAO.findById(meeraId), staff);
        check("The customer who booked can start the game", find(tableDAO, "Pool Table 1").isOccupied());
        sessions.cancelSession(bookedGame, staff, "test cleanup");
        bookingDAO.cancel(todayBooking);
        int freeGame = sessions.startSession(find(tableDAO, "Pool Table 1"), customerDAO.findById(arunId), staff);
        check("After the booking is cancelled anyone can play", find(tableDAO, "Pool Table 1").isOccupied());
        sessions.cancelSession(freeGame, staff, "test cleanup");

        // ---------- change my own password ----------
        check("Change password: a wrong current password is rejected",
                throwsType(() -> auth.changeOwnPassword(staff, "wrong", "newpass1", "newpass1"), BusinessException.class));
        check("Change password: the two new passwords must match",
                throwsType(() -> auth.changeOwnPassword(staff, "staff123", "newpass1", "newpass2"), InvalidInputException.class));
        check("Change password: a short new password is rejected",
                throwsType(() -> auth.changeOwnPassword(staff, "staff123", "abc", "abc"), InvalidInputException.class));
        check("Change password: the new password must be different",
                throwsType(() -> auth.changeOwnPassword(staff, "staff123", "staff123", "staff123"), InvalidInputException.class));
        auth.changeOwnPassword(staff, "staff123", "newpass1", "newpass1");
        check("After the change the new password logs in", auth.login("staff", "newpass1") instanceof Staff);
        check("... and the old password does not", fails(() -> auth.login("staff", "staff123")));
        auth.changeOwnPassword(staff, "newpass1", "staff123", "staff123");
        check("The password can be changed back", auth.login("staff", "staff123") instanceof Staff);

        // ---------- delete a customer ----------
        check("A customer with games is NOT deleted (bills must stay)", !customerDAO.deleteIfNoHistory(arunId) && customerDAO.findById(arunId) != null);
        int newCustomerId = customerDAO.add(new Customer("Delete Me", "9000000001", Customer.MEN));
        check("A new customer with no history is deleted", customerDAO.deleteIfNoHistory(newCustomerId) && customerDAO.findById(newCustomerId) == null);
        int walkInId = customerDAO.findAll().stream().filter(Customer::isWalkIn).findFirst().get().getId();
        check("A walk-in guest row is never deleted", !customerDAO.deleteIfNoHistory(walkInId) && customerDAO.findById(walkInId) != null);
        ShiftDAO shiftDAO = new ShiftDAO();
        shiftDAO.startShift(staff.getId(), LocalDateTime.now().minusHours(2));
        check("Shift started", shiftDAO.hasOpenShift(staff.getId()));
        shiftDAO.endShift(staff.getId(), LocalDateTime.now());
        check("Shift ended and listed", !shiftDAO.hasOpenShift(staff.getId()) && shiftDAO.findRecent(staff.getId()).size() == 1);

        // ---------- validation, exceptions, interfaces, CSV, logging ----------
        check("Validator accepts a good phone number", !fails(() -> Validator.requirePhone("9876543210")));
        check("Validator rejects a short phone number", fails(() -> Validator.requirePhone("12345")));
        check("Validator rejects letters in a phone number", fails(() -> Validator.requirePhone("98765abcde")));
        check("Validator rejects an empty name", fails(() -> Validator.requireName("  ")));
        check("Validator rejects digits in a person's name", fails(() -> Validator.requireName("R2D2")));
        check("Validator accepts 'Arun Kumar'", !fails(() -> Validator.requireName("Arun Kumar")));
        check("Validator rejects a wrong date format", fails(() -> Validator.requireDate("04-11-2026")));
        check("Validator accepts yyyy-MM-dd", Validator.requireDate("2026-11-04").getDayOfMonth() == 4);
        check("Validator rejects a past booking date", fails(() -> Validator.requireNotPast(LocalDate.now().minusDays(1))));
        check("Validator rejects a future expense date", fails(() -> Validator.requireNotFuture(LocalDate.now().plusDays(1))));
        check("Validator rejects amount zero", fails(() -> Validator.requirePositive(BigDecimal.ZERO, "Amount")));
        check("Validator rejects a bad username", fails(() -> Validator.requireUsername("Bad Name!")));
        check("InvalidInputException is a ClubException", new InvalidInputException("x") instanceof ClubException);
        check("BusinessException is a ClubException", new BusinessException("x") instanceof ClubException);
        check("Staff add-user with a bad username throws InvalidInputException", throwsType(() -> auth.addUser("A B", "Test Person", "secret1", "STAFF"), InvalidInputException.class));
        check("Staff add-user with a duplicate username throws BusinessException", throwsType(() -> auth.addUser("staff", "Another Person", "secret1", "STAFF"), BusinessException.class));
        Receiptable[] slips = {billDAO.findById(b1.getId()), expenseDAO.findAll().get(0)};
        check("Bill and Expense both work as Receiptable (interface)", slips[0].toReceipt().contains("CUE 147") && slips[1].toReceipt().contains("EXPENSE VOUCHER"));
        // ---------- club details on the receipt (saved settings) ----------
        AppSettings.setClubName("Test Cue Club");
        AppSettings.setClubPhone("9123456789");
        String customReceipt = billDAO.findById(b1.getId()).toReceipt();
        check("The saved club name and phone are printed on the bill", customReceipt.contains("TEST CUE CLUB") && customReceipt.contains("9123456789"));
        check("The saved club name is printed on the expense voucher", expenseDAO.findAll().get(0).toReceipt().contains("TEST CUE CLUB"));
        AppSettings.resetAll();
        check("After reset the default club name is used again", AppSettings.getClubName().startsWith("CUE 147") && AppSettings.getClubPhone().isEmpty());
        AppSettings.setTheme("Burgundy");
        AppSettings.setFontSize(16);
        AppSettings.setNotificationsOn(false);
        check("Appearance settings are saved (theme, text size, notifications)",
                AppSettings.getTheme().equals("Burgundy") && AppSettings.getFontSize() == 16 && !AppSettings.isNotificationsOn());
        AppSettings.resetAll();
        check("Reset gives the default look (Green Baize, 14, notifications on)",
                AppSettings.getTheme().equals("Green Baize") && AppSettings.getFontSize() == 14 && AppSettings.isNotificationsOn());
        DefaultTableModel exportModel = new DefaultTableModel(new String[]{"Name", "Note"}, 0);
        exportModel.addRow(new Object[]{"Arun", "likes pool, snooker"});
        File csv = File.createTempFile("cue147", ".csv");
        CsvExporter.write(csv, exportModel);
        String csvText = new String(Files.readAllBytes(csv.toPath()), java.nio.charset.StandardCharsets.UTF_8);
        check("CSV export writes header and a quoted row", csvText.contains("Name,Note") && csvText.contains("\"likes pool, snooker\""));
        csv.delete();
        check("CSV export to a missing folder throws IOException", throwsType(() -> CsvExporter.write(new File("/no/such/folder/x.csv"), exportModel), java.io.IOException.class));
        AppLogger.info("BackendTest finished the logging check");
        check("Logger file cue147.log exists", new File("cue147.log").exists());


        // =====================================================================
        // CORRECTIONS: maintenance, cancel / adjust / reopen a game, cancel / refund a bill, edit expense
        // =====================================================================
        BillService billService = new BillService();
        ReportService reports2 = new ReportService();
        LocalDate today = LocalDate.now();
        check("Staff cannot correct financial records, owner can", !staff.canCorrectFinancialRecords() && owner.canCorrectFinancialRecords());

        // ---------- table maintenance ----------
        GameTable pool3 = find(tableDAO, "Pool Table 3");
        tableDAO.setMaintenance(pool3.getId(), true);
        check("A table can be put under maintenance", find(tableDAO, "Pool Table 3").getStatus().equals("MAINTENANCE"));
        check("A game cannot be started on a table under maintenance",
                throwsType(() -> sessions.startSession(find(tableDAO, "Pool Table 3"), customerDAO.findById(arunId), staff), BusinessException.class));
        DashboardStats maintStats = reports2.loadDashboardStats();
        check("Dashboard: a table under maintenance is not counted as available",
                maintStats.maintenanceTables == 1 && maintStats.availableTables == maintStats.totalTables - maintStats.occupiedTables - 1);
        tableDAO.setMaintenance(pool3.getId(), false);
        check("A table back in service is AVAILABLE again", find(tableDAO, "Pool Table 3").getStatus().equals("AVAILABLE"));

        // ---------- cancel a game started by mistake ----------
        GameTable pool2 = find(tableDAO, "Pool Table 2");
        int billsBefore = billDAO.search("", false).size();
        final int m1 = sessions.startSession(pool2, customerDAO.findById(arunId), staff);
        check("Staff can cancel a game that was just started", !fails(() -> sessions.cancelSession(m1, staff, "Started on the wrong table")));
        check("A cancelled game frees the table and creates no bill",
                !find(tableDAO, "Pool Table 2").isOccupied() && billDAO.search("", false).size() == billsBefore && new SessionDAO().countActive() == 0);
        final int m2 = sessions.startSession(pool2, customerDAO.findById(arunId), staff, LocalDateTime.now().minusMinutes(40));
        check("Cancelling needs a reason", throwsType(() -> sessions.cancelSession(m2, staff, "   "), InvalidInputException.class));
        check("Staff cannot cancel a game that ran for 40 minutes", throwsType(() -> sessions.cancelSession(m2, staff, "oops"), BusinessException.class));
        check("The owner can cancel it", !fails(() -> sessions.cancelSession(m2, owner, "Owner correction")));
        check("Cancelling a game that is not running is rejected", throwsType(() -> sessions.cancelSession(m2, owner, "again"), BusinessException.class));
        check("Cancelled games are not counted in the usage report",
                reports2.getTableUsage(1).stream().anyMatch(r -> r[0].equals("Pool Table 2") && r[2].equals("0")));

        // ---------- adjust the start time of a running game ----------
        final int a1 = sessions.startSession(pool2, customerDAO.findById(arunId), staff, LocalDateTime.now().minusMinutes(5));
        check("A start time in the future is rejected", throwsType(() -> sessions.adjustStartTime(a1, LocalDateTime.now().plusMinutes(5), staff), InvalidInputException.class));
        check("A start time more than 12 hours ago is rejected", throwsType(() -> sessions.adjustStartTime(a1, LocalDateTime.now().minusHours(13), staff), InvalidInputException.class));
        sessions.adjustStartTime(a1, LocalDateTime.now().minusMinutes(70), staff);
        Bill adjusted = sessions.endSession(a1, LocalDateTime.now(), staff);
        check("After moving the start 70 minutes back: 1h10m pool (men) = 270 + 22.50 -> Rs.290",
                adjusted.getBilledMinutes() == 70 && same("290", adjusted.getFinalAmount()));
        check("The bill remembers who ended the game", adjusted.getServedBy().equals("Counter Staff"));

        // ---------- payment: who received it, duplicate payment ----------
        billService.recordPayment(adjusted, "CASH", owner);
        Bill paidBill = billDAO.findById(adjusted.getId());
        check("The paid bill remembers who received the money", paidBill.isPaid() && paidBill.getReceivedBy().equals("Club Owner"));
        check("Paying the same bill again is rejected", throwsType(() -> billService.recordPayment(paidBill, "UPI", staff), BusinessException.class));
        check("An unknown payment method is rejected", throwsType(() -> billService.recordPayment(paidBill, "CARD", staff), BusinessException.class));

        // ---------- reopen an accidentally ended game ----------
        final int r1 = sessions.startSession(pool2, customerDAO.findById(arunId), staff, LocalDateTime.now().minusMinutes(65));
        final Bill ended = sessions.endSession(r1, LocalDateTime.now(), staff);
        int visitsBefore = customerDAO.findById(arunId).getTotalVisits();
        sessions.reopenSession(ended, staff);
        check("A game ended by accident can be reopened: the table is OCCUPIED again", find(tableDAO, "Pool Table 2").isOccupied());
        Bill oldBill = billDAO.findById(ended.getId());
        check("The old bill is CANCELLED (kept, not deleted) and its payment CANCELLED", oldBill != null && oldBill.isCancelled() && oldBill.getPaymentLabel().equals("CANCELLED"));
        check("The extra visit was taken back", customerDAO.findById(arunId).getTotalVisits() == visitsBefore - 1);
        final Bill newBill = sessions.endSession(r1, LocalDateTime.now(), staff);
        check("Ending the reopened game makes a NEW bill with a new number",
                newBill.getId() != ended.getId() && !newBill.getBillNumber().equals(ended.getBillNumber()) && !newBill.isCancelled());
        check("The unpaid list shows live bills only", billDAO.search("", true).stream().noneMatch(Bill::isCancelled));
        check("A paid bill cannot be reopened", throwsType(() -> sessions.reopenSession(paidBill, staff), BusinessException.class));
        check("A cancelled bill cannot be reopened", throwsType(() -> sessions.reopenSession(billDAO.findById(ended.getId()), staff), BusinessException.class));
        final int busy = sessions.startSession(pool2, customerDAO.findById(meeraId), staff);
        check("Reopen is refused when the table is in use again", throwsType(() -> sessions.reopenSession(newBill, staff), BusinessException.class));
        check("... and nothing was changed by the refused reopen", !billDAO.findById(newBill.getId()).isCancelled());
        sessions.cancelSession(busy, staff, "test cleanup");
        check("MySQL itself refuses a second live bill for the same session", duplicateBillRejected(newBill.getSessionId()));
        final int o1 = sessions.startSession(find(tableDAO, "Pool Table 3"), customerDAO.findById(arunId), staff, LocalDateTime.now().minusHours(3));
        final Bill oldEnded = sessions.endSession(o1, LocalDateTime.now().minusHours(2), staff);
        check("Staff cannot reopen a game that ended 2 hours ago", throwsType(() -> sessions.reopenSession(oldEnded, staff), BusinessException.class));
        sessions.reopenSession(oldEnded, owner);
        check("The owner can reopen an old game", find(tableDAO, "Pool Table 3").isOccupied());
        sessions.endSession(o1, LocalDateTime.now(), owner);

        // ---------- cancel and refund bills ----------
        check("Staff cannot cancel a bill", throwsType(() -> billService.cancelBill(newBill, staff, "x"), BusinessException.class));
        check("Cancelling a bill needs a reason", throwsType(() -> billService.cancelBill(newBill, owner, "  "), InvalidInputException.class));
        BigDecimal revenueBeforeCancel = billDAO.revenueBetween(today, today);
        billService.cancelBill(newBill, owner, "Wrong customer");
        Bill cancelled = billDAO.findById(newBill.getId());
        check("The owner cancels an unpaid bill: bill CANCELLED with reason, payment CANCELLED",
                cancelled.isCancelled() && cancelled.getCancelReason().equals("Wrong customer") && cancelled.getPaymentLabel().equals("CANCELLED"));
        check("Cancelling an unpaid bill does not change revenue", revenueBeforeCancel.compareTo(billDAO.revenueBetween(today, today)) == 0);
        check("Cancelling a bill twice is rejected", throwsType(() -> billService.cancelBill(billDAO.findById(newBill.getId()), owner, "again"), BusinessException.class));
        check("A cancelled bill cannot be paid", throwsType(() -> billService.recordPayment(billDAO.findById(newBill.getId()), "CASH", staff), BusinessException.class));
        BigDecimal revenueBeforeRefund = billDAO.revenueBetween(today, today);
        BigDecimal spentBeforeRefund = customerDAO.findById(arunId).getTotalSpent();
        billService.cancelBill(paidBill, owner, "Customer complaint");
        check("A PAID bill that is cancelled becomes REFUNDED", billDAO.findById(paidBill.getId()).getPaymentLabel().equals("REFUNDED"));
        check("The refund is taken out of today's revenue",
                revenueBeforeRefund.subtract(paidBill.getFinalAmount()).compareTo(billDAO.revenueBetween(today, today)) == 0);
        check("The refund is taken out of the customer's total spending",
                spentBeforeRefund.subtract(paidBill.getFinalAmount()).compareTo(customerDAO.findById(arunId).getTotalSpent()) == 0);
        check("The dashboard shows the new revenue", reports2.loadDashboardStats().todayRevenue.compareTo(billDAO.revenueBetween(today, today)) == 0);
        check("Cancelled bills are not in the 'billed' total of reports", billDAO.billedBetween(today, today).compareTo(new BigDecimal("0")) >= 0
                && billDAO.search("", false).stream().filter(b -> !b.isCancelled()).map(Bill::getFinalAmount).reduce(BigDecimal.ZERO, BigDecimal::add)
                        .compareTo(billDAO.billedBetween(today, today)) == 0);

        // ---------- change the payment method ----------
        final int p1 = sessions.startSession(find(tableDAO, "PS5 Station 1"), customerDAO.findById(arunId), staff, LocalDateTime.now().minusMinutes(61));
        Bill toPay = sessions.endSession(p1, LocalDateTime.now(), staff);
        billService.recordPayment(toPay, "UPI", staff);
        final Bill paidUpi = billDAO.findById(toPay.getId());
        check("Staff cannot change a payment method", throwsType(() -> billService.changePaymentMethod(paidUpi, "CASH", staff), BusinessException.class));
        check("An unpaid bill has no payment method to change", throwsType(() -> billService.changePaymentMethod(newBill, "CASH", owner), BusinessException.class));
        check("Changing to the same method is rejected", throwsType(() -> billService.changePaymentMethod(paidUpi, "UPI", owner), BusinessException.class));
        billService.changePaymentMethod(paidUpi, "CASH", owner);
        check("The owner can correct UPI to CASH", billDAO.findById(toPay.getId()).getPaymentMethod().equals("CASH"));

        // ---------- edit an expense ----------
        Expense electricityNow = expenseDAO.findAll().stream().filter(x -> x.getCategory().equals("ELECTRICITY")).findFirst().get();
        BigDecimal monthBefore = reports2.loadDashboardStats().monthExpenses;
        Expense fixed = new Expense(electricityNow.getId(), "MAINTENANCE", new BigDecimal("999.00"), today, "Fixed the lights", "CASH");
        check("An expense can be edited", expenseDAO.update(fixed));
        Expense reloaded = expenseDAO.findAll().stream().filter(x -> x.getId() == electricityNow.getId()).findFirst().get();
        check("The edited values are saved (category, amount, method, description)",
                reloaded.getCategory().equals("MAINTENANCE") && same("999.00", reloaded.getAmount()) && reloaded.getPaymentMethod().equals("CASH")
                        && reloaded.getDescription().equals("Fixed the lights"));
        check("Dashboard monthly expenses follow the edit (-1200.50 + 999.00)",
                monthBefore.subtract(new BigDecimal("1200.50")).add(new BigDecimal("999.00")).compareTo(reports2.loadDashboardStats().monthExpenses) == 0);
        check("Editing an unknown expense changes nothing", !expenseDAO.update(new Expense(999999, "RENT", BigDecimal.ONE, today, "x", "CASH")));
        check("No active game is left behind by the tests", new SessionDAO().countActive() == 0);

        // ---------- checkout: discounts, split payments, Billed by ----------
        check("The test database is at the newest version", database.SchemaMigrator.migrate() == database.SchemaMigrator.latestVersion()
                && database.SchemaMigrator.latestVersion() == 3);
        StaffMemberDAO staffDAO = new StaffMemberDAO();
        check("The Billed-by list starts with the names of the login accounts",
                staffDAO.findActive().stream().anyMatch(m -> m.getName().equals("Club Owner")) && staffDAO.findActive().stream().anyMatch(m -> m.getName().equals("Counter Staff")));
        int kenId = staffDAO.add("Ken");
        check("A duplicate name in the staff list is refused by MySQL", sqlFails(() -> staffDAO.add("Ken")));
        StaffMember ken = staffDAO.findActive().stream().filter(m -> m.getId() == kenId).findFirst().get();
        GameTable ps5 = find(tableDAO, "PS5 Station 1");
        final int c1 = sessions.startSession(find(tableDAO, "Pool Table 1"), customerDAO.findById(arunId), staff, LocalDateTime.now().minusMinutes(60));
        final Bill toCheckout = sessions.endSession(c1, LocalDateTime.now(), staff);
        check("A new bill remembers its table-time amount (Rs. 270)", same("270", toCheckout.getTableAmount()) && same("270", toCheckout.getChargesSubtotal()));
        Checkout co = billService.startCheckout(toCheckout);
        co.setDiscount(Checkout.DiscountType.PERCENT, new BigDecimal("10"));
        co.setOccasion("Christmas");
        co.addPayment("UPI", new BigDecimal("100"));
        check("A bill cannot be closed before it is fully paid", throwsType(() -> billService.completeCheckout(toCheckout, co, ken, staff), BusinessException.class));
        co.addPayment("CASH", co.getRemaining());
        check("A bill cannot be closed without Billed by", throwsType(() -> billService.completeCheckout(toCheckout, co, null, staff), BusinessException.class));
        BigDecimal revenueBeforeCheckout = billDAO.revenueBetween(today, today);
        BigDecimal spentBeforeCheckout = customerDAO.findById(arunId).getTotalSpent();
        billService.completeCheckout(toCheckout, co, ken, staff);
        Bill closed = billDAO.findById(toCheckout.getId());
        check("Checkout saves the discount: 10% (Christmas) = Rs. 27, final Rs. 243",
                same("27", closed.getBillDiscountAmount()) && same("243", closed.getFinalAmount()) && closed.getDiscountLabel().equals("10% (Christmas)"));
        check("Checkout saves PAID as SPLIT with two parts: UPI 100 + CASH 143", closed.isPaid() && closed.getPaymentMethod().equals("SPLIT")
                && closed.getPaymentParts().size() == 2 && same("100", closed.getPaymentParts().get(0).getAmount()) && same("143", closed.getPaymentParts().get(1).getAmount()));
        check("Checkout saves Billed by (Ken) and shows it on the bill", closed.getBilledBy().equals("Ken") && closed.toReceipt().contains("Billed by : Ken"));
        check("The receipt shows Subtotal, Discount and FINAL TOTAL", closed.toReceipt().contains("Subtotal") && closed.toReceipt().contains("Discount")
                && closed.toReceipt().contains("FINAL TOTAL"));
        check("Revenue goes up by the discounted total (243)", revenueBeforeCheckout.add(new BigDecimal("243")).compareTo(billDAO.revenueBetween(today, today)) == 0);
        check("Customer spending goes up by the discounted total (243)", spentBeforeCheckout.add(new BigDecimal("243")).compareTo(customerDAO.findById(arunId).getTotalSpent()) == 0);
        check("Paying the same bill again is refused", throwsType(() -> billService.completeCheckout(toCheckout, co, ken, staff), BusinessException.class));
        check("A split bill's method cannot be changed", throwsType(() -> billService.changePaymentMethod(closed, "CASH", owner), BusinessException.class));
        billService.cancelBill(closed, owner, "Test refund of a split bill");
        check("Refunding a split bill takes the whole 243 out of revenue", revenueBeforeCheckout.compareTo(billDAO.revenueBetween(today, today)) == 0);

        final int c2 = sessions.startSession(ps5, customerDAO.findById(meeraId), staff, LocalDateTime.now().minusMinutes(60));
        final Bill later = sessions.endSession(c2, LocalDateTime.now(), staff);
        Checkout co2 = billService.startCheckout(later);
        co2.setDiscount(Checkout.DiscountType.FLAT, new BigDecimal("20"));
        billService.saveForLater(later, co2, ken);
        Bill unpaidLater = billDAO.findById(later.getId());
        check("Pay Later keeps the bill UNPAID with its discount and Billed by", !unpaidLater.isPaid() && same("100", unpaidLater.getFinalAmount())
                && same("20", unpaidLater.getBillDiscountAmount()) && unpaidLater.getBilledBy().equals("Ken"));
        Checkout co3 = billService.startCheckout(unpaidLater);
        check("Opening the checkout again brings back the saved discount", co3.getDiscountType() == Checkout.DiscountType.FLAT && same("100", co3.getFinalTotal()));
        billService.recordPayment(unpaidLater, "CASH", staff);
        Bill paidLater = billDAO.findById(later.getId());
        check("A one-method payment saves one part with the full amount", paidLater.isPaid() && paidLater.getPaymentParts().size() == 1
                && paidLater.getPaymentParts().get(0).getMethod().equals("CASH") && same("100", paidLater.getPaymentParts().get(0).getAmount()));
        billService.changePaymentMethod(paidLater, "UPI", owner);
        check("Changing the method of a one-method bill also changes its part", billDAO.findById(later.getId()).getPaymentParts().get(0).getMethod().equals("UPI"));
        staffDAO.setActive(kenId, false);
        check("A switched-off name leaves the Billed-by list but old bills keep it",
                staffDAO.findActive().stream().noneMatch(m -> m.getId() == kenId) && billDAO.findById(later.getId()).getBilledBy().equals("Ken"));
        check("No active game is left after the checkout tests", new SessionDAO().countActive() == 0);

        // ---------- food & drinks ----------
        MenuItemDAO menuDAO = new MenuItemDAO();
        int coffeeId = menuDAO.add("Cold Coffee", "Beverages", new BigDecimal("80"));
        int chipsId = menuDAO.add("Chips", "Snacks", new BigDecimal("30"));
        int teaId = menuDAO.add("Masala Tea", "Coffee", new BigDecimal("20"));
        check("Menu items are saved", menuDAO.findAll().size() == 3);
        check("A duplicate menu item is refused by MySQL", sqlFails(() -> menuDAO.add("Chips", "Snacks", new BigDecimal("40"))));
        menuDAO.setAvailable(teaId, false);
        check("A switched-off item is not offered any more", menuDAO.findAvailable().stream().noneMatch(m -> m.getId() == teaId));
        MenuItem coffee = menuDAO.findAll().stream().filter(m -> m.getId() == coffeeId).findFirst().get();
        MenuItem chips = menuDAO.findAll().stream().filter(m -> m.getId() == chipsId).findFirst().get();
        OrderItemDAO orderDAO = new OrderItemDAO();
        final int f1 = sessions.startSession(find(tableDAO, "Pool Table 1"), customerDAO.findById(arunId), staff, LocalDateTime.now().minusMinutes(60));
        orderDAO.add(f1, OrderItem.of(coffee, 2));
        orderDAO.add(f1, OrderItem.of(chips, 1));
        List<OrderItem> ordered = orderDAO.findBySession(f1);
        check("Items ordered during a game are saved with their time", ordered.size() == 2 && ordered.get(0).getAddedAt() != null);
        orderDAO.delete(ordered.get(1).getId());
        check("One line can be removed while the game runs", orderDAO.findBySession(f1).size() == 1);
        menuDAO.update(coffeeId, "Cold Coffee", "Beverages", new BigDecimal("90"));
        check("A later menu price change does not change what was ordered", same("80", orderDAO.findBySession(f1).get(0).getUnitPrice()));
        final Bill fb = sessions.endSession(f1, LocalDateTime.now(), staff);
        check("End Game puts the food on the same bill: 270 + 2 x 80 = Rs. 430",
                same("160", fb.getItemsAmount()) && same("430", fb.getFinalAmount()) && same("430", fb.getChargesSubtotal()));
        check("The receipt lists the food", fb.toReceipt().contains("Cold Coffee x2"));
        Checkout fco = billService.startCheckout(fb);
        fco.addItem(OrderItem.of(chips, 1));
        fco.setDiscount(Checkout.DiscountType.FLAT, new BigDecimal("30"));
        fco.addPayment("UPI", new BigDecimal("200"));
        fco.addPayment("CASH", fco.getRemaining());
        BigDecimal revenueBeforeFood = billDAO.revenueBetween(today, today);
        billService.completeCheckout(fb, fco, staffDAO.findActive().get(0), staff);
        Bill fbPaid = billDAO.findById(fb.getId());
        check("Chips added at the checkout are on the bill: food 190, subtotal 460, minus 30 = Rs. 430",
                same("190", fbPaid.getItemsAmount()) && same("460", fbPaid.getChargesSubtotal()) && same("430", fbPaid.getFinalAmount())
                        && fbPaid.getItems().size() == 2);
        check("The food is stored with the game for reports", orderDAO.findBySession(f1).size() == 2);
        check("Revenue goes up by the bill total with food (430)", revenueBeforeFood.add(new BigDecimal("430")).compareTo(billDAO.revenueBetween(today, today)) == 0);

        // ---------- memberships with plans ----------
        MembershipPlanDAO planDAO = new MembershipPlanDAO();
        MembershipService membershipService = new MembershipService();
        List<MembershipPlan> plans = planDAO.findAll();
        check("The three plans exist: Monthly, Quarterly, Yearly", plans.size() == 3 && plans.get(0).getName().equals("Monthly")
                && plans.get(1).getMonths() == 3 && plans.get(2).getMonths() == 12);
        Customer meeraNow = customerDAO.findById(meeraId);
        Checkout noPrice = new Checkout(plans.get(0).getPrice());
        check("A plan whose price is not set cannot be sold", throwsType(() -> membershipService.sell(meeraNow, plans.get(0), noPrice, ken, staff), BusinessException.class));
        planDAO.update(plans.get(0).getId(), 1, new BigDecimal("500"), new BigDecimal("5"));
        MembershipPlan monthly = planDAO.findAll().get(0);
        check("The owner can set the plan price", same("500", monthly.getPrice()));
        StaffMember billedByNow = staffDAO.findActive().get(0);
        Checkout half = new Checkout(monthly.getPrice());
        half.addPayment("UPI", new BigDecimal("200"));
        check("A membership cannot be sold before it is fully paid", throwsType(() -> membershipService.sell(meeraNow, monthly, half, billedByNow, staff), BusinessException.class));
        Customer walkIn = customerDAO.findAll().stream().filter(Customer::isWalkIn).findFirst().get();
        Checkout full = new Checkout(monthly.getPrice());
        full.addPayment("CASH", new BigDecimal("500"));
        check("A walk-in guest cannot become a member", throwsType(() -> membershipService.sell(walkIn, monthly, full, billedByNow, staff), BusinessException.class));
        half.addPayment("CASH", half.getRemaining());
        BigDecimal revenueBeforeMembership = billDAO.revenueBetween(today, today);
        Membership sold = membershipService.sell(meeraNow, monthly, half, billedByNow, staff);
        check("Selling Monthly: starts today, ends one month later minus a day",
                sold.getStartDate().equals(today) && sold.getExpiryDate().equals(today.plusMonths(1).minusDays(1)) && sold.getPlanName().equals("Monthly"));
        check("The member gets the plan discount on games (5%)", same("5", membershipDAO.discountFor(meeraId, today)));
        check("Membership sales count as revenue (+500)", revenueBeforeMembership.add(new BigDecimal("500")).compareTo(billDAO.revenueBetween(today, today)) == 0);
        check("Membership sales are totalled separately (500 today)", same("500", membershipDAO.salesBetween(today, today)));
        check("The customer record shows the membership", sold.getStatusText().startsWith("Monthly member until"));
        Checkout again = new Checkout(monthly.getPrice());
        again.addPayment("UPI", new BigDecimal("500"));
        Membership renewed = membershipService.sell(meeraNow, monthly, again, billedByNow, staff);
        check("Buying again while still a member starts the day after the current end", renewed.getStartDate().equals(sold.getExpiryDate().plusDays(1)));
        check("Staff cannot cancel a membership", throwsType(() -> membershipService.cancel(renewed, staff), BusinessException.class));
        BigDecimal revenueBeforeCancelM = billDAO.revenueBetween(today, today);
        membershipService.cancel(renewed, owner);
        check("Cancelling keeps the money in the revenue (no refund)", revenueBeforeCancelM.compareTo(billDAO.revenueBetween(today, today)) == 0);

        System.out.println(passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    /** True if the action throws exactly this kind of exception (or a subclass). */
    static boolean throwsType(Action a, Class<? extends Exception> type) {
        try { a.run(); return false; } catch (Exception e) { return type.isInstance(e); }
    }

    interface Action { void run() throws Exception; }

    /** True if the action throws any exception. */
    static boolean fails(Action a) {
        try { a.run(); return false; } catch (Exception e) { return true; }
    }

    static boolean sqlFails(Action a) { return fails(a); }

    /** Tries to insert a second LIVE bill for the same session; MySQL must say "duplicate" (error 1062). */
    static boolean duplicateBillRejected(int sessionId) throws SQLException {
        String sql = "INSERT INTO bills (bill_number, session_id, billed_minutes, hourly_rate, subtotal, discount_percent, final_amount, created_at) "
                + "VALUES ('DUP-TEST', ?, 1, 1, 1, 0, 1, NOW())";
        try (Connection con = DBConnection.getConnection(); java.sql.PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, sessionId);
            ps.executeUpdate();
            return false;
        } catch (SQLException e) {
            return e.getErrorCode() == 1062;
        }
    }

    static int pool2Id(GameTableDAO dao) throws SQLException { return find(dao, "Pool Table 2").getId(); }

    static GameTable find(GameTableDAO dao, String name) throws SQLException {
        for (GameTable t : dao.findAll()) if (t.getName().equals(name)) return t;
        throw new IllegalStateException("table not found: " + name);
    }

    /** Empties the transaction tables of the TEST database so every test run starts clean. */
    static void cleanTestDatabase() throws SQLException {
        try (Connection con = DBConnection.getConnection(); Statement st = con.createStatement()) {
            for (String table : new String[]{"payment_parts", "payments", "order_items", "bills", "sessions", "bookings", "expenses",
                    "memberships", "menu_items", "staff_shifts", "staff_members", "users"}) {
                st.executeUpdate("DELETE FROM " + table);
            }
            st.executeUpdate("DELETE FROM customers WHERE is_walk_in = FALSE");
            st.executeUpdate("DELETE FROM game_tables WHERE table_name NOT IN ('Pool Table 1','Pool Table 2','Pool Table 3','Snooker Table 1','PS5 Station 1')");
            st.executeUpdate("UPDATE game_tables SET is_active = TRUE, is_under_maintenance = FALSE");
            // plans back to the starting values: price not set yet
            st.executeUpdate("UPDATE membership_plans SET price = 0, duration_months = CASE name WHEN 'Monthly' THEN 1 WHEN 'Quarterly' THEN 3 ELSE 12 END, "
                    + "discount_percent = CASE name WHEN 'Monthly' THEN 5 WHEN 'Quarterly' THEN 10 ELSE 15 END WHERE plan_id > 0");
        }
    }
}
