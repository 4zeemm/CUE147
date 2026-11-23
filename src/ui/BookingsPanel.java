package ui;

import dao.BookingDAO;
import dao.CustomerDAO;
import dao.GameTableDAO;
import model.Booking;
import model.Customer;
import model.GameTable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import exception.InvalidInputException;
import util.Validator;
import java.util.ArrayList;
import java.util.List;

/** Screen 5 - advance bookings saved in MySQL. */
public class BookingsPanel extends ScreenPanel {
    private final BookingDAO bookingDAO = new BookingDAO();
    private final CustomerDAO customerDAO = new CustomerDAO();
    private final GameTableDAO tableDAO = new GameTableDAO();

    private final DefaultTableModel model = Theme.tableModel("ID", "Customer", "Table", "Date", "Time", "Hours", "Source", "Status");
    private final JTable table = new JTable(model);
    private final List<Booking> shown = new ArrayList<>();

    private final JComboBox<Customer> customerBox = new JComboBox<>();
    private final JComboBox<GameTable> tableBox = new JComboBox<>();
    private final JTextField dateField = Theme.field(12);
    private final JSpinner timeSpinner = Theme.timeSpinner(19, 0);
    private final JSpinner durationSpinner = Theme.numberSpinner(1, 0.5, 8, 0.5);
    private final JRadioButton phoneRadio = Theme.radio("Phone");
    private final JRadioButton walkInRadio = Theme.radio("Walk-in");
    private final JRadioButton onlineRadio = Theme.radio("Online");

    public BookingsPanel(MainFrame frame) {
        super(frame);
        add(header("Bookings", "Reserve a table for a customer"), BorderLayout.NORTH);
        for (JComboBox<?> box : new JComboBox<?>[]{customerBox, tableBox}) {
            box.setFont(Theme.BODY);
            box.setBackground(Theme.FIELD);
            box.setForeground(Theme.TEXT);
        }

        Theme.styleTable(table);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JPanel centre = Theme.panel(new BorderLayout(16, 0));
        centre.add(buildForm(), BorderLayout.WEST);
        centre.add(Theme.scroll(table), BorderLayout.CENTER);
        add(centre, BorderLayout.CENTER);
        resetFields();
    }

    private JPanel buildForm() {
        RoundedPanel card = new RoundedPanel(new BorderLayout(0, 10));
        card.setBorder(new EmptyBorder(16, 16, 16, 16));
        card.setPreferredSize(new Dimension(360, 0));
        card.add(Theme.label("New Booking", Theme.HEADING, Theme.TEXT), BorderLayout.NORTH);

        ButtonGroup group = new ButtonGroup();
        group.add(phoneRadio);
        group.add(walkInRadio);
        group.add(onlineRadio);
        JPanel sourcePanel = Theme.panel(new GridLayout(1, 3));
        sourcePanel.add(phoneRadio);
        sourcePanel.add(walkInRadio);
        sourcePanel.add(onlineRadio);

        JPanel form = Theme.panel(new GridBagLayout());
        Theme.addRow(form, 0, "Customer", customerBox);
        Theme.addRow(form, 1, "Table", tableBox);
        Theme.addRow(form, 2, "Date", dateField);
        Theme.addRow(form, 3, "Time", timeSpinner);
        Theme.addRow(form, 4, "Duration (hrs)", durationSpinner);
        Theme.addRow(form, 5, "Booked via", sourcePanel);
        Theme.addRow(form, 6, "", Theme.label("Date format: yyyy-MM-dd", Theme.SMALL, Theme.MUTED));
        JPanel wrap = Theme.panel(new BorderLayout());
        wrap.add(form, BorderLayout.NORTH);
        card.add(wrap, BorderLayout.CENTER);

        JPanel buttons = Theme.panel(new GridLayout(1, 3, 8, 0));
        buttons.add(Theme.button("Book Table", Theme.GREEN, e -> bookTable()));
        buttons.add(Theme.button("Cancel Booking", Theme.RED, e -> cancelBooking()));
        buttons.add(Theme.button("Clear", Theme.NEUTRAL, e -> resetFields()));
        for (Component c : buttons.getComponents()) ((JComponent) c).setBorder(new EmptyBorder(8, 4, 8, 4));
        card.add(buttons, BorderLayout.SOUTH);
        return card;
    }

