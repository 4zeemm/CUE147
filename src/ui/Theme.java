package ui;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.Date;

/** Colours, fonts and small helper methods so every screen looks the same. */
public class Theme {
    public static final Color BG = new Color(0x0E1411);
    public static final Color SIDEBAR = new Color(0x0A100D);
    public static final Color PANEL = new Color(0x151F1A);
    public static final Color CARD = new Color(0x1B2922);
    public static final Color FIELD = new Color(0x111A15);
    public static final Color NEUTRAL = new Color(0x3A5045);
    public static final Color BORDER = new Color(0x2C4036);
    public static final Color GREEN = new Color(0x1FA866);
    public static final Color GOLD = new Color(0xD9B44A);
    public static final Color TEXT = new Color(0xEAF2ED);
    public static final Color MUTED = new Color(0x8DA398);
    public static final Color RED = new Color(0xD65A5A);
    public static final Color AMBER = new Color(0xE0A030);
    public static final Color BLUE = new Color(0x4A90D9);
    /** The highlight colour of the chosen theme (selected menu, table selection, progress bars). */
    public static Color ACCENT = GREEN;

    // not final: install() makes them bigger or smaller with the "Text size" setting
    public static Font TITLE = new Font("SansSerif", Font.BOLD, 26);
    public static Font HEADING = new Font("SansSerif", Font.BOLD, 17);
    public static Font BODY = new Font("SansSerif", Font.PLAIN, 14);
    public static Font BOLD = new Font("SansSerif", Font.BOLD, 13);
    public static Font SMALL = new Font("SansSerif", Font.PLAIN, 12);

    /** The highlight colour for a theme name (see AppSettings.THEMES). */
    public static Color accentFor(String theme) {
        if (theme.equals("Midnight Blue")) return new Color(0x2F6FB5);
        if (theme.equals("Burgundy")) return new Color(0xA8364B);
        return GREEN;
    }

    /** Reads the saved theme and text size. Called once at start-up, before any window is made. */
    private static void loadAppearance() {
        ACCENT = accentFor(util.AppSettings.getTheme());
        double scale = util.AppSettings.getFontSize() / 14.0;   // 14 is the normal size
        TITLE = new Font("SansSerif", Font.BOLD, (int) Math.round(26 * scale));
        HEADING = new Font("SansSerif", Font.BOLD, (int) Math.round(17 * scale));
        BODY = new Font("SansSerif", Font.PLAIN, (int) Math.round(14 * scale));
        BOLD = new Font("SansSerif", Font.BOLD, (int) Math.round(13 * scale));
        SMALL = new Font("SansSerif", Font.PLAIN, (int) Math.round(12 * scale));
    }

