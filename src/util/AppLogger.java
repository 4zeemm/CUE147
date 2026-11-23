package util;

import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * Writes errors to the file cue147.log using java.util.logging.
 * When something goes wrong at the club, the log shows what happened and when.
 */
public class AppLogger {
    private static final Logger LOGGER = Logger.getLogger("cue147");

    static {
        try {
            FileHandler fileHandler = new FileHandler("cue147.log", true);   // true = add to the old file
            fileHandler.setFormatter(new SimpleFormatter());
            LOGGER.addHandler(fileHandler);
        } catch (IOException | SecurityException e) {
            System.err.println("Could not open cue147.log: " + e.getMessage());   // we still run without a log file
        }
    }

    public static void info(String message) {
        LOGGER.info(message);
    }

    public static void error(String message, Throwable problem) {
        LOGGER.log(Level.SEVERE, message, problem);
    }
}
