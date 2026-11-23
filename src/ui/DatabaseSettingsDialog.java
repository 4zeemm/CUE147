package ui;

import database.DBConnection;
import database.DatabaseConfig;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.IOException;

/**
 * "Database settings" window (opened from the login screen).
 * Every computer at the club points to the SAME database server, so all of them see the same data.
 *   - On the server computer itself: host 127.0.0.1
 *   - On the other computers: the server's network address, for example 192.168.1.20
 */
public class DatabaseSettingsDialog extends JDialog {
    private final JTextField hostField = Theme.field(18);
    private final JSpinner portSpinner = Theme.styleSpinner(new JSpinner(new SpinnerNumberModel(3306, 1, 65535, 1)));
    private final JTextField databaseField = Theme.field(18);
    private final JTextField userField = Theme.field(18);
    private final JPasswordField passwordField = new JPasswordField(18);
    private boolean saved = false;

    public DatabaseSettingsDialog(Window owner) {
        super(owner, "Database settings", ModalityType.APPLICATION_MODAL);
        Theme.styleField(passwordField);
        portSpinner.setEditor(new JSpinner.NumberEditor(portSpinner, "#"));   // 3306, not 3,306

        DatabaseConfig c = DBConnection.getConfig();
        hostField.setText(c.getHost());
        portSpinner.setValue(c.getPort());
        databaseField.setText(c.getDatabase());
        userField.setText(c.getUser());
        passwordField.setText(c.getPassword());

        JPanel form = Theme.panel(new GridBagLayout());
        Theme.addRow(form, 0, "Server address", hostField);
        Theme.addRow(form, 1, "Port", portSpinner);
        Theme.addRow(form, 2, "Database", databaseField);
        Theme.addRow(form, 3, "User", userField);
        Theme.addRow(form, 4, "Password", passwordField);
        Theme.addRow(form, 5, "", Theme.label("<html>On the server computer use 127.0.0.1.<br>"
                + "On every other computer use the server's address, e.g. 192.168.1.20.</html>", Theme.SMALL, Theme.MUTED));

        JPanel buttons = Theme.panel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(Theme.button("Test Connection", Theme.BLUE, e -> testConnection()));
        buttons.add(Theme.button("Save", Theme.GREEN, e -> save()));
        buttons.add(Theme.button("Cancel", Theme.NEUTRAL, e -> dispose()));

        JPanel root = Theme.panel(new BorderLayout(0, 14));
        root.setBorder(new EmptyBorder(20, 24, 18, 24));
        root.add(Theme.label("Where is the club database?", Theme.HEADING, Theme.TEXT), BorderLayout.NORTH);
        root.add(form, BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);
        setContentPane(root);
        pack();
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    /** Reads the boxes. Returns null (after showing a message) when something is missing or wrong. */
    private DatabaseConfig readFields() {
        String host = hostField.getText().trim();
        String database = databaseField.getText().trim();
        String user = userField.getText().trim();
        if (host.isEmpty()) { Theme.error(this, "Server address is required (for example 127.0.0.1)."); return null; }
        if (host.contains(" ") || host.contains("/")) { Theme.error(this, "Server address must not contain spaces or '/'."); return null; }
        if (!database.matches("[A-Za-z0-9_]+")) { Theme.error(this, "Database name may only contain letters, numbers and _."); return null; }
        if (user.isEmpty()) { Theme.error(this, "User is required."); return null; }
        return new DatabaseConfig(host, ((Number) portSpinner.getValue()).intValue(), database, user,
                new String(passwordField.getPassword()));
    }

    private void testConnection() {
        DatabaseConfig c = readFields();
        if (c == null) return;
        String problem = DBConnection.testConnection(c);
        if (problem == null) Theme.info(this, "Connected to " + c.describe() + ".", "Database settings");
        else Theme.error(this, problem);
    }

    private void save() {
        DatabaseConfig c = readFields();
        if (c == null) return;
        String problem = DBConnection.testConnection(c);
        if (problem != null && !Theme.confirm(this, problem + "\n\nSave these settings anyway?", "Database settings")) return;
        try {
            c.save();
        } catch (IOException e) {
            Theme.error(this, "Could not save the settings file:\n" + DatabaseConfig.FILE + "\n" + e.getMessage());
            return;
        }
        DBConnection.setConfig(c);
        saved = true;
        dispose();
    }

    /** Opens the window and waits. True when new settings were saved. */
    public static boolean open(Window owner) {
        DatabaseSettingsDialog d = new DatabaseSettingsDialog(owner);
        d.setVisible(true);
        return d.saved;
    }
}