    /**
     * Call once at start-up: the modern FlatLaf look with the club's own dark green colours.
     * Every colour, font and rounding is set HERE, so all screens look the same.
     */
    public static void install() {
        loadAppearance();
        try {
            com.formdev.flatlaf.FlatDarkLaf.setup();
        } catch (Exception e) {
            System.out.println("Could not set look and feel: " + e);
        }
        UIManager.put("defaultFont", BODY);                       // one font for every component (Text size setting)

        // ---- club colours ----
        put("@background", PANEL);
        put("@foreground", TEXT);
        put("@accentColor", ACCENT);
        put("@selectionBackground", ACCENT);
        put("Panel.background", BG);
        put("OptionPane.background", PANEL);
        put("OptionPane.messageForeground", TEXT);
        put("RootPane.background", BG);
        put("Component.borderColor", BORDER);
        put("Component.disabledBorderColor", BORDER);
        put("Component.focusColor", ACCENT);
        put("Component.focusedBorderColor", ACCENT);
        put("Component.linkColor", BLUE);
        put("Separator.foreground", BORDER);

        // ---- things the user types in ----
        for (String k : new String[]{"TextField", "PasswordField", "FormattedTextField", "TextArea", "ComboBox", "Spinner", "List"}) {
            put(k + ".background", FIELD);
            put(k + ".foreground", TEXT);
            put(k + ".selectionBackground", ACCENT);
            put(k + ".selectionForeground", Color.WHITE);
        }
        put("ComboBox.buttonBackground", FIELD);
        put("ComboBox.buttonArrowColor", MUTED);
        put("ComboBox.buttonHoverArrowColor", TEXT);
        put("ComboBox.popupBackground", CARD);
        put("Spinner.buttonBackground", FIELD);
        put("Spinner.buttonArrowColor", MUTED);
        put("TextComponent.selectionInsets", new Insets(0, 0, 0, 0));

        // ---- buttons, tabs, menus ----
        put("Button.background", CARD);
        put("Button.foreground", TEXT);
        put("Button.hoverBackground", NEUTRAL);
        put("Button.default.background", ACCENT);
        put("Button.default.foreground", Color.WHITE);
        put("ToggleButton.selectedBackground", ACCENT);
        put("MenuBar.background", SIDEBAR);
        put("MenuItem.background", SIDEBAR);
        put("PopupMenu.background", CARD);
        put("Menu.selectionBackground", ACCENT);
        put("MenuItem.selectionBackground", ACCENT);
        put("MenuItem.selectionForeground", Color.WHITE);
        put("TabbedPane.background", BG);
        put("TabbedPane.contentAreaColor", CARD);
        put("TabbedPane.underlineColor", ACCENT);
        put("TabbedPane.selectedBackground", CARD);
        put("TabbedPane.hoverColor", NEUTRAL);
        put("TabbedPane.focusColor", NEUTRAL);

        // ---- lists, tables, bars ----
        put("Table.background", PANEL);
        put("Table.gridColor", BORDER);
        put("Table.selectionBackground", ACCENT.darker());
        put("Table.selectionForeground", Color.WHITE);
        put("TableHeader.background", CARD);
        put("TableHeader.foreground", GOLD);
        put("TableHeader.separatorColor", BORDER);
        put("ProgressBar.background", FIELD);
        put("ProgressBar.foreground", ACCENT);
        put("Slider.trackColor", FIELD);
        put("Slider.thumbColor", ACCENT);
        put("ScrollBar.track", FIELD);
        put("ScrollBar.thumb", NEUTRAL);
        put("ScrollBar.hoverThumbColor", ACCENT);
        put("ScrollPane.background", BG);
        put("Viewport.background", PANEL);
        put("ToolTip.background", CARD);
        put("ToolTip.foreground", TEXT);
        put("CheckBox.background", BG);
        put("RadioButton.background", BG);
        put("Label.foreground", TEXT);

        // ---- rounded corners and spacing (the same everywhere) ----
        put("Component.arc", 10);
        put("Button.arc", 10);
        put("CheckBox.arc", 6);
        put("TextComponent.arc", 8);
        put("ProgressBar.arc", 8);
        put("ScrollBar.thumbArc", 8);
        put("ScrollBar.width", 12);
        put("ScrollBar.trackInsets", new Insets(2, 2, 2, 2));
        put("ScrollBar.thumbInsets", new Insets(2, 2, 2, 2));
        put("Component.innerFocusWidth", 1);
        put("TabbedPane.tabHeight", 34);
        put("Table.rowHeight", 30);
        put("OptionPane.buttonMinimumWidth", 90);
    }

    private static void put(String key, Object value) { UIManager.put(key, value); }

    // ---------------- small factory methods ----------------

    public static JLabel label(String text, Font font, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(font);
        l.setForeground(color);
        return l;
    }

    /** A see-through panel (so the parent's background shows through). */
    public static JPanel panel(LayoutManager layout) {
        JPanel p = new JPanel(layout);
        p.setOpaque(false);
        return p;
    }

    public static RoundButton button(String text, Color color, java.awt.event.ActionListener action) {
        RoundButton b = new RoundButton(text, color);
        b.addActionListener(action);
        return b;
    }

    public static JTextField field(int columns) {
        JTextField f = new JTextField(columns);
        styleField(f);
        return f;
    }

    public static JComboBox<String> combo(String... items) {
        JComboBox<String> c = new JComboBox<>(items);
        c.setFont(BODY);
        c.setBackground(FIELD);
        c.setForeground(TEXT);
        return c;
    }

    public static JCheckBox checkBox(String text) {
        JCheckBox c = new JCheckBox(text);
        c.setOpaque(false);
        c.setFont(BODY);
        c.setForeground(TEXT);
        c.setFocusPainted(false);
        return c;
    }

    public static JRadioButton radio(String text) {
        JRadioButton r = new JRadioButton(text);
        r.setOpaque(false);
        r.setFont(BODY);
        r.setForeground(TEXT);
        r.setFocusPainted(false);
        return r;
    }

    public static void styleField(JTextField f) {
        f.setFont(BODY);
        f.setBackground(FIELD);
        f.setForeground(TEXT);
        f.setCaretColor(TEXT);
        f.setBorder(new CompoundBorder(new LineBorder(BORDER, 1, true), new EmptyBorder(6, 8, 6, 8)));
    }

    public static JSpinner styleSpinner(JSpinner s) {
        s.setFont(BODY);
        s.setBorder(new LineBorder(BORDER, 1, true));
        if (s.getEditor() instanceof JSpinner.DefaultEditor) {
            JTextField tf = ((JSpinner.DefaultEditor) s.getEditor()).getTextField();
            tf.setBackground(FIELD);
            tf.setForeground(TEXT);
            tf.setCaretColor(TEXT);
            tf.setFont(BODY);
        }
        return s;
    }

