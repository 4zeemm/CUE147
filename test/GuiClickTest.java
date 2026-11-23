import dao.*;
import model.*;
import service.*;
import ui.*;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * GUI click test: opens the REAL application, types into the real text boxes, clicks the real buttons,
 * answers the pop-up windows and then checks the result in MySQL (database cue147_test).
 * Every case is written to docs/test-results/gui-click-test-results.tsv with what really happened.
 * Run:  java -Dcue147.db=cue147_test -cp "target/classes:target/test-classes:target/lib/*" GuiClickTest
 */
public class GuiClickTest {
    static final List<String[]> results = new ArrayList<>();
    static final String SHOTS = "docs/screenshots";
    static int passed = 0, failed = 0;

    interface Step { String run() throws Exception; }

    // ------------------------------------------------------------------ small helpers
    static void sleep(int ms) { try { Thread.sleep(ms); } catch (InterruptedException e) { } }

    static void edt(Runnable r) throws Exception { SwingUtilities.invokeAndWait(r); }

    static <T> T onEdt(Callable<T> c) throws Exception {
        Object[] box = new Object[1];
        SwingUtilities.invokeAndWait(() -> { try { box[0] = c.call(); } catch (Exception e) { throw new RuntimeException(e); } });
        @SuppressWarnings("unchecked") T t = (T) box[0];
        return t;
    }

