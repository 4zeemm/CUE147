package util;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/** The program's own details: version number and window icon. */
public class AppInfo {
    private static final String VERSION = readVersion();

    /** For example "2.0.0" (set in pom.xml, written into app.properties when the program is built). */
    public static String getVersion() { return VERSION; }

    private static String readVersion() {
        try (InputStream in = AppInfo.class.getResourceAsStream("/app.properties")) {
            if (in == null) return "development";
            Properties p = new Properties();
            p.load(in);
            return p.getProperty("version", "development");
        } catch (Exception e) {
            return "development";
        }
    }

    /** The CUE 147 icon in several sizes, for the window corner and the task bar. */
    public static List<java.awt.Image> icons() {
        List<java.awt.Image> list = new ArrayList<>();
        for (int size : new int[]{16, 32, 48, 64, 128, 256, 512}) {
            java.net.URL url = AppInfo.class.getResource("/icons/icon-" + size + ".png");
            if (url != null) list.add(new javax.swing.ImageIcon(url).getImage());
        }
        return list;
    }
}
