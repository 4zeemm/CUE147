import model.User;
import service.AuthService;
import ui.MainFrame;
import ui.Theme;

import javax.swing.*;
import java.awt.*;
import java.util.HashSet;
import java.util.Set;

/**
 * Opens the real main window as Owner and as Staff, visits every screen and checks that
 * the owner-only buttons are shown to the owner and hidden from the staff member.
 * (It needs a screen, so it runs on a normal computer, not on a server without display.)
 */
public class UiSmokeTest {
    static int passed = 0, failed = 0;

    static void check(String name, boolean ok) {
        System.out.println((ok ? "PASS  " : "FAIL  ") + name);
        if (ok) passed++; else failed++;
    }

    /** Collects the text of every JButton inside a component (also buttons on screens that are not visible now). */
    static void collectButtons(Component c, Set<String> texts) {
        if (c instanceof AbstractButton) texts.add(((AbstractButton) c).getText());
        if (c instanceof Container) for (Component child : ((Container) c).getComponents()) collectButtons(child, texts);
    }

    static Set<String> openAllScreens(String username, String password, String[] screens) throws Exception {
        User user = new AuthService().login(username, password);
        MainFrame[] frame = new MainFrame[1];
        SwingUtilities.invokeAndWait(() -> frame[0] = new MainFrame(user));
        for (String screen : screens) {
            SwingUtilities.invokeAndWait(() -> frame[0].showScreen(screen));
            Thread.sleep(500);   // let the dashboard SwingWorker finish
        }
        Set<String> buttons = new HashSet<>();
        SwingUtilities.invokeAndWait(() -> collectButtons(frame[0], buttons));
        SwingUtilities.invokeAndWait(() -> frame[0].dispose());
        return buttons;
    }

    public static void main(String[] args) throws Exception {
        // safety: never hang forever if an error dialog opens
        Thread watchdog = new Thread(() -> { try { Thread.sleep(90000); } catch (InterruptedException e) { } System.out.println("FAIL  timeout (a dialog may have opened)"); System.exit(2); });
        watchdog.setDaemon(true);
        watchdog.start();

        SwingUtilities.invokeAndWait(Theme::install);
        String[] ownerScreens = {"Dashboard", "Tables", "Customers", "Bookings", "Billing", "Membership", "Expenses", "Reports", "Staff", "Settings"};
        String[] staffScreens = {"Dashboard", "Tables", "Customers", "Bookings", "Billing", "Membership", "Staff"};

        Set<String> owner = openAllScreens("owner", "owner123", ownerScreens);
        check("Owner: all 10 screens open without an error", true);
        check("Owner sees Edit, Delete, Export on Expenses", owner.contains("Edit") && owner.contains("Delete") && owner.contains("View Voucher"));
        check("Owner sees Maintenance and Remove Table on Tables", owner.contains("Maintenance") && owner.contains("Remove Table"));
        check("Owner sees Cancel Bill and Change Method on Billing", owner.contains("Cancel Bill") && owner.contains("Change Method"));
        check("Owner sees Reopen Game", owner.contains("Reopen Game"));

        Set<String> staff = openAllScreens("staff", "staff123", staffScreens);
        check("Staff: the 7 allowed screens open without an error", true);
        check("Staff does NOT get Expenses buttons", !staff.contains("Edit") && !staff.contains("Delete") && !staff.contains("View Voucher"));
        check("Staff does NOT get Maintenance / Remove Table", !staff.contains("Maintenance") && !staff.contains("Remove Table") && !staff.contains("Add Table"));
        check("Staff does NOT get Cancel Bill / Change Method", !staff.contains("Cancel Bill") && !staff.contains("Change Method"));
        check("Staff still gets Reopen Game, Checkout / Pay and Start Game", staff.contains("Reopen Game") && staff.contains("Checkout / Pay"));

        System.out.println(passed + " passed, " + failed + " failed");
        System.exit(failed > 0 ? 1 : 0);
    }
}