    @Override
    public void refresh() {
        try {
            int oldCustomer = customerBox.getSelectedItem() == null ? -1 : ((Customer) customerBox.getSelectedItem()).getId();
            int oldTable = tableBox.getSelectedItem() == null ? -1 : ((GameTable) tableBox.getSelectedItem()).getId();
            customerBox.removeAllItems();
            for (Customer c : customerDAO.findAll()) {
                customerBox.addItem(c);
                if (c.getId() == oldCustomer) customerBox.setSelectedItem(c);
            }
            tableBox.removeAllItems();
            for (GameTable t : tableDAO.findAll()) {
                tableBox.addItem(t);
                if (t.getId() == oldTable) tableBox.setSelectedItem(t);
            }
            shown.clear();
            shown.addAll(bookingDAO.findAll());
        } catch (SQLException e) {
            showDbError(e);
            return;
        }
        model.setRowCount(0);
        for (Booking b : shown) {
            model.addRow(new Object[]{b.getDisplayId(), b.getCustomerName(), b.getTableName(), b.getDate(), b.getTime(),
                    b.getDurationHours(), b.getSource(), b.getStatus()});
        }
    }

    private void bookTable() {
        if (customerBox.getSelectedItem() == null || tableBox.getSelectedItem() == null) {
            Theme.error(this, "Choose a customer and a table.");
            return;
        }
        LocalDate date;
        try {
            date = Validator.requireDate(dateField.getText());
            Validator.requireNotPast(date);
        } catch (InvalidInputException ex) {
            Theme.error(this, ex.getMessage());
            return;
        }

        Customer customer = (Customer) customerBox.getSelectedItem();
        GameTable gameTable = (GameTable) tableBox.getSelectedItem();
        java.time.LocalTime time = Theme.timeOf(timeSpinner);
        BigDecimal hours = BigDecimal.valueOf(((Number) durationSpinner.getValue()).doubleValue());
        String source = phoneRadio.isSelected() ? "PHONE" : walkInRadio.isSelected() ? "WALK_IN" : "ONLINE";
        try {
            if (bookingDAO.isTableBooked(gameTable.getId(), date, time, hours)) {   // overlapping times are not allowed
                Theme.error(this, gameTable.getName() + " already has a booking that overlaps " + time + " (" + hours + " h) on " + date + ".");
                return;
            }
            int id = bookingDAO.add(customer.getId(), gameTable.getId(), date, time, hours, source);
            refresh();
            frame.setStatus("Booking B" + String.format("%03d", id) + " created");
            Theme.info(this, "Booking confirmed!\n\nCustomer: " + customer.getName() + "\nTable: " + gameTable.getName()
                    + "\nWhen: " + date + " at " + time + " for " + hours + " hr(s)", "Booking Confirmed");
        } catch (SQLException e) {
            showDbError(e);
        }
    }

    private void cancelBooking() {
        int row = table.getSelectedRow();
        if (row < 0) { Theme.error(this, "Select a booking in the table first."); return; }
        Booking b = shown.get(row);
        if (b.getStatus().equals("CANCELLED")) { Theme.info(this, "This booking is already cancelled.", "Cancel Booking"); return; }
        if (Theme.confirm(this, "Cancel booking " + b.getDisplayId() + " for " + b.getCustomerName() + "?", "Cancel Booking")) {
            try {
                bookingDAO.cancel(b.getId());
                refresh();
                frame.setStatus("Booking " + b.getDisplayId() + " cancelled");
            } catch (SQLException e) {
                showDbError(e);
            }
        }
    }

    private void resetFields() {
        if (customerBox.getItemCount() > 0) customerBox.setSelectedIndex(0);
        if (tableBox.getItemCount() > 0) tableBox.setSelectedIndex(0);
        dateField.setText(LocalDate.now().toString());
        timeSpinner.setValue(Theme.timeSpinner(19, 0).getValue());
        durationSpinner.setValue(1.0);
        phoneRadio.setSelected(true);
        table.clearSelection();
    }
}
