package app;

import ui.LoginFrame;
import ui.Theme;
import util.AppLogger;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/** Entry point of CUE 147. Starts the Swing UI on the Event Dispatch Thread. */
public class Main {
    public static void main(String[] args) {
        // Safety net: if any error was not caught anywhere, log it and show a message instead of silently failing.
        Thread.setDefaultUncaughtExceptionHandler((thread, problem) -> {
            AppLogger.error("Unexpected error in thread " + thread.getName(), problem);
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(null,
                    "Something unexpected went wrong:\n" + problem + "\n\nThe details were saved in cue147.log.",
                    "Unexpected error", JOptionPane.ERROR_MESSAGE));
        });
        SwingUtilities.invokeLater(() -> {
            Theme.install();
            new LoginFrame().setVisible(true);
        });
    }
}
