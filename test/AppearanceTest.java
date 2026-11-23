import model.User;
import service.AuthService;
import ui.MainFrame;
import ui.Theme;
import util.AppSettings;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Runs as a NEW program after GuiClickTest. GuiClickTest saved "Midnight Blue" and text size 16 in Settings.
 * This checks that a fresh start of CUE 147 really uses that look, takes a screenshot, then puts the defaults back.
 */
public class AppearanceTest {
    static int passed = 0, failed = 0;

    static void check(String name, boolean condition) {
        System.out.println((condition ? "PASS  " : "FAIL  ") + name);
        if (condition) passed++; else failed++;
    }

    public static void main(String[] args) throws Exception {
        check("The saved look is read back: Midnight Blue, text size 16",
                AppSettings.getTheme().equals("Midnight Blue") && AppSettings.getFontSize() == 16);
        SwingUtilities.invokeAndWait(Theme::install);
        check("After start-up the accent colour is Midnight Blue", Theme.ACCENT.equals(Theme.accentFor("Midnight Blue")));
        check("After start-up the text is bigger (body 16, title 30)", Theme.BODY.getSize() == 16 && Theme.TITLE.getSize() == 30);

        User owner = new AuthService().login("owner", "owner123");
        MainFrame[] frame = new MainFrame[1];
        SwingUtilities.invokeAndWait(() -> { frame[0] = new MainFrame(owner); frame[0].setVisible(true); });
        Thread.sleep(2500);
        SwingUtilities.invokeAndWait(() -> {
            try {
                BufferedImage img = new BufferedImage(frame[0].getWidth(), frame[0].getHeight(), BufferedImage.TYPE_INT_RGB);
                Graphics2D g = img.createGraphics();
                frame[0].getLayeredPane().paint(g);
                g.dispose();
                ImageIO.write(img, "png", new File("docs/screenshots/15-appearance-midnight-blue.png"));
            } catch (Exception e) { System.out.println("screenshot failed: " + e); }
            frame[0].dispose();
        });
        check("The main window opens with the new look (screenshot 15-appearance-midnight-blue.png)",
                new File("docs/screenshots/15-appearance-midnight-blue.png").length() > 0);

        AppSettings.resetAll();   // put the normal look and the normal club name back for the test database
        SwingUtilities.invokeAndWait(Theme::install);
        check("After Reset the normal look is used again (Green Baize, 14)",
                Theme.ACCENT.equals(Theme.GREEN) && Theme.BODY.getSize() == 14 && AppSettings.getClubName().startsWith("CUE 147"));
        System.out.println(passed + " passed, " + failed + " failed");
        System.exit(failed > 0 ? 1 : 0);
    }
}