    public static JSpinner numberSpinner(double value, double min, double max, double step) {
        return styleSpinner(new JSpinner(new SpinnerNumberModel(value, min, max, step)));
    }

    /** A spinner that shows a time of day, e.g. 18:30. */
    public static JSpinner timeSpinner(int hour, int minute) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, minute);
        cal.set(Calendar.SECOND, 0);
        JSpinner s = new JSpinner(new SpinnerDateModel(cal.getTime(), null, null, Calendar.MINUTE));
        s.setEditor(new JSpinner.DateEditor(s, "HH:mm"));
        return styleSpinner(s);
    }

    public static LocalTime timeOf(JSpinner s) {
        return ((Date) s.getValue()).toInstant().atZone(ZoneId.systemDefault()).toLocalTime().withSecond(0).withNano(0);
    }

    public static String timeText(JSpinner s) {
        return new SimpleDateFormat("HH:mm").format((Date) s.getValue());
    }

    public static void setLabelWidth(JLabel l) { l.setPreferredSize(new Dimension(110, 30)); }

    /** Adds "label : component" as a row of a GridBagLayout form. */
    public static void addRow(JPanel form, int row, String label, JComponent comp) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = row;
        c.insets = new Insets(6, 4, 6, 8);
        c.anchor = GridBagConstraints.WEST;
        c.gridx = 0;
        form.add(label(label, BODY, MUTED), c);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        form.add(comp, c);
    }

    public static JScrollPane scroll(Component view) {
        JScrollPane sp = new JScrollPane(view);
        sp.setBorder(new LineBorder(BORDER, 1, true));
        sp.getViewport().setBackground(CARD);
        sp.setBackground(CARD);
        return sp;
    }

    // ---------------- tables ----------------

    /** A read-only table model. */
    public static DefaultTableModel tableModel(String... columns) {
        return new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
    }

    public static void styleTable(JTable t) {
        t.setRowHeight(32);
        t.setFont(BODY);
        t.setForeground(TEXT);
        t.setBackground(CARD);
        t.setGridColor(BORDER);
        t.setShowVerticalLines(false);
        t.setSelectionBackground(ACCENT.darker());
        t.setSelectionForeground(Color.WHITE);
        t.setFillsViewportHeight(true);
        t.setDefaultRenderer(Object.class, new StripedRenderer());
        JTableHeader h = t.getTableHeader();
        h.setFont(BOLD);
        h.setBackground(SIDEBAR);
        h.setForeground(GOLD);
        h.setReorderingAllowed(false);
        h.setDefaultRenderer(new HeaderRenderer());
    }

    /** Alternating row colours; status words are coloured. */
    static class StripedRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean sel, boolean focus, int row, int col) {
            super.getTableCellRendererComponent(table, value, sel, false, row, col);
            setBorder(new EmptyBorder(0, 6, 0, 6));   // small padding so more text fits in a column
            if (!sel) setBackground(row % 2 == 0 ? CARD : PANEL);
            Color fg = TEXT;
            String s = String.valueOf(value);
            if (s.equals("AVAILABLE") || s.equals("CONFIRMED") || s.equals("PAID") || s.equals("ACTIVE")) fg = GREEN;
            else if (s.equals("OCCUPIED") || s.equals("UNPAID")) fg = RED;
            else if (s.equals("RESERVED") || s.equals("MAINTENANCE") || s.equals("CANCELLED") || s.equals("REFUNDED")) fg = AMBER;
            setForeground(sel ? Color.WHITE : fg);
            return this;
        }
    }

    static class HeaderRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean sel, boolean focus, int row, int col) {
            super.getTableCellRendererComponent(table, value, false, false, row, col);
            setBackground(SIDEBAR);
            setForeground(GOLD);
            setFont(BOLD);
            setBorder(new CompoundBorder(new MatteBorder(0, 0, 2, 0, ACCENT), new EmptyBorder(8, 6, 8, 6)));
            return this;
        }
    }

    // ---------------- dialogs ----------------

    public static void info(Component parent, String message, String title) {
        JOptionPane.showMessageDialog(parent, message, title, JOptionPane.INFORMATION_MESSAGE);
    }

    public static void error(Component parent, String message) {
        JOptionPane.showMessageDialog(parent, message, "Please check", JOptionPane.WARNING_MESSAGE);
    }

    public static boolean confirm(Component parent, String message, String title) {
        return JOptionPane.showConfirmDialog(parent, message, title, JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION;
    }

    public static String money(java.math.BigDecimal v) { return String.format("\u20B9%,.2f", v); }
    public static String money0(java.math.BigDecimal v) { return String.format("\u20B9%,.0f", v); }
    public static String money(double v) { return String.format("₹%,.2f", v); }
    public static String money0(double v) { return String.format("₹%,.0f", v); }
}
