package util;

import java.util.prefs.Preferences;

/**
 * Small settings that belong to THIS computer, not to the database:
 * the club's name and phone (printed on bills), opening hours, and the look of the program.
 * They are saved with Java Preferences, so they stay after the program is closed.
 */
public class AppSettings {
    // the tests use a separate place (cue147_test), so they never change the real settings
    private static final Preferences PREFS = Preferences.userRoot().node("cue147/" + System.getProperty("cue147.db", "cue147"));

    public static final String[] THEMES = {"Green Baize", "Midnight Blue", "Burgundy"};

    public static String getClubName() { return PREFS.get("clubName", "CUE 147 Snooker & Pool Club"); }
    public static void setClubName(String name) { PREFS.put("clubName", name); }

    public static String getClubPhone() { return PREFS.get("clubPhone", ""); }
    public static void setClubPhone(String phone) { PREFS.put("clubPhone", phone); }

    public static String getOpenTime() { return PREFS.get("openTime", "10:00"); }
    public static void setOpenTime(String time) { PREFS.put("openTime", time); }

    public static String getCloseTime() { return PREFS.get("closeTime", "23:30"); }
    public static void setCloseTime(String time) { PREFS.put("closeTime", time); }

    /** Colour theme: one of THEMES. */
    public static String getTheme() { return PREFS.get("theme", THEMES[0]); }
    public static void setTheme(String theme) { PREFS.put("theme", theme); }

    /** Normal text size in points (12 to 16, normal is 14). */
    public static int getFontSize() { return PREFS.getInt("fontSize", 14); }
    public static void setFontSize(int size) { PREFS.putInt("fontSize", size); }

    public static boolean isNotificationsOn() { return PREFS.getBoolean("notifications", true); }
    public static void setNotificationsOn(boolean on) { PREFS.putBoolean("notifications", on); }

    /** Puts every setting back to its starting value. */
    /** The "Billed by" person last chosen at THIS counter (0 = nobody yet). */
    public static int getLastBilledById() { return PREFS.getInt("lastBilledBy", 0); }
    public static void setLastBilledById(int staffId) { PREFS.putInt("lastBilledBy", staffId); }

    // Backups (saved per computer: each computer can keep copies in its own folder)
    public static String getBackupFolder() {
        return PREFS.get("backupFolder", java.nio.file.Paths.get(System.getProperty("user.home"), "CUE147 Backups").toString());
    }
    public static void setBackupFolder(String folder) { PREFS.put("backupFolder", folder); }
    public static int getBackupKeep() { return PREFS.getInt("backupKeep", 14); }
    public static void setBackupKeep(int count) { PREFS.putInt("backupKeep", count); }
    public static boolean isAutoBackup() { return PREFS.getBoolean("autoBackup", true); }
    public static void setAutoBackup(boolean on) { PREFS.putBoolean("autoBackup", on); }

    public static void resetAll() {
        try {
            PREFS.clear();
        } catch (Exception e) {
            AppLogger.error("Could not reset the settings", e);
        }
    }
}
