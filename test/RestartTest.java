import dao.*;
import model.*;
import service.*;

import java.time.LocalDateTime;

/**
 * Proves that a running game survives closing the program.
 * ./test.sh runs this class TWICE in two separate Java programs:
 *   java RestartTest create   -> starts a game and exits (the "program is closed")
 *   java RestartTest check    -> a brand new program finds the game still running and ends it
 */
public class RestartTest {
    public static void main(String[] args) throws Exception {
        User staff = new AuthService().login("staff", "staff123");
        GameTableDAO tableDAO = new GameTableDAO();
        if (args[0].equals("create")) {
            GameTable ps5 = tableDAO.findAll().stream().filter(t -> t.getName().equals("PS5 Station 1")).findFirst().get();
            Customer customer = new CustomerDAO().findAll().stream().filter(c -> !c.isWalkIn()).findFirst().get();
            new SessionService().startSession(ps5, customer, staff, LocalDateTime.now().minusMinutes(20));
            System.out.println("Program 1: game started on PS5 Station 1, now closing the program...");
        } else {
            GameTable ps5 = tableDAO.findAll().stream().filter(t -> t.getName().equals("PS5 Station 1")).findFirst().get();
            boolean stillRunning = ps5.isOccupied() && new SessionDAO().countActive() == 1;
            System.out.println((stillRunning ? "PASS  " : "FAIL  ") + "Program 2: the running game survived the restart (table is OCCUPIED, 1 active session)");
            Bill bill = new SessionService().endSession(ps5.getActiveSessionId(), staff);
            boolean billed = bill.getBilledMinutes() >= 20 && bill.getFinalAmount().signum() > 0;
            System.out.println((billed ? "PASS  " : "FAIL  ") + "Program 2: the game can be ended and billed after the restart (" + bill.getFinalAmount() + ")");
            if (!stillRunning || !billed) System.exit(1);
        }
    }
}
