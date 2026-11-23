package ui;

import model.User;
import service.AuthService;
import exception.ClubException;
import util.AppLogger;
import util.Validator;
import database.DBConnection;
import database.SchemaMigrator;
import exception.BusinessException;
import java.sql.SQLException;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/** Screen 1 - the login window. */
public class LoginFrame extends JFrame {
    // remembered between logouts while the app is running
    private static String rememberedUser = "";

    private final JTextField usernameField = Theme.field(18);
    private final JPasswordField passwordField = new JPasswordField(18);
    private final JCheckBox rememberBox = Theme.checkBox("Remember me");
    private final JCheckBox showPasswordBox = Theme.checkBox("Show password");
    private final JRadioButton ownerRadio = Theme.radio("Owner");
    private final JRadioButton staffRadio = Theme.radio("Staff");
    private final AuthService authService = new AuthService();

    public LoginFrame() {
        super("CUE 147 - Login");
        setIconImages(util.AppInfo.icons());
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(900, 580);
        setLocationRelativeTo(null);
        setResizable(false);

        setLayout(new GridLayout(1, 2));
        add(new BilliardsArtPanel());
        add(buildFormSide());

        prepareDatabase();
        if (!rememberedUser.isEmpty()) {
            usernameField.setText(rememberedUser);
            rememberBox.setSelected(true);
        }
    }

    /**
     * Connects to the club database, brings its tables up to date and creates the first owner/staff
     * accounts if needed. When the database cannot be reached, the user can open the Database settings.
     */
    private void prepareDatabase() {
        while (true) {
            String problem = DBConnection.testConnection(DBConnection.getConfig());
            if (problem == null) break;
            AppLogger.info("No database connection to " + DBConnection.getConfig().describe() + ": " + problem);
            String[] options = {"Database settings", "Try again", "Close"};
            int choice = JOptionPane.showOptionDialog(this, "Cannot connect to the club database.\n\n" + problem,
                    "No database connection", JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[0]);
            if (choice == 0) DatabaseSettingsDialog.open(this);
            else if (choice != 1) return;   // Close: the login window stays; logging in will say what is wrong
        }
        try {
            SchemaMigrator.migrate();          // adds new tables/columns after an update (keeps all data)
            authService.createDefaultUsers();
            new dao.StaffMemberDAO().fillFromUsersIfEmpty();   // first names for "Billed by" on a new database
        } catch (BusinessException e) {
            Theme.error(this, e.getMessage());
        } catch (SQLException e) {
            AppLogger.error("Database preparation failed", e);
            Theme.error(this, "Database problem:\n" + e.getMessage());
        }
    }

    private JPanel buildFormSide() {
        JPanel side = new JPanel(new GridBagLayout());
        side.setBackground(Theme.BG);
        side.setBorder(new EmptyBorder(30, 50, 30, 50));

        JPanel form = Theme.panel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        c.insets = new Insets(4, 0, 4, 0);

        form.add(Theme.label("Welcome back", Theme.TITLE, Theme.TEXT), c);
        form.add(Theme.label("Sign in to manage the club", Theme.BODY, Theme.MUTED), c);
        c.insets = new Insets(18, 0, 2, 0);
        form.add(Theme.label("Username", Theme.BOLD, Theme.MUTED), c);
        c.insets = new Insets(2, 0, 4, 0);
        form.add(usernameField, c);

        c.insets = new Insets(10, 0, 2, 0);
        form.add(Theme.label("Password", Theme.BOLD, Theme.MUTED), c);
        passwordField.setEchoChar('•');
        Theme.styleField(passwordField);
        c.insets = new Insets(2, 0, 4, 0);
        form.add(passwordField, c);

        JPanel checks = Theme.panel(new GridLayout(1, 2));
        checks.add(rememberBox);
        checks.add(showPasswordBox);
        form.add(checks, c);

        c.insets = new Insets(8, 0, 2, 0);
        form.add(Theme.label("Login as", Theme.BOLD, Theme.MUTED), c);
        ButtonGroup roleGroup = new ButtonGroup();
        roleGroup.add(ownerRadio);
        roleGroup.add(staffRadio);
        ownerRadio.setSelected(true);
        JPanel roles = Theme.panel(new GridLayout(1, 2));
        roles.add(ownerRadio);
        roles.add(staffRadio);
        c.insets = new Insets(2, 0, 4, 0);
        form.add(roles, c);

        RoundButton loginButton = Theme.button("LOGIN", Theme.GREEN, e -> doLogin());
        RoundButton clearButton = Theme.button("Clear", Theme.NEUTRAL, e -> doClear());
        JPanel buttons = Theme.panel(new GridLayout(1, 2, 10, 0));
        buttons.add(loginButton);
        buttons.add(clearButton);
        c.insets = new Insets(14, 0, 4, 0);
        form.add(buttons, c);

        JButton forgot = new JButton("Forgot password?");
        forgot.setBorderPainted(false);
        forgot.setContentAreaFilled(false);
        forgot.setFocusPainted(false);
        forgot.setForeground(Theme.GOLD);
        forgot.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        forgot.addActionListener(e -> forgotPassword());
        c.insets = new Insets(0, 0, 0, 0);
        form.add(forgot, c);
        form.add(Theme.label("Version " + util.AppInfo.getVersion(), Theme.SMALL, Theme.MUTED), c);

        JButton database = new JButton("Database settings");
        database.setBorderPainted(false);
        database.setContentAreaFilled(false);
        database.setFocusPainted(false);
        database.setForeground(Theme.MUTED);
        database.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        database.addActionListener(e -> { if (DatabaseSettingsDialog.open(this)) prepareDatabase(); });
        form.add(database, c);

        showPasswordBox.addActionListener(e ->
                passwordField.setEchoChar(showPasswordBox.isSelected() ? (char) 0 : '•'));
        getRootPane().setDefaultButton(loginButton);   // Enter key = Login

        side.add(form);
        return side;
    }

    private void doLogin() {
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());
        try {
            Validator.requireNotEmpty(username, "Username");
            Validator.requireNotEmpty(password, "Password");
            User user = authService.login(username, password);
            // the radio button must match the real role stored in the database
            String chosenRole = ownerRadio.isSelected() ? "OWNER" : "STAFF";
            if (!user.getRole().equals(chosenRole)) {
                Theme.error(this, "This account is a " + user.getRole() + " account. Please choose the correct role.");
                return;
            }
            rememberedUser = rememberBox.isSelected() ? username : "";
            dispose();
            new MainFrame(user).setVisible(true);
        } catch (ClubException e) {
            Theme.error(this, e.getMessage());   // empty fields (InvalidInputException) or wrong password (BusinessException)
        } catch (SQLException e) {
            AppLogger.error("Login failed because of a database problem", e);
            Theme.error(this, DBConnection.explain(e, DBConnection.getConfig()));
        }
    }

    private void doClear() {
        usernameField.setText("");
        passwordField.setText("");
        usernameField.requestFocus();
    }

    private void forgotPassword() {
        Theme.info(this, "Passwords are stored as secure hashes, so they cannot be shown.\n\n"
                + "Ask the Owner to reset your password:\nStaff screen > Reset Password.", "Forgot Password");
    }
}
