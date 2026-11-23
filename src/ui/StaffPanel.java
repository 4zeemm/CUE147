package ui;

import dao.ShiftDAO;
import dao.UserDAO;
import model.Shift;
import model.User;
import service.AuthService;
import exception.ClubException;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Screen - staff shifts (everyone) and user accounts (owner only).
 * The permission check is user.canManageStaff(): the Owner object says yes, the Staff object says no.
 */
public class StaffPanel extends ScreenPanel {
    private final UserDAO userDAO = new UserDAO();
    private final ShiftDAO shiftDAO = new ShiftDAO();
    private final AuthService authService = new AuthService();
    private final boolean isOwner;

    private final DefaultTableModel userModel = Theme.tableModel("Username", "Full name", "Role", "Status");
    private final JTable userTable = new JTable(userModel);
    private final List<User> users = new ArrayList<>();
    private final DefaultTableModel shiftModel = Theme.tableModel("Staff", "Start", "End", "Hours");
    private final JLabel shiftStatus = Theme.label("", Theme.HEADING, Theme.GREEN);

    private final JTextField usernameField = Theme.field(14);
    private final JTextField fullNameField = Theme.field(14);
    private final JPasswordField passwordField = new JPasswordField(14);
    private final JRadioButton staffRadio = Theme.radio("Staff");
    private final JRadioButton ownerRadio = Theme.radio("Owner");

    public StaffPanel(MainFrame frame) {
        super(frame);
        isOwner = frame.getUser().canManageStaff();
        add(header("Staff", isOwner ? "User accounts and work shifts" : "Your work shift",
                Theme.button("Change My Password", Theme.BLUE, e -> changeMyPassword()),
                Theme.button("Refresh", Theme.NEUTRAL, e -> refresh())), BorderLayout.NORTH);

        JPanel centre = Theme.panel(new GridLayout(1, isOwner ? 2 : 1, 16, 0));
        if (isOwner) centre.add(buildUsersCard());
        centre.add(buildShiftCard());
        add(centre, BorderLayout.CENTER);
    }

    private JPanel buildUsersCard() {
        RoundedPanel card = new RoundedPanel(new BorderLayout(0, 10));
        card.setBorder(new EmptyBorder(16, 16, 16, 16));
        card.add(Theme.label("User Accounts", Theme.HEADING, Theme.TEXT), BorderLayout.NORTH);
        Theme.styleTable(userTable);
        userTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        Theme.styleField(passwordField);
        passwordField.setEchoChar('•');
        ButtonGroup group = new ButtonGroup();
        group.add(staffRadio);
        group.add(ownerRadio);
        staffRadio.setSelected(true);
        JPanel roles = Theme.panel(new GridLayout(1, 2));
        roles.add(staffRadio);
        roles.add(ownerRadio);
        JPanel form = Theme.panel(new GridBagLayout());
        Theme.addRow(form, 0, "Username", usernameField);
        Theme.addRow(form, 1, "Full name", fullNameField);
        Theme.addRow(form, 2, "Password", passwordField);
        Theme.addRow(form, 3, "Role", roles);

        JPanel buttons = Theme.panel(new GridLayout(1, 3, 8, 0));
        buttons.add(Theme.button("Add User", Theme.GREEN, e -> addUser()));
        buttons.add(Theme.button("Reset Password", Theme.BLUE, e -> resetPassword()));
        buttons.add(Theme.button("Activate/Block", Theme.AMBER, e -> toggleActive()));
        for (Component c : buttons.getComponents()) ((JComponent) c).setBorder(new EmptyBorder(8, 4, 8, 4));

        JPanel south = Theme.panel(new BorderLayout(0, 10));
        south.add(form, BorderLayout.CENTER);
        south.add(buttons, BorderLayout.SOUTH);
        card.add(Theme.scroll(userTable), BorderLayout.CENTER);
        card.add(south, BorderLayout.SOUTH);
        return card;
    }

    private JPanel buildShiftCard() {
        RoundedPanel card = new RoundedPanel(new BorderLayout(0, 10));
        card.setBorder(new EmptyBorder(16, 16, 16, 16));
        JPanel top = Theme.panel(new BorderLayout());
        top.add(Theme.label("Work Shifts", Theme.HEADING, Theme.TEXT), BorderLayout.WEST);
        top.add(shiftStatus, BorderLayout.EAST);
        card.add(top, BorderLayout.NORTH);
        JTable shiftTable = new JTable(shiftModel);
        Theme.styleTable(shiftTable);
        card.add(Theme.scroll(shiftTable), BorderLayout.CENTER);
        JPanel buttons = Theme.panel(new GridLayout(1, 2, 10, 0));
        buttons.add(Theme.button("Start My Shift", Theme.GREEN, e -> startShift()));
        buttons.add(Theme.button("End My Shift", Theme.RED, e -> endShift()));
        card.add(buttons, BorderLayout.SOUTH);
        return card;
    }