    static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }

    /** Runs one test case and records what happened. */
    static void test(String module, String name, String expected, Step step) {
        String actual; boolean ok;
        try { actual = step.run(); ok = true; }
        catch (Throwable t) {
            Throwable cause = t;
            while (cause.getCause() != null) cause = cause.getCause();   // show the real reason, not the wrapper
            actual = "FAILED: " + cause;
            ok = false;
            closeStrayDialogs();
        }
        String id = String.format("G%02d", results.size() + 1);
        results.add(new String[]{id, module, name, expected, actual, ok ? "PASS" : "FAIL"});
        if (ok) passed++; else failed++;
        System.out.println((ok ? "PASS  " : "FAIL  ") + id + " " + module + " - " + name + "  ->  " + actual);
    }

    // ------------------------------------------------------------------ finding components
    static <T extends Component> List<T> all(Container root, Class<T> type, boolean showingOnly) {
        List<T> list = new ArrayList<>();
        collect(root, type, showingOnly, list);
        return list;
    }

    static <T extends Component> void collect(Component c, Class<T> type, boolean showingOnly, List<T> list) {
        if (type.isInstance(c) && (!showingOnly || c.isShowing())) list.add(type.cast(c));
        if (c instanceof Container) for (Component child : ((Container) c).getComponents()) collect(child, type, showingOnly, list);
    }

    static AbstractButton findButton(Container root, String text) {
        for (AbstractButton b : all(root, AbstractButton.class, true)) {
            if (b.getText() != null && b.getText().trim().equals(text)) return b;
        }
        return null;
    }

    static void click(Container root, String text) throws Exception {
        AbstractButton b = findButton(root, text);
        check(b != null, "button '" + text + "' not found");
        check(b.isEnabled(), "button '" + text + "' is disabled");
        SwingUtilities.invokeLater(b::doClick);   // later, because a pop-up would block this thread
        sleep(300);
    }

    static <T extends Container> T panel(Class<T> type) throws Exception {
        List<T> list = all(mainFrame(), type, true);
        check(!list.isEmpty(), type.getSimpleName() + " is not visible");
        return list.get(0);
    }

    static MainFrame mainFrame() {
        for (Window w : Window.getWindows()) if (w instanceof MainFrame && w.isShowing()) return (MainFrame) w;
        return null;
    }

    static LoginFrame loginFrame() {
        for (Window w : Window.getWindows()) if (w instanceof LoginFrame && w.isShowing()) return (LoginFrame) w;
        return null;
    }

    static MainFrame waitForMainFrame() { for (int i = 0; i < 100; i++) { if (mainFrame() != null) return mainFrame(); sleep(50); } return null; }
    static LoginFrame waitForLogin() { for (int i = 0; i < 100; i++) { if (loginFrame() != null) return loginFrame(); sleep(50); } return null; }

    static void goTo(String screen) throws Exception { click(mainFrame(), screen); sleep(700); }

    static void setText(JTextComponent_ t, String text) throws Exception { edt(() -> t.set(text)); }

    // tiny wrapper so JTextField and JTextArea can be used the same way
    interface JTextComponent_ { void set(String s); }
    static JTextComponent_ field(javax.swing.text.JTextComponent c) { return s -> c.setText(s); }

    static void selectRow(JTable table, int row) throws Exception {
        edt(() -> table.setRowSelectionInterval(row, row));
        sleep(300);
    }

    static int rowWith(JTable table, String text) throws Exception {
        return onEdt(() -> {
            for (int r = 0; r < table.getRowCount(); r++)
                for (int c = 0; c < table.getColumnCount(); c++)
                    if (String.valueOf(table.getValueAt(r, c)).contains(text)) return r;
            return -1;
        });
    }

    // ------------------------------------------------------------------ pop-up windows
    /** Returns the newest pop-up window that is on the screen (a pop-up can open on top of the bill window). */
    static JDialog waitForDialog() {
        for (int i = 0; i < 400; i++) {
            JDialog newest = null;
            for (Window w : Window.getWindows()) if (w instanceof JDialog && w.isShowing()) newest = (JDialog) w;
            if (newest != null) return newest;
            sleep(15);
        }
        return null;
    }

    static String dialogText(JDialog d) throws Exception {
        return onEdt(() -> {
            StringBuilder sb = new StringBuilder();
            for (JLabel l : all(d, JLabel.class, false)) if (l.getText() != null) sb.append(l.getText().replaceAll("<[^>]+>", " ")).append(' ');
            return sb.toString().replaceAll("\\s+", " ").trim();
        });
    }

    /** Waits until a pop-up that contains a component of the given type is on the screen. */
    static JDialog waitForDialogWith(Class<? extends Component> type) {
        for (int i = 0; i < 400; i++) {
            for (Window w : Window.getWindows()) if (w instanceof JDialog && w.isShowing() && !all((JDialog) w, type, false).isEmpty()) return (JDialog) w;
            sleep(15);
        }
        return null;
    }

    /** Waits until a pop-up that has the named button is on the screen, returns its text, then presses the button. */
    static String answer(String buttonText) throws Exception {
        JDialog d = null;
        AbstractButton b = null;
        for (int i = 0; i < 400 && b == null; i++) {
            for (Window w : Window.getWindows()) {
                if (w instanceof JDialog && w.isShowing()) {
                    AbstractButton found = findButton((JDialog) w, buttonText);
                    if (found != null) { d = (JDialog) w; b = found; }
                }
            }
            if (b == null) sleep(15);
        }
        check(b != null, "no pop-up window with a '" + buttonText + "' button appeared");
        String text = dialogText(d);
        AbstractButton button = b;
        JDialog dialog = d;
        SwingUtilities.invokeLater(button::doClick);
        for (int i = 0; i < 200 && dialog.isShowing(); i++) sleep(15);
        sleep(250);
        return text;
    }

    static void shotDialog(String name) throws Exception {
        JDialog d = waitForDialog();
        if (d != null) shot(d, name);
    }

    static void closeStrayDialogs() {
        for (Window w : Window.getWindows()) {
            if (w instanceof JDialog && w.isShowing()) { try { edt(() -> ((JDialog) w).dispose()); } catch (Exception e) { } }
        }
    }

    static void shot(Window w, String name) throws Exception {
        edt(() -> {
            try {
                BufferedImage img = new BufferedImage(w.getWidth(), w.getHeight(), BufferedImage.TYPE_INT_RGB);
                Graphics2D g = img.createGraphics();
                ((RootPaneContainer) w).getLayeredPane().paint(g);
                g.dispose();
                ImageIO.write(img, "png", new File(SHOTS, name + ".png"));
            } catch (Exception e) { System.out.println("screenshot failed: " + e); }
        });
    }

    // ------------------------------------------------------------------ right-click menu
    /** Finds the card of a table on the Tables screen (the card has a label with the table name). */
    static RoundedPanel cardFor(Container panel, String tableName) throws Exception {
        return onEdt(() -> {
            for (RoundedPanel card : all(panel, RoundedPanel.class, true))
                for (JLabel l : all(card, JLabel.class, true))
                    if (tableName.equals(l.getText())) return card;
            return null;
        });
    }

    /** Sends a real right mouse button press to a component. */
    static void rightClick(Component c) throws Exception {
        edt(() -> c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(),
                InputEvent.BUTTON3_DOWN_MASK, 20, 20, 1, true, MouseEvent.BUTTON3)));
        sleep(400);
    }

    static JPopupMenu visiblePopup() {
        for (Window w : Window.getWindows()) for (JPopupMenu m : all(w, JPopupMenu.class, true)) return m;
        return null;
    }

    /** Clicks one item of the right-click menu that is on the screen. */
    static void clickPopup(String item) throws Exception {
        JPopupMenu menu = visiblePopup();
        check(menu != null, "right-click menu did not open");
        JMenuItem target = null;
        for (Component c : menu.getComponents()) if (c instanceof JMenuItem && item.equals(((JMenuItem) c).getText())) target = (JMenuItem) c;
        check(target != null, "menu item '" + item + "' not found");
        JMenuItem chosen = target;
        edt(() -> menu.setVisible(false));
        SwingUtilities.invokeLater(chosen::doClick);
        sleep(300);
    }

    /** Status word shown on a table card: AVAILABLE, OCCUPIED or MAINTENANCE. */
    static String cardStatus(Container panel, String tableName) throws Exception {
        RoundedPanel card = cardFor(panel, tableName);
        if (card == null) return "no card";
        return onEdt(() -> {
            for (JLabel l : all(card, JLabel.class, true)) {
                String t = l.getText() == null ? "" : l.getText().trim();
                if (t.equals("AVAILABLE") || t.equals("OCCUPIED") || t.equals("MAINTENANCE")) return t;
            }
            return "?";
        });
    }

    static void chooseCustomer(JDialog d, String name) throws Exception {
        edt(() -> { JComboBox<?> box = all(d, JComboBox.class, false).get(0); for (int i = 0; i < box.getItemCount(); i++) if (String.valueOf(box.getItemAt(i)).contains(name)) box.setSelectedIndex(i); });
    }

    // ------------------------------------------------------------------ the test
    public static void main(String[] args) throws Exception {
        Thread watchdog = new Thread(() -> { sleep(400000); System.out.println("FAIL  timeout"); System.exit(2); });
        watchdog.setDaemon(true);
        watchdog.start();
        new File(SHOTS).mkdirs();
        new File("docs/test-results").mkdirs();

        BackendTest.cleanTestDatabase();
        edt(Theme::install);
        CustomerDAO customerDAO = new CustomerDAO();
        GameTableDAO tableDAO = new GameTableDAO();
        BillDAO billDAO = new BillDAO();
        ExpenseDAO expenseDAO = new ExpenseDAO();
        SessionDAO sessionDAO = new SessionDAO();
        ReportService reports = new ReportService();
        LocalDate today = LocalDate.now();

        edt(() -> new LoginFrame().setVisible(true));
        LoginFrame login = waitForLogin();
        check(login != null, "login window did not open");
        sleep(500);
        shot(login, "01-login");

        // ================= LOGIN =================
        test("Login", "Login with empty fields", "Pop-up says the username is required", () -> {
            click(login, "LOGIN");
            String text = answer("OK");
            check(text.contains("required"), text);
            return "Pop-up: \"" + text + "\"";
        });
        JTextFieldPair f = new JTextFieldPair(login);
        test("Login", "Login with a wrong password", "Pop-up: wrong username or password", () -> {
            setText(field(f.user), "owner"); setText(field(f.pass), "wrong-password");
            click(login, "LOGIN");
            String text = answer("OK");
            check(text.contains("Wrong username or password"), text);
            return "Pop-up: \"" + text + "\"";
        });
        test("Login", "Wrong role chosen (Staff radio for the owner account)", "Pop-up says the account is an OWNER account", () -> {
            setText(field(f.pass), "owner123"); click(login, "Staff");
            click(login, "LOGIN");
            String text = answer("OK");
            check(text.contains("OWNER"), text);
            return "Pop-up: \"" + text + "\"";
        });
        test("Login", "Show password check box", "Password text becomes visible, then hidden again", () -> {
            click(login, "Show password");
            boolean visible = onEdt(() -> f.pass.getEchoChar() == 0);
            click(login, "Show password");
            boolean hidden = onEdt(() -> f.pass.getEchoChar() != 0);
            check(visible && hidden, "echo char did not change");
            return "Password shown, then hidden again";
        });
        test("Login", "Clear button", "Username and password boxes become empty", () -> {
            click(login, "Clear");
            String u = onEdt(() -> f.user.getText()); String p = onEdt(() -> new String(f.pass.getPassword()));
            check(u.isEmpty() && p.isEmpty(), "boxes not empty");
            return "Both boxes empty";
        });
        test("Login", "Forgot password button", "Information pop-up opens", () -> {
            click(login, "Forgot password?");
            String text = answer("OK");
            check(text.contains("Owner"), text);
            return "Pop-up: \"" + text.substring(0, Math.min(70, text.length())) + "...\"";
        });
        test("Login", "Database settings: Test Connection with the saved settings", "Pop-up: Connected to the database", () -> {
            click(login, "Database settings");
            JDialog d = waitForDialogWith(JPasswordField.class);
            check(d != null && d.getTitle().equals("Database settings"), "settings window did not open");
            String host = onEdt(() -> all(d, JTextField.class, false).get(0).getText());
            check(host.equals(database.DBConnection.getConfig().getHost()), "host box shows " + host);
            shot(d, "01b-database-settings");
            SwingUtilities.invokeLater(findButton(d, "Test Connection")::doClick);
            String text = answer("OK");
            check(text.startsWith("Connected to"), text);
            return "Host box shows " + host + "; pop-up: \"" + text + "\"";
        });
        test("Login", "Database settings: wrong password, then Cancel", "Clear message about the password; nothing is saved", () -> {
            JDialog d = waitForDialogWith(JPasswordField.class);
            check(d != null, "settings window is not open");
            JPasswordField pw = all(d, JPasswordField.class, false).get(0);
            edt(() -> pw.setText("wrong-password"));
            SwingUtilities.invokeLater(findButton(d, "Test Connection")::doClick);
            String text = answer("OK");
            check(text.contains("user name or password is wrong"), text);
            SwingUtilities.invokeLater(findButton(d, "Cancel")::doClick);
            for (int i = 0; i < 100 && d.isShowing(); i++) sleep(20);
            check(!d.isShowing(), "window did not close");
            check(database.DBConnection.isDatabaseReachable(), "settings were changed");
            return "Pop-up: \"" + text + "\"; Cancel closed the window and the app still uses the old settings";
        });
        test("Login", "Login as Owner with the correct password", "Main window opens as Club Owner", () -> {
            setText(field(f.user), "owner"); setText(field(f.pass), "owner123"); click(login, "Owner");
            click(login, "LOGIN");
            MainFrame mf = waitForMainFrame();
            check(mf != null, "main window did not open");
            sleep(1000);
            return "Main window opened: \"" + mf.getTitle() + "\"";
        });
        MainFrame frame = mainFrame();

        // ================= NAVIGATION =================
        for (String screen : new String[]{"Dashboard", "Tables", "Customers", "Bookings", "Billing", "Membership", "Expenses", "Reports", "Staff", "Settings"}) {
            test("Navigation", "Sidebar button: " + screen, screen + " screen is shown", () -> {
                goTo(screen);
                boolean shown = onEdt(() -> all(frame, JLabel.class, true).stream().anyMatch(l -> ("Viewing: " + screen).equals(l.getText())));
                check(shown, "status bar does not say Viewing: " + screen);
                return "Status bar shows \"Viewing: " + screen + "\"";
            });
        }

        // ================= CUSTOMERS =================
        goTo("Customers");
        CustomersPanel cp = panel(CustomersPanel.class);
        List<JTextField> boxes = all(cp, JTextField.class, true);   // [search, name, phone]
        JTextField searchBox = boxes.get(0), nameBox = boxes.get(1), phoneBox = boxes.get(2);
        JComboBox<?> genderBox = all(cp, JComboBox.class, true).get(0);
        JTable customerTable = all(cp, JTable.class, true).get(0);
        test("Customers", "Add customer with a bad phone number", "Pop-up: phone must be exactly 10 digits; nothing saved", () -> {
            int before = customerDAO.findAll().size();
            setText(field(nameBox), "Rahul Sharma"); setText(field(phoneBox), "12345");
            click(cp, "Add");
            String text = answer("OK");
            check(text.contains("10 digits"), text);
            check(customerDAO.findAll().size() == before, "a customer was saved");
            return "Pop-up: \"" + text + "\"; customer count unchanged (" + before + ")";
        });
        test("Customers", "Add customer with an empty name", "Pop-up: name is required", () -> {
            setText(field(nameBox), ""); setText(field(phoneBox), "9876500011");
            click(cp, "Add");
            String text = answer("OK");
            check(text.contains("required"), text);
            return "Pop-up: \"" + text + "\"";
        });
        test("Customers", "Add a valid customer (Rahul Sharma, Men)", "Customer saved in MySQL and shown in the table", () -> {
            setText(field(nameBox), "Rahul Sharma"); setText(field(phoneBox), "9876500011");
            click(cp, "Add");
            String text = answer("OK");
            check(text.contains("registered"), text);
            check(customerDAO.search("9876500011").size() == 1, "not in MySQL");
            check(rowWith(customerTable, "Rahul Sharma") >= 0, "not in the table");
            return "Pop-up: \"" + text + "\"; found in MySQL and in the table";
        });
        test("Customers", "Add a second customer (Priya Nair, Ladies)", "Customer saved with gender Ladies", () -> {
            setText(field(nameBox), "Priya Nair"); setText(field(phoneBox), "9876500022");
            edt(() -> genderBox.setSelectedItem("Ladies"));
            click(cp, "Add"); answer("OK");
            Customer c = customerDAO.search("9876500022").get(0);
            check(c.getGender().equals(Customer.LADIES), "gender is " + c.getGender());
            return "Saved: " + c.getName() + ", gender " + c.getGenderLabel();
        });
        test("Customers", "Add customer with a phone number that already exists", "Pop-up: phone number is already registered", () -> {
            setText(field(nameBox), "Another Person"); setText(field(phoneBox), "9876500011");
            click(cp, "Add");
            String text = answer("OK");
            check(text.contains("already registered"), text);
            check(customerDAO.search("9876500011").size() == 1, "duplicate saved");
            return "Pop-up: \"" + text + "\"; still only one row in MySQL";
        });
        test("Customers", "Search for 'Rahul'", "Table shows only Rahul Sharma", () -> {
            setText(field(searchBox), "Rahul");
            click(cp, "Search");
            int rows = onEdt(() -> customerTable.getRowCount());
            check(rows == 1, "rows = " + rows);
            return "Table shows " + rows + " row: " + onEdt(() -> String.valueOf(customerTable.getValueAt(0, 1)));
        });
        test("Customers", "Show All button", "All customers are shown again", () -> {
            click(cp, "Show All");
            int rows = onEdt(() -> customerTable.getRowCount());
            check(rows == 4, "rows = " + rows);
            return "Table shows " + rows + " rows (2 walk-in guests + 2 customers)";
        });
        test("Customers", "History button without selecting a customer", "Pop-up asks to select a customer first", () -> {
            edt(() -> customerTable.clearSelection());
            click(cp, "History");
            String text = answer("OK");
            check(text.contains("Select a customer"), text);
            return "Pop-up: \"" + text + "\"";
        });
        test("Customers", "History of a customer", "History window opens and can be closed", () -> {
            selectRow(customerTable, rowWith(customerTable, "Rahul Sharma"));
            click(cp, "History");
            JDialog d = waitForDialog();
            check(d != null, "history window did not open");
            String text = dialogText(d);
            check(text.contains("Rahul Sharma"), text);
            answer("Close");
            return "History window opened for Rahul Sharma and was closed";
        });
        test("Customers", "Delete a new customer who never played (owner)", "Question Yes: customer removed from MySQL and from the table", () -> {
            setText(field(nameBox), "Temp Person"); setText(field(phoneBox), "9876500099");
            edt(() -> genderBox.setSelectedItem("Men"));
            click(cp, "Add"); answer("OK");
            check(customerDAO.search("9876500099").size() == 1, "not added");
            selectRow(customerTable, rowWith(customerTable, "Temp Person"));
            click(cp, "Delete");
            String question = answer("Yes");
            check(question.contains("Delete customer Temp Person"), question);
            sleep(400);
            check(customerDAO.search("9876500099").isEmpty(), "still in MySQL");
            check(rowWith(customerTable, "Temp Person") < 0, "still in the table");
            return "Question: \"" + question + "\"; after Yes the customer is gone from MySQL and the table";
        });
        test("Customers", "Delete the walk-in guest row", "Pop-up: the walk-in guest cannot be deleted", () -> {
            selectRow(customerTable, rowWith(customerTable, "Walk-in"));
            click(cp, "Delete");
            String text = answer("OK");
            check(text.contains("cannot be deleted"), text);
            return "Pop-up: \"" + text + "\"";
        });
        edt(() -> customerTable.clearSelection());
        click(cp, "Clear");
        shot(frame, "04-customers");

        // ================= TABLES / SESSIONS / PAYMENTS =================
        goTo("Tables");
        TablesPanel tp = panel(TablesPanel.class);
        test("Sessions", "Start Game on Pool Table 1 for Rahul Sharma", "Table becomes OCCUPIED, one active session in MySQL", () -> {
            click(tp, "Start Game");
            JDialog d = waitForDialog();
            check(d != null, "no player pop-up");
            shot(d, "05-start-game");
            edt(() -> { JComboBox<?> box = all(d, JComboBox.class, false).get(0); for (int i = 0; i < box.getItemCount(); i++) if (String.valueOf(box.getItemAt(i)).contains("Rahul Sharma")) box.setSelectedIndex(i); });
            answer("OK");
            String info = answer("OK");
            check(info.contains("Game started"), info);
            check(sessionDAO.countActive() == 1, "active sessions = " + sessionDAO.countActive());
            GameTable t = tableDAO.findAll().stream().filter(x -> x.getName().equals("Pool Table 1")).findFirst().get();
            check(t.isOccupied() && t.getActivePlayer().equals("Rahul Sharma"), "table is " + t.getStatus());
            return "Pop-up: \"" + info + "\"; MySQL: Pool Table 1 = OCCUPIED, player Rahul Sharma, 1 active session";
        });
        sleep(2200);
        test("Sessions", "Running timer on the table card", "Card shows 'Running: 00:00:xx' that counts up", () -> {
            boolean running = onEdt(() -> all(tp, JLabel.class, true).stream().anyMatch(l -> l.getText() != null && l.getText().startsWith("Running: 00:00:")));
            check(running, "no running timer label");
            return "Card shows a live timer (Running: 00:00:xx)";
        });
        shot(frame, "03-tables-running");
        test("Sessions", "Dashboard shows the active session", "Active Sessions = 1, Occupied = 1", () -> {
            DashboardStats s = reports.loadDashboardStats();
            check(s.activeSessions == 1 && s.occupiedTables == 1 && s.availableTables == s.totalTables - 1, "stats wrong");
            return "Dashboard query: active " + s.activeSessions + ", occupied " + s.occupiedTables + ", available " + s.availableTables + " of " + s.totalTables;
        });
        test("Payments", "End Game, then pay by Cash", "Bill Rs. 270 (1-hour minimum, Pool Men) saved as PAID by CASH", () -> {
            click(tp, "End Game");
            answer("Yes");
            JDialog bill = waitForDialog();
            check(bill != null, "bill window did not open");
            sleep(400);
            shot(bill, "06-bill");
            AbstractButton cash = findButton(bill, "Pay Cash");
            check(cash != null, "no Pay Cash button");
            SwingUtilities.invokeLater(cash::doClick);
            String payText = answer("OK");
            check(payText.contains("Payment of"), payText);
            answer("Close");
            Bill b = billDAO.search("Rahul", false).get(0);
            check(b.isPaid() && b.getPaymentMethod().equals("CASH") && b.getFinalAmount().compareTo(new BigDecimal("270")) == 0, b.getPaymentLabel() + " " + b.getFinalAmount());
            check(sessionDAO.countActive() == 0, "session still active");
            return "Pop-up: \"" + payText + "\"; MySQL: bill " + b.getBillNumber() + " = Rs. " + b.getFinalAmount() + ", " + b.getPaymentLabel() + " (" + b.getPaymentMethod() + "), received by " + b.getReceivedBy();
        });
        test("Sessions", "Table is free again after the game", "Pool Table 1 is AVAILABLE", () -> {
            GameTable t = tableDAO.findAll().stream().filter(x -> x.getName().equals("Pool Table 1")).findFirst().get();
            check(!t.isOccupied(), "still occupied");
            return "MySQL: Pool Table 1 = " + t.getStatus();
        });
        test("Payments", "Second game: Priya (Ladies) pays by UPI", "Bill Rs. 220 (Pool Ladies) saved as PAID by UPI", () -> {
            click(tp, "Start Game");
            JDialog d = waitForDialog();
            edt(() -> { JComboBox<?> box = all(d, JComboBox.class, false).get(0); for (int i = 0; i < box.getItemCount(); i++) if (String.valueOf(box.getItemAt(i)).contains("Priya Nair")) box.setSelectedIndex(i); });
            answer("OK"); answer("OK");
            click(tp, "End Game"); answer("Yes");
            JDialog bill = waitForDialog();
            SwingUtilities.invokeLater(findButton(bill, "Pay UPI")::doClick);
            answer("OK"); answer("Close");
            Bill b = billDAO.search("Priya", false).get(0);
            check(b.isPaid() && b.getPaymentMethod().equals("UPI") && b.getFinalAmount().compareTo(new BigDecimal("220")) == 0, b.getPaymentLabel() + " " + b.getFinalAmount());
            return "MySQL: bill " + b.getBillNumber() + " = Rs. " + b.getFinalAmount() + ", PAID (UPI)";
        });
        test("Tables", "Add Table (Pool Table 9)", "New table saved in MySQL", () -> {
            click(tp, "Add Table");
            JDialog d = waitForDialog();
            check(d != null, "no dialog");
            edt(() -> all(d, JTextField.class, false).get(0).setText("Pool Table 9"));
            AbstractButton add = findButton(d, "Add");
            SwingUtilities.invokeLater(add::doClick);
            for (int i = 0; i < 200 && d.isShowing(); i++) sleep(15);
            sleep(400);
            check(tableDAO.findAll().stream().anyMatch(t -> t.getName().equals("Pool Table 9")), "not in MySQL");
            return "MySQL now has Pool Table 9 (" + tableDAO.findAll().size() + " tables)";
        });
        JTable overview = all(tp, JTable.class, true).get(0);
        test("Tables", "Remove Table (Pool Table 9, no history)", "Confirm Yes: table deleted from MySQL", () -> {
            selectRow(overview, rowWith(overview, "Pool Table 9"));
            click(tp, "Remove Table"); answer("Yes");
            sleep(400);
            check(tableDAO.findAll().stream().noneMatch(t -> t.getName().equals("Pool Table 9")), "still in MySQL");
            return "Table removed; MySQL has " + tableDAO.findAll().size() + " tables again";
        });
        test("Tables", "Maintenance on, then off (Pool Table 3)", "Table shows MAINTENANCE, then AVAILABLE again", () -> {
            selectRow(overview, rowWith(overview, "Pool Table 3"));
            click(tp, "Maintenance");
            GameTable t = tableDAO.findAll().stream().filter(x -> x.getName().equals("Pool Table 3")).findFirst().get();
            check(t.isUnderMaintenance(), "flag not set");
            boolean startDisabled = onEdt(() -> { for (AbstractButton b : all(tp, AbstractButton.class, true)) if ("Start Game".equals(b.getText()) && !b.isEnabled()) return true; return false; });
            check(startDisabled, "Start Game is not disabled");
            shot(frame, "03b-tables-maintenance");
            click(tp, "Maintenance");
            GameTable t2 = tableDAO.findAll().stream().filter(x -> x.getName().equals("Pool Table 3")).findFirst().get();
            check(!t2.isUnderMaintenance(), "flag not cleared");
            return "Status MAINTENANCE (Start Game disabled), then AVAILABLE again";
        });

        // ================= BILLING =================
        goTo("Billing");
        BillingPanel bp = panel(BillingPanel.class);
        JTable billTable = all(bp, JTable.class, true).get(0);
        test("Billing", "Bill list shows both bills", "2 rows, newest first", () -> {
            int rows = onEdt(() -> billTable.getRowCount());
            check(rows == 2, "rows = " + rows);
            return "Billing table shows " + rows + " bills";
        });
        shot(frame, "07-billing");
        test("Billing", "Cancel Bill (refund Priya's paid bill)", "Bill CANCELLED, payment REFUNDED, revenue drops by Rs. 220", () -> {
            BigDecimal before = billDAO.revenueBetween(today, today);
            selectRow(billTable, rowWith(billTable, "Priya"));
            click(bp, "Cancel Bill");
            JDialog d = waitForDialog();
            edt(() -> all(d, JTextField.class, false).get(0).setText("Customer changed mind"));
            answer("OK");
            sleep(400);
            Bill b = billDAO.search("Priya", false).get(0);
            check(b.isCancelled() && b.getPaymentLabel().equals("REFUNDED"), b.getPaymentLabel());
            BigDecimal after = billDAO.revenueBetween(today, today);
            check(before.subtract(after).compareTo(new BigDecimal("220")) == 0, before + " -> " + after);
            return "MySQL: bill CANCELLED, payment " + b.getPaymentLabel() + ", reason kept; revenue " + before + " -> " + after;
        });
        test("Billing", "Search bills by customer name", "Only Rahul's bill is listed", () -> {
            setText(field(all(bp, JTextField.class, true).get(0)), "Rahul");
            click(bp, "Search");
            int rows = onEdt(() -> billTable.getRowCount());
            check(rows == 1, "rows = " + rows);
            setText(field(all(bp, JTextField.class, true).get(0)), "");
            click(bp, "Refresh");
            return "Search shows " + rows + " bill; Refresh shows all again";
        });
        test("Billing", "Cancel Bill without selecting a bill", "Pop-up asks to select a bill first", () -> {
            edt(() -> billTable.clearSelection());
            click(bp, "Cancel Bill");
            String text = answer("OK");
            check(text.contains("Select a bill"), text);
            return "Pop-up: \"" + text + "\"";
        });

        // ================= EXPENSES =================
        goTo("Expenses");
        ExpensesPanel ep = panel(ExpensesPanel.class);
        JTable expenseTable = all(ep, JTable.class, true).get(0);
        test("Expenses", "Add expense with amount 0", "Pop-up: amount must be more than zero", () -> {
            click(ep, "Add Expense");
            String text = answer("OK");
            check(text.contains("more than zero"), text);
            return "Pop-up: \"" + text + "\"";
        });
        test("Expenses", "Add expense (Rent, Rs. 1500, Cash)", "Expense saved in MySQL and listed", () -> {
            edt(() -> { all(ep, JSpinner.class, true).get(0).setValue(1500.0); all(ep, JTextArea.class, true).get(0).setText("November rent"); });
            click(ep, "Add Expense");
            String text = answer("OK");
            check(text.contains("Expense saved"), text);
            check(expenseDAO.findAll().size() == 1, "not in MySQL");
            check(onEdt(() -> expenseTable.getRowCount()) == 1, "not in the table");
            return "Pop-up: \"" + text + "\"; MySQL has 1 expense";
        });
        test("Expenses", "Edit the expense (amount 2000)", "MySQL amount changes to 2000.00", () -> {
            selectRow(expenseTable, 0);
            click(ep, "Edit");
            JDialog d = waitForDialog();
            check(d != null, "no edit window");
            edt(() -> all(d, JSpinner.class, false).get(0).setValue(2000.0));
            AbstractButton save = findButton(d, "Save");
            SwingUtilities.invokeLater(save::doClick);
            for (int i = 0; i < 200 && d.isShowing(); i++) sleep(15);
            sleep(400);
            Expense e = expenseDAO.findAll().get(0);
            check(e.getAmount().compareTo(new BigDecimal("2000")) == 0, "amount " + e.getAmount());
            return "MySQL amount = " + e.getAmount();
        });
        test("Dashboard", "Dashboard figures after the sales and the expense", "Revenue 270, today's expenses 2000, profit = 270 - 2000", () -> {
            DashboardStats s = reports.loadDashboardStats();
            check(s.todayRevenue.compareTo(new BigDecimal("270")) == 0, "revenue " + s.todayRevenue);
            check(s.todayExpenses.compareTo(new BigDecimal("2000")) == 0, "expenses " + s.todayExpenses);
            check(s.getEstimatedProfit().compareTo(new BigDecimal("270").subtract(new BigDecimal("2000"))) == 0, "profit " + s.getEstimatedProfit());
            return "Revenue " + s.todayRevenue + ", expenses " + s.todayExpenses + ", estimated profit " + s.getEstimatedProfit();
        });
        shot(frame, "08-expenses");
        goTo("Dashboard"); sleep(1200); shot(frame, "02-dashboard");
        goTo("Expenses");
        test("Expenses", "Delete: press Cancel in the question", "Nothing is deleted", () -> {
            selectRow(expenseTable, 0);
            click(ep, "Delete");
            String text = answer("Cancel");
            check(text.contains("Are you sure"), text);
            check(expenseDAO.findAll().size() == 1, "expense was deleted");
            return "Question: \"" + text.substring(0, 40) + "...\"; after Cancel MySQL still has 1 expense";
        });
        test("Expenses", "Delete: press Yes in the question", "Expense removed from MySQL and from the list", () -> {
            selectRow(expenseTable, 0);
            click(ep, "Delete"); answer("Yes"); sleep(400);
            check(expenseDAO.findAll().isEmpty(), "still in MySQL");
            check(onEdt(() -> expenseTable.getRowCount()) == 0, "still in the list");
            DashboardStats s = reports.loadDashboardStats();
            check(s.todayExpenses.signum() == 0, "dashboard still shows " + s.todayExpenses);
            return "MySQL has 0 expenses; dashboard today's expenses = " + s.todayExpenses;
        });

        // ================= BOOKINGS / MEMBERSHIP / SETTINGS / REPORTS =================
        goTo("Bookings");
        BookingsPanel bk = panel(BookingsPanel.class);
        test("Bookings", "Book a table (defaults: today 19:00, 1 hour)", "Booking saved in MySQL as CONFIRMED", () -> {
            click(bk, "Book Table");
            String text = answer("OK");
            check(text.contains("Booking confirmed"), text);
            check(new BookingDAO().findAll().size() == 1, "not in MySQL");
            return "Pop-up: \"" + text.substring(0, 20) + "...\"; MySQL has 1 booking";
        });
        shot(frame, "09-bookings");
        test("Bookings", "Cancel the booking", "Booking status becomes CANCELLED", () -> {
            selectRow(all(bk, JTable.class, true).get(0), 0);
            click(bk, "Cancel Booking"); answer("Yes"); sleep(400);
            check(new BookingDAO().findAll().get(0).getStatus().equals("CANCELLED"), "status not cancelled");
            return "MySQL booking status = CANCELLED";
        });
        JSpinner bookTime = onEdt(() -> { for (JSpinner sp1 : all(bk, JSpinner.class, true)) if (sp1.getEditor() instanceof JSpinner.DateEditor) return sp1; return null; });
        JComboBox<?> bookCustomer = all(bk, JComboBox.class, true).get(0);
        JComboBox<?> bookTable = all(bk, JComboBox.class, true).get(1);
        test("Bookings", "Book 19:00, then try 19:30 on the same table", "Second booking refused: it overlaps the first one", () -> {
            edt(() -> bookTime.setValue(Theme.timeSpinner(19, 0).getValue()));
            click(bk, "Book Table"); answer("OK");
            edt(() -> bookTime.setValue(Theme.timeSpinner(19, 30).getValue()));
            click(bk, "Book Table");
            String text = answer("OK");
            check(text.contains("overlaps"), text);
            long confirmed = new BookingDAO().findAll().stream().filter(b -> b.getStatus().equals("CONFIRMED")).count();
            check(confirmed == 1, "confirmed bookings = " + confirmed);
            JTable list = all(bk, JTable.class, true).get(0);
            selectRow(list, rowWith(list, "CONFIRMED"));
            click(bk, "Cancel Booking"); answer("Yes"); sleep(300);
            return "Pop-up: \"" + text + "\"; MySQL still has only 1 confirmed booking (then cancelled)";
        });
        test("Bookings", "Priya books Pool Table 1 from now; Rahul tries to play there", "Start Game for Rahul is refused: the table is booked for Priya", () -> {
            edt(() -> {
                java.util.Calendar cal = java.util.Calendar.getInstance();
                cal.set(java.util.Calendar.SECOND, 0);
                bookTime.setValue(cal.getTime());
                for (int i = 0; i < bookCustomer.getItemCount(); i++) if (String.valueOf(bookCustomer.getItemAt(i)).contains("Priya Nair")) bookCustomer.setSelectedIndex(i);
                for (int i = 0; i < bookTable.getItemCount(); i++) if (String.valueOf(bookTable.getItemAt(i)).contains("Pool Table 1")) bookTable.setSelectedIndex(i);
            });
            click(bk, "Book Table"); answer("OK");
            goTo("Tables");
            TablesPanel tb = panel(TablesPanel.class);
            rightClick(cardFor(tb, "Pool Table 1"));
            clickPopup("Start Game");
            JDialog d = waitForDialog();
            chooseCustomer(d, "Rahul Sharma");
            answer("OK");
            String text = answer("OK");
            check(text.contains("booked for Priya Nair"), text);
            check(cardStatus(tb, "Pool Table 1").equals("AVAILABLE") && sessionDAO.countActive() == 0, "a game was started");
            goTo("Bookings");
            JTable list = all(bk, JTable.class, true).get(0);
            selectRow(list, rowWith(list, "CONFIRMED"));
            click(bk, "Cancel Booking"); answer("Yes"); sleep(300);
            return "Pop-up: \"" + text + "\"; no game started (booking cancelled afterwards)";
        });
        goTo("Membership");
        MembershipPanel mp = panel(MembershipPanel.class);
        test("Membership", "Add a Monthly member", "Membership saved: ACTIVE, 5% discount", () -> {
            click(mp, "Add Member");
            String text = answer("OK");
            check(text.contains("member"), text);
            Membership m = new MembershipDAO().findAll().get(0);
            check(m.getDisplayStatus().equals("ACTIVE") && m.getDiscountPercent().compareTo(new BigDecimal("5")) == 0, m.getDisplayStatus());
            return "Pop-up: \"" + text + "\"; MySQL: " + m.getCustomerName() + " " + m.getDisplayStatus() + ", " + m.getDiscountPercent() + "% until " + m.getExpiryDate();
        });
        shot(frame, "10-membership");
        goTo("Settings");
        SettingsPanel sp = panel(SettingsPanel.class);
        test("Settings", "Change Pool price for Men to Rs. 280", "Price saved in MySQL", () -> {
            edt(() -> all(sp, JSpinner.class, true).get(0).setValue(280.0));
            click(sp, "Save Prices"); answer("OK");
            BigDecimal rate = new PriceDAO().getRate("POOL", "MEN");
            check(rate.compareTo(new BigDecimal("280")) == 0, "rate " + rate);
            return "MySQL: Pool / Men = Rs. " + rate;
        });
        test("Settings", "Change the price back to Rs. 270", "Price saved in MySQL", () -> {
            edt(() -> all(sp, JSpinner.class, true).get(0).setValue(270.0));
            click(sp, "Save Prices"); answer("OK");
            BigDecimal rate = new PriceDAO().getRate("POOL", "MEN");
            check(rate.compareTo(new BigDecimal("270")) == 0, "rate " + rate);
            return "MySQL: Pool / Men = Rs. " + rate;
        });
        shot(frame, "11-settings");
        JTabbedPane settingsTabs = all(sp, JTabbedPane.class, true).get(0);
        test("Settings", "Appearance: Midnight Blue, text size 16, Apply", "Look saved (used the next time CUE 147 starts)", () -> {
            edt(() -> settingsTabs.setSelectedIndex(2));
            sleep(300);
            edt(() -> {
                for (JComboBox<?> box : all(sp, JComboBox.class, true)) box.setSelectedItem("Midnight Blue");
                all(sp, JSlider.class, true).get(0).setValue(16);
            });
            shot(frame, "11b-settings-appearance");
            click(sp, "Apply");
            String text = answer("OK");
            check(text.contains("Midnight Blue") && text.contains("16"), text);
            check(util.AppSettings.getTheme().equals("Midnight Blue") && util.AppSettings.getFontSize() == 16, "not saved");
            return "Pop-up: \"" + text + "\"; saved theme = " + util.AppSettings.getTheme() + ", size = " + util.AppSettings.getFontSize();
        });
        test("Settings", "Appearance: switch notifications OFF", "Top-bar button says 'Notifications (off)' and the list is not shown", () -> {
            click(sp, "Notifications: ON");
            check(!util.AppSettings.isNotificationsOn(), "setting not saved");
            AbstractButton n = findButton(frame, "Notifications (off)");
            check(n != null, "top-bar button did not change");
            SwingUtilities.invokeLater(n::doClick);
            String text = answer("OK");
            check(text.contains("switched off"), text);
            return "Top bar: \"Notifications (off)\"; pop-up: \"" + text + "\"";
        });
        test("Settings", "Appearance: switch notifications ON again", "Top-bar button shows the count again", () -> {
            click(sp, "Notifications: OFF");
            check(util.AppSettings.isNotificationsOn(), "setting not saved");
            String label = onEdt(() -> all(frame, AbstractButton.class, true).stream().map(AbstractButton::getText)
                    .filter(t -> t != null && t.startsWith("Notifications (")).findFirst().orElse("none"));
            check(!label.equals("Notifications (off)") && !label.equals("none"), label);
            return "Top bar: \"" + label + "\"";
        });
        test("Settings", "Club Settings: save the club name and phone", "Saved; printed at the top of every bill", () -> {
            edt(() -> settingsTabs.setSelectedIndex(3));
            sleep(300);
            List<JTextField> clubBoxes = all(sp, JTextField.class, true);
            edt(() -> { clubBoxes.get(0).setText("CUE 147 Test Club"); clubBoxes.get(1).setText("9123456789"); });
            click(sp, "Save");
            String text = answer("OK");
            check(util.AppSettings.getClubName().equals("CUE 147 Test Club") && util.AppSettings.getClubPhone().equals("9123456789"), "not saved");
            return "Pop-up: \"" + text + "\"; saved club name = " + util.AppSettings.getClubName();
        });
        test("Billing", "View / Print Bill: print the bill to a file", "The bill shows the new club name; a PostScript print file is made", () -> {
            edt(() -> settingsTabs.setSelectedIndex(0));
            goTo("Billing");
            BillingPanel bpp = panel(BillingPanel.class);
            JTable bills = all(bpp, JTable.class, true).get(0);
            selectRow(bills, rowWith(bills, "Rahul"));
            click(bpp, "View / Print Bill");
            JDialog d = waitForDialogWith(JTextArea.class);
            check(d != null, "bill window did not open");
            JTextArea area = all(d, JTextArea.class, false).get(0);
            String receipt = onEdt(area::getText);
            check(receipt.contains("CUE 147 TEST CLUB") && receipt.contains("9123456789"), "club name not on the bill");
            File ps = new File("docs/test-results/sample-bill-print.ps");
            long size = printToFile(area, ps, new File(SHOTS, "14-bill-print-preview.png"));
            String head = new String(java.nio.file.Files.readAllBytes(ps.toPath()), 0, 4, "ISO-8859-1");
            check(head.equals("%!PS") && size > 1000, "print file wrong: " + head + " " + size);
            answer("Close");
            return "Bill shows \"CUE 147 TEST CLUB\"; printed to " + ps.getPath() + " (" + size + " bytes, starts with %!PS)";
        });
        goTo("Reports");
        test("Reports", "Daily Report button", "Pop-up with today's revenue, games and busiest table", () -> {
            click(panel(ReportsPanel.class), "Daily Report");
            String text = answer("OK");
            check(text.contains("DAILY REPORT") && text.contains("Revenue"), text);
            return "Pop-up: \"" + text + "\"";
        });
        goTo("Reports"); sleep(500); shot(frame, "12-reports");
        goTo("Staff"); sleep(500); shot(frame, "13-staff");
        StaffPanel stp = panel(StaffPanel.class);
        test("Staff", "Change My Password with a wrong current password", "Pop-up: the current password is wrong; nothing changes", () -> {
            click(stp, "Change My Password");
            JDialog d = waitForDialogWith(JPasswordField.class);
            check(d != null, "password window did not open");
            List<JPasswordField> p = all(d, JPasswordField.class, false);
            edt(() -> { p.get(0).setText("wrong-one"); p.get(1).setText("owner456"); p.get(2).setText("owner456"); });
            shot(d, "13b-change-password");
            answer("OK");
            String text = answer("OK");
            check(text.contains("current password is wrong"), text);
            check(new AuthService().login("owner", "owner123") != null, "password changed");
            return "Pop-up: \"" + text + "\"; owner123 still works";
        });
        test("Staff", "Change My Password (owner123 -> owner456)", "New password works, old one does not", () -> {
            click(stp, "Change My Password");
            JDialog d = waitForDialogWith(JPasswordField.class);
            List<JPasswordField> p = all(d, JPasswordField.class, false);
            edt(() -> { p.get(0).setText("owner123"); p.get(1).setText("owner456"); p.get(2).setText("owner456"); });
            answer("OK");
            String text = answer("OK");
            check(text.contains("has been changed"), text);
            AuthService auth = new AuthService();
            User u = auth.login("owner", "owner456");
            boolean oldRefused;
            try { auth.login("owner", "owner123"); oldRefused = false; } catch (exception.BusinessException e) { oldRefused = true; }
            check(oldRefused, "old password still works");
            auth.changeOwnPassword(u, "owner456", "owner123", "owner123");   // put it back for the next tests
            return "Pop-up: \"" + text + "\"; owner456 logs in, owner123 refused (then changed back)";
        });
        // ================= RIGHT-CLICK MENU AND CORRECTIONS (Tables, Billing) =================
        goTo("Tables");
        TablesPanel tpc = panel(TablesPanel.class);
        test("Right-click menu", "Right-click a table card (Pool Table 2)", "Menu opens with Start Game, End Game, Cancel Game, Adjust Start Time, View Details", () -> {
            RoundedPanel card = cardFor(tpc, "Pool Table 2");
            check(card != null, "card not found");
            rightClick(card);
            JPopupMenu menu = visiblePopup();
            check(menu != null, "menu did not open");
            List<String> items = onEdt(() -> { List<String> list = new ArrayList<>(); for (Component c : menu.getComponents()) if (c instanceof JMenuItem) list.add(((JMenuItem) c).getText()); return list; });
            for (String need : new String[]{"Start Game", "End Game", "Cancel Game (started by mistake)", "Adjust Start Time", "View Details"}) check(items.contains(need), need + " missing");
            edt(() -> menu.setVisible(false));
            return "Menu items: " + items;
        });
        test("Right-click menu", "Right-click > View Details", "Pop-up with the table name, type and prices", () -> {
            rightClick(cardFor(tpc, "Pool Table 2"));
            clickPopup("View Details");
            String text = answer("OK");
            check(text.contains("Pool Table 2") && text.contains("Type: Pool"), text);
            return "Pop-up: \"" + text.substring(0, Math.min(75, text.length())) + "...\"";
        });
        test("Right-click menu", "Right-click > Start Game (Pool Table 2, Rahul Sharma)", "Table becomes OCCUPIED", () -> {
            rightClick(cardFor(tpc, "Pool Table 2"));
            clickPopup("Start Game");
            JDialog d = waitForDialog();
            chooseCustomer(d, "Rahul Sharma");
            answer("OK"); answer("OK");
            check(cardStatus(tpc, "Pool Table 2").equals("OCCUPIED"), "card is " + cardStatus(tpc, "Pool Table 2"));
            return "Card shows OCCUPIED; MySQL active sessions = " + sessionDAO.countActive();
        });
        test("Corrections", "Right-click > Adjust Start Time (20 minutes earlier)", "Start time saved 20 minutes earlier; timer shows about 00:20", () -> {
            rightClick(cardFor(tpc, "Pool Table 2"));
            clickPopup("Adjust Start Time");
            JDialog d = waitForDialogWith(JSpinner.class);
            check(d != null, "no time window");
            java.util.Date earlier = new java.util.Date(System.currentTimeMillis() - 20 * 60 * 1000L);
            edt(() -> all(d, JSpinner.class, false).get(0).setValue(earlier));
            answer("OK");
            sleep(1500);
            GameSession s = sessionDAO.findActive().stream().filter(x -> x.getTableName().equals("Pool Table 2")).findFirst().get();
            long minutes = s.getMinutes(java.time.LocalDateTime.now());
            check(minutes >= 19 && minutes <= 21, "minutes = " + minutes);
            RoundedPanel timerCard = cardFor(tpc, "Pool Table 2");
            boolean timer = onEdt(() -> all(timerCard, JLabel.class, true).stream().anyMatch(l -> l.getText() != null && (l.getText().startsWith("Running: 00:19") || l.getText().startsWith("Running: 00:20") || l.getText().startsWith("Running: 00:21"))));
            check(timer, "card timer not about 20 minutes");
            return "MySQL start time is " + minutes + " minutes ago; card timer shows about 00:20";
        });
        test("Corrections", "Right-click > Cancel Game (started by mistake)", "Game CANCELLED, no bill made, table AVAILABLE", () -> {
            int billsBefore = billDAO.search("", false).size();
            rightClick(cardFor(tpc, "Pool Table 2"));
            clickPopup("Cancel Game (started by mistake)");
            JDialog d = waitForDialog();
            check(d != null, "no reason window");
            edt(() -> all(d, JTextField.class, false).get(0).setText("Started on the wrong table"));
            answer("OK");
            sleep(500);
            check(billDAO.search("", false).size() == billsBefore, "a bill was made");
            check(cardStatus(tpc, "Pool Table 2").equals("AVAILABLE"), "card is " + cardStatus(tpc, "Pool Table 2"));
            check(sessionDAO.findActive().stream().noneMatch(x -> x.getTableName().equals("Pool Table 2")), "still active");
            return "No new bill (" + billsBefore + " bills before and after); Pool Table 2 is AVAILABLE again";
        });
        test("Corrections", "Reopen Game after an accidental End Game (bill not paid)", "Old bill CANCELLED, game running again on the same table", () -> {
            rightClick(cardFor(tpc, "Pool Table 2"));
            clickPopup("Start Game");
            JDialog d = waitForDialog();
            chooseCustomer(d, "Rahul Sharma");
            answer("OK"); answer("OK");
            rightClick(cardFor(tpc, "Pool Table 2"));
            clickPopup("End Game");
            answer("Yes");
            answer("Close");                       // close the bill window without paying (the "accident")
            Bill unpaid = billDAO.search("", true).get(0);
            goTo("Billing");
            BillingPanel bpc = panel(BillingPanel.class);
            JTable bills = all(bpc, JTable.class, true).get(0);
            selectRow(bills, rowWith(bills, unpaid.getBillNumber()));
            click(bpc, "Reopen Game");
            answer("Yes");
            String info = answer("OK");
            check(info.contains("running again"), info);
            check(billDAO.findById(unpaid.getId()).isCancelled(), "old bill not cancelled");
            check(sessionDAO.findActive().stream().anyMatch(x -> x.getTableName().equals("Pool Table 2")), "game not running");
            return "Pop-up: \"" + info + "\"; MySQL: bill " + unpaid.getBillNumber() + " CANCELLED, Pool Table 2 ACTIVE again";
        });
        test("Corrections", "End the reopened game and pay", "A new bill is made and paid; the cancelled bill stays in history", () -> {
            goTo("Tables");
            rightClick(cardFor(tpc, "Pool Table 2"));
            clickPopup("End Game");
            answer("Yes");
            JDialog bill = waitForDialog();
            SwingUtilities.invokeLater(findButton(bill, "Pay Cash")::doClick);
            answer("OK"); answer("Close");
            Bill paid = billDAO.search("Rahul", false).get(0);
            check(paid.isPaid() && !paid.isCancelled(), paid.getPaymentLabel());
            long cancelled = billDAO.search("Rahul", false).stream().filter(Bill::isCancelled).count();
            check(cancelled >= 1, "cancelled bill missing");
            return "New bill " + paid.getBillNumber() + " PAID (" + paid.getPaymentMethod() + "); cancelled bills kept in history: " + cancelled;
        });
        test("Auto refresh", "Another counter starts a game (directly in MySQL)", "Within 15 seconds the Tables screen shows it without clicking anything", () -> {
            User owner = new AuthService().login("owner", "owner123");
            GameTable ps5 = tableDAO.findAll().stream().filter(x -> x.getName().equals("PS5 Station 1")).findFirst().get();
            Customer priya = customerDAO.search("Priya").get(0);
            check(cardStatus(tpc, "PS5 Station 1").equals("AVAILABLE"), "PS5 not free at start");
            int sessionId = new SessionService().startSession(ps5, priya, owner);
            long start = System.currentTimeMillis();
            String status = "AVAILABLE";
            while (System.currentTimeMillis() - start < 20000 && !status.equals("OCCUPIED")) { sleep(500); status = cardStatus(tpc, "PS5 Station 1"); }
            long seconds = (System.currentTimeMillis() - start) / 1000;
            Bill b = new SessionService().endSession(sessionId, owner);      // tidy up
            new BillService().recordPayment(b, "UPI", owner);
            check(status.equals("OCCUPIED"), "screen did not update in 20 seconds");
            return "Card changed to OCCUPIED by itself after about " + seconds + " seconds (auto refresh every 15 seconds)";
        });

        test("Dashboard", "Notifications button", "Pop-up lists the current notifications", () -> {
            AbstractButton n = onEdt(() -> all(frame, AbstractButton.class, true).stream().filter(b -> b.getText() != null && b.getText().startsWith("Notifications")).findFirst().orElse(null));
            check(n != null, "no notification button");
            SwingUtilities.invokeLater(n::doClick);
            String text = answer("OK");
            return "Pop-up: \"" + text + "\"";
        });
        test("Navigation", "Menu: Help > About Cue 147", "About pop-up opens", () -> {
            JMenuItem about = onEdt(() -> { JMenuBar bar = frame.getJMenuBar(); for (int i = 0; i < bar.getMenuCount(); i++) for (java.awt.Component c : bar.getMenu(i).getMenuComponents()) if (c instanceof JMenuItem && "About Cue 147".equals(((JMenuItem) c).getText())) return (JMenuItem) c; return null; });
            check(about != null, "menu item not found");
            SwingUtilities.invokeLater(about::doClick);
            String text = answer("OK");
            check(text.contains("CUE 147"), text);
            return "Pop-up: \"" + text.substring(0, 40) + "...\"";
        });
        test("Navigation", "Toolbar button: New Customer", "Customers screen opens", () -> {
            click(frame, "New Customer"); sleep(600);
            check(onEdt(() -> all(frame, CustomersPanel.class, true).size() == 1), "Customers screen not shown");
            return "Customers screen is shown";
        });
        test("Customers", "Delete a customer who has bills (Rahul Sharma)", "Pop-up: the record is kept; nothing deleted", () -> {
            CustomersPanel cp2 = panel(CustomersPanel.class);
            JTable ct = all(cp2, JTable.class, true).get(0);
            selectRow(ct, rowWith(ct, "Rahul Sharma"));
            click(cp2, "Delete");
            answer("Yes");
            String text = answer("OK");
            check(text.contains("record is kept"), text);
            check(customerDAO.search("9876500011").size() == 1, "customer was deleted");
            return "Pop-up: \"" + text + "\"; Rahul is still in MySQL";
        });

        // ================= LOGOUT / STAFF =================
        test("Logout", "Logout button, answer Yes", "Login window opens again", () -> {
            click(frame, "Logout"); answer("Yes");
            LoginFrame again = waitForLogin();
            check(again != null, "login window did not open");
            return "Login window is shown again";
        });
        LoginFrame login2 = waitForLogin();
        JTextFieldPair f2 = new JTextFieldPair(login2);
        test("Permissions", "Login as Staff", "Main window opens as Counter Staff", () -> {
            setText(field(f2.user), "staff"); setText(field(f2.pass), "staff123"); click(login2, "Staff");
            click(login2, "LOGIN");
            MainFrame mf = waitForMainFrame();
            check(mf != null, "main window did not open");
            sleep(1000);
            return "Main window opened for staff";
        });
        MainFrame staffFrame = mainFrame();
        test("Permissions", "Staff sidebar has no Expenses, Reports or Settings", "Owner-only screens are hidden", () -> {
            // the sidebar buttons are RoundButton objects (the menu bar also has a "Reports" menu, which is a different component)
            List<String> sidebar = onEdt(() -> { List<String> names = new ArrayList<>(); for (RoundButton rb : all(staffFrame, RoundButton.class, true)) names.add(rb.getText().trim()); return names; });
            for (String hidden : new String[]{"Expenses", "Reports", "Settings"}) check(!sidebar.contains(hidden), hidden + " is in the sidebar");
            sidebar = sidebar.subList(0, Math.min(7, sidebar.size()));   // the 7 screen buttons
            check(sidebar.contains("Tables") && sidebar.contains("Billing"), "normal screens missing");
            boolean reportsMenuOff = onEdt(() -> { JMenuBar bar = staffFrame.getJMenuBar(); for (int i = 0; i < bar.getMenuCount(); i++) if ("Reports".equals(bar.getMenu(i).getText())) return !bar.getMenu(i).isEnabled(); return false; });
            check(reportsMenuOff, "the Reports menu is enabled");
            return "Sidebar: " + sidebar + " (no Expenses, Reports, Settings); the Reports menu is greyed out";
        });
        goTo("Tables");
        test("Permissions", "Staff Tables screen has no Add / Remove / Maintenance", "Owner-only table buttons are hidden", () -> {
            for (String hidden : new String[]{"Add Table", "Edit Table", "Remove Table", "Maintenance"}) check(findButton(staffFrame, hidden) == null, hidden + " is visible");
            return "Add, Edit, Remove, Maintenance buttons are not shown";
        });
        TablesPanel tp2 = panel(TablesPanel.class);
        test("Permissions", "Staff runs a game and takes UPI payment", "Bill saved; 'received by' is Counter Staff", () -> {
            click(tp2, "Start Game");
            JDialog d = waitForDialog();
            edt(() -> { JComboBox<?> box = all(d, JComboBox.class, false).get(0); for (int i = 0; i < box.getItemCount(); i++) if (String.valueOf(box.getItemAt(i)).contains("Rahul Sharma")) box.setSelectedIndex(i); });
            answer("OK"); answer("OK");
            click(tp2, "End Game"); answer("Yes");
            JDialog bill = waitForDialog();
            SwingUtilities.invokeLater(findButton(bill, "Pay UPI")::doClick);
            answer("OK"); answer("Close");
            Bill b = billDAO.search("Rahul", false).get(0);
            check(b.isPaid() && b.getServedBy().equals("Counter Staff") && b.getReceivedBy().equals("Counter Staff"), b.getServedBy() + "/" + b.getReceivedBy());
            return "MySQL: bill " + b.getBillNumber() + " PAID (UPI); served by " + b.getServedBy() + ", received by " + b.getReceivedBy();
        });
        goTo("Customers");
        test("Permissions", "Staff Customers screen has no Delete button", "Only the owner can delete customers", () -> {
            check(findButton(staffFrame, "Delete") == null, "Delete is visible");
            check(findButton(staffFrame, "Add") != null, "Add missing");
            return "Delete is not shown for staff; Add is shown";
        });
        goTo("Staff");
        test("Permissions", "Staff can change their own password", "Change My Password button is shown for staff", () -> {
            check(findButton(staffFrame, "Change My Password") != null, "button missing");
            return "Change My Password is shown for staff";
        });
        goTo("Billing");
        test("Permissions", "Staff Billing screen has no Cancel Bill / Change Method", "Owner-only buttons are hidden; Reopen Game is available", () -> {
            check(findButton(staffFrame, "Cancel Bill") == null && findButton(staffFrame, "Change Method") == null, "owner buttons visible");
            check(findButton(staffFrame, "Reopen Game") != null, "Reopen Game missing");
            return "Cancel Bill and Change Method hidden; Reopen Game shown";
        });
        test("Logout", "Staff logs out", "Login window opens again", () -> {
            click(staffFrame, "Logout"); answer("Yes");
            check(waitForLogin() != null, "login window did not open");
            return "Login window is shown again";
        });

        // ================= RESULTS =================
        try (PrintWriter out = new PrintWriter("docs/test-results/gui-click-test-results.tsv", "UTF-8")) {
            out.println("ID\tModule\tTest case\tExpected result\tActual result\tResult");
            for (String[] r : results) out.println(String.join("\t", r).replace("\n", " "));
        }
        System.out.println(passed + " passed, " + failed + " failed  (results saved in docs/test-results/gui-click-test-results.tsv)");
        System.exit(failed > 0 ? 1 : 0);
    }

    /**
     * Prints the bill text area to a PostScript FILE instead of a printer (same Printable that area.print() sends
     * to a real printer) and saves a picture of page 1. Returns the size of the print file.
     */
    static long printToFile(JTextArea area, File file, File picture) throws Exception {
        javax.print.DocFlavor flavor = javax.print.DocFlavor.SERVICE_FORMATTED.PRINTABLE;
        javax.print.StreamPrintServiceFactory[] factories =
                javax.print.StreamPrintServiceFactory.lookupStreamPrintServiceFactories(flavor, "application/postscript");
        check(factories.length > 0, "Java has no PostScript print service");
        Printable printable = onEdt(() -> area.getPrintable(null, null));
        try (FileOutputStream out = new FileOutputStream(file)) {
            javax.print.StreamPrintService service = factories[0].getPrintService(out);
            edt(() -> {
                try { service.createPrintJob().print(new javax.print.SimpleDoc(printable, flavor, null), null); }
                catch (javax.print.PrintException e) { throw new RuntimeException(e); }
            });
        }
        edt(() -> {
            try {
                PageFormat page = new PageFormat();   // A4/Letter portrait, 72 dots per inch
                BufferedImage img = new BufferedImage((int) page.getWidth() * 2, (int) page.getHeight() * 2, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = img.createGraphics();
                g.setColor(Color.WHITE); g.fillRect(0, 0, img.getWidth(), img.getHeight());
                g.scale(2, 2);
                area.getPrintable(null, null).print(g, page, 0);
                g.dispose();
                ImageIO.write(img, "png", picture);
            } catch (Exception e) { System.out.println("print preview failed: " + e); }
        });
        return file.length();
    }

    /** Finds the username box and the password box of a login window. */
    static class JTextFieldPair {
        final JTextField user; final JPasswordField pass;
        JTextFieldPair(LoginFrame frame) {
            List<JPasswordField> p = all(frame, JPasswordField.class, true);
            pass = p.get(0);
            JTextField u = null;
            for (JTextField t : all(frame, JTextField.class, true)) if (!(t instanceof JPasswordField)) { u = t; break; }
            user = u;
        }
    }
}