    @Override
    public void refresh() {
        try {
            if (isOwner) {
                users.clear();
                users.addAll(userDAO.findAll());
                userModel.setRowCount(0);
                for (User u : users) {
                    userModel.addRow(new Object[]{u.getUsername(), u.getFullName(), u.getRole(), u.isActive() ? "Active" : "Blocked"});
                }
            }
            shiftModel.setRowCount(0);
            DateTimeFormatter f = DateTimeFormatter.ofPattern("dd MMM HH:mm");
            // the owner sees everybody's shifts (id 0 = all), staff only their own
            for (Shift s : shiftDAO.findRecent(isOwner ? 0 : frame.getUser().getId())) {
                shiftModel.addRow(new Object[]{s.getUserName(), s.getStart().format(f),
                        s.getEnd() == null ? "-" : s.getEnd().format(f), s.getHoursText()});
            }
            boolean open = shiftDAO.hasOpenShift(frame.getUser().getId());
            shiftStatus.setText(open ? "Your shift is RUNNING" : "No shift running");
            shiftStatus.setForeground(open ? Theme.GREEN : Theme.MUTED);
        } catch (SQLException e) {
            showDbError(e);
        }
    }

    /** Every user (owner or staff) can change their own password here. */
    private void changeMyPassword() {
        JPasswordField current = new JPasswordField(), fresh = new JPasswordField(), repeat = new JPasswordField();
        for (JPasswordField f : new JPasswordField[]{current, fresh, repeat}) Theme.styleField(f);
        Object[] form = {"Current password:", current, "New password (at least 6 characters):", fresh, "New password again:", repeat};
        int answer = JOptionPane.showConfirmDialog(this, form, "Change My Password", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (answer != JOptionPane.OK_OPTION) return;
        try {
            authService.changeOwnPassword(frame.getUser(), new String(current.getPassword()),
                    new String(fresh.getPassword()), new String(repeat.getPassword()));
            frame.setStatus("Password changed");
            Theme.info(this, "Your password has been changed.\nUse the new password next time you log in.", "Password Changed");
        } catch (ClubException e) {
            Theme.error(this, e.getMessage());
        } catch (SQLException e) {
            showDbError(e);
        }
    }

    private void startShift() {
        try {
            if (shiftDAO.hasOpenShift(frame.getUser().getId())) {
                Theme.info(this, "Your shift is already running.", "Start Shift");
                return;
            }
            shiftDAO.startShift(frame.getUser().getId(), LocalDateTime.now());
            refresh();
            frame.setStatus("Shift started");
        } catch (SQLException e) {
            showDbError(e);
        }
    }

    private void endShift() {
        try {
            if (!shiftDAO.hasOpenShift(frame.getUser().getId())) {
                Theme.info(this, "You have no running shift.", "End Shift");
                return;
            }
            shiftDAO.endShift(frame.getUser().getId(), LocalDateTime.now());
            refresh();
            frame.setStatus("Shift ended");
        } catch (SQLException e) {
            showDbError(e);
        }
    }

    private void addUser() {
        try {
            authService.addUser(usernameField.getText().trim(), fullNameField.getText().trim(),
                    new String(passwordField.getPassword()), ownerRadio.isSelected() ? "OWNER" : "STAFF");
            usernameField.setText("");
            fullNameField.setText("");
            passwordField.setText("");
            refresh();
            frame.setStatus("User added");
            Theme.info(this, "User account created.", "User Added");
        } catch (ClubException e) {
            Theme.error(this, e.getMessage());
        } catch (SQLException e) {
            showDbError(e);
        }
    }

    private User selectedUser() {
        int row = userTable.getSelectedRow();
        if (row < 0 || row >= users.size()) {
            Theme.error(this, "Select a user in the table first.");
            return null;
        }
        return users.get(row);
    }

    private void resetPassword() {
        User u = selectedUser();
        if (u == null) return;
        JPasswordField pf = new JPasswordField();
        Theme.styleField(pf);
        int answer = JOptionPane.showConfirmDialog(this, new Object[]{"New password for " + u.getUsername() + " (min 6 characters):", pf},
                "Reset Password", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (answer != JOptionPane.OK_OPTION) return;
        try {
            authService.resetPassword(u.getId(), new String(pf.getPassword()));
            frame.setStatus("Password reset for " + u.getUsername());
            Theme.info(this, "Password changed.", "Reset Password");
        } catch (ClubException e) {
            Theme.error(this, e.getMessage());
        } catch (SQLException e) {
            showDbError(e);
        }
    }

    private void toggleActive() {
        User u = selectedUser();
        if (u == null) return;
        if (u.getId() == frame.getUser().getId()) {
            Theme.error(this, "You cannot block your own account.");
            return;
        }
        try {
            userDAO.setActive(u.getId(), !u.isActive());
            refresh();
            frame.setStatus(u.getUsername() + (u.isActive() ? " blocked" : " activated"));
        } catch (SQLException e) {
            showDbError(e);
        }
    }
}
