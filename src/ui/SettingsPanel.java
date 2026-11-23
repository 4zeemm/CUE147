package ui;

import dao.PriceDAO;
import model.GameTable;
import model.Price;
import service.BillingCalculator;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import exception.InvalidInputException;
import util.AppSettings;
import util.Validator;

/** Screen 9 (owner only) - settings in a JTabbedPane: Pricing, Billing Rules, Appearance, Club. */
public class SettingsPanel extends ScreenPanel {
    private final PriceDAO priceDAO = new PriceDAO();
    private final BillingCalculator calculator = new BillingCalculator();

    // Pricing tab
    private final DefaultTableModel priceModel = Theme.tableModel("Service", "Men / hour", "Ladies / hour");
    private final JTable priceTable = new JTable(priceModel);
    private final JComboBox<String> typeBox = Theme.combo(GameTable.POOL, GameTable.SNOOKER, GameTable.PS5);
    private final JSpinner menSpinner = Theme.numberSpinner(270, 10, 5000, 5);
    private final JSpinner ladiesSpinner = Theme.numberSpinner(220, 10, 5000, 5);

    // Billing rules tab
    private final JSpinner calcHours = Theme.numberSpinner(1, 0, 24, 1);
    private final JSpinner calcMinutes = Theme.numberSpinner(6, 0, 59, 1);
    private final JSpinner calcRate = Theme.numberSpinner(300, 10, 5000, 5);
    private final JSpinner calcDiscount = Theme.numberSpinner(0, 0, 100, 5);
    private final JTextArea calcResult = new JTextArea(7, 30);

    // Appearance tab (saved on this computer with AppSettings)
    private final JSlider sizeSlider = new JSlider(12, 16, 14);
    private final JToggleButton notifyToggle = new JToggleButton("Notifications: ON", true);
    private final JComboBox<String> themeBox = Theme.combo(AppSettings.THEMES);
    private final RoundedPanel preview = new RoundedPanel(new BorderLayout());
    private final JLabel previewText = new JLabel("CUE 147 preview text", SwingConstants.CENTER);

    // Club tab
    private final JTextField clubName = Theme.field(20);
    private final JTextField clubPhone = Theme.field(20);
    private final JSpinner openSpinner = Theme.timeSpinner(10, 0);
    private final JSpinner closeSpinner = Theme.timeSpinner(23, 30);

    public SettingsPanel(MainFrame frame) {
        super(frame);
        add(header("Settings", "Prices, billing rules, look and club details"), BorderLayout.NORTH);
        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(Theme.BODY);
        tabs.addTab("Pricing", pricingTab());
        tabs.addTab("Billing Rules", billingTab());
        tabs.addTab("Appearance", appearanceTab());
        tabs.addTab("Club Settings", clubTab());
        add(tabs, BorderLayout.CENTER);
        loadClubValues();
        loadAppearance();
    }

    private JPanel tab(JComponent content, JButton... buttons) {
        RoundedPanel card = new RoundedPanel(new BorderLayout(0, 16));
        card.setBorder(new EmptyBorder(24, 28, 24, 28));
        JPanel wrap = Theme.panel(new BorderLayout());
        wrap.add(content, BorderLayout.NORTH);
        card.add(wrap, BorderLayout.CENTER);
        JPanel row = Theme.panel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        for (JButton b : buttons) row.add(b);
        card.add(row, BorderLayout.SOUTH);
        return card;
    }

    // ---------------- Pricing (stored in the MySQL prices table) ----------------

    private JPanel pricingTab() {
        Theme.styleTable(priceTable);
        priceTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        priceTable.getSelectionModel().addListSelectionListener(e -> { if (!e.getValueIsAdjusting()) fillPriceForm(); });
        typeBox.addActionListener(e -> selectRowOfType());

        JPanel content = Theme.panel(new BorderLayout(0, 16));
        JScrollPane sp = Theme.scroll(priceTable);
        sp.setPreferredSize(new Dimension(0, 140));
        content.add(sp, BorderLayout.NORTH);
        JPanel form = Theme.panel(new GridBagLayout());
        Theme.addRow(form, 0, "Service", typeBox);
        Theme.addRow(form, 1, "Men (₹ / hour)", menSpinner);
        Theme.addRow(form, 2, "Ladies (₹ / hour)", ladiesSpinner);
        content.add(form, BorderLayout.CENTER);
        return tab(content,
                Theme.button("Save Prices", Theme.GREEN, e -> savePrices()),
                Theme.button("Reload", Theme.NEUTRAL, e -> loadPrices()));
    }

    private void loadPrices() {
        try {
            List<Price> prices = priceDAO.findAll();
            priceModel.setRowCount(0);
            for (String type : new String[]{GameTable.POOL, GameTable.SNOOKER, GameTable.PS5}) {
                BigDecimal men = BigDecimal.ZERO, ladies = BigDecimal.ZERO;
                for (Price p : prices) {
                    if (!p.getTableType().equals(type)) continue;
                    if (p.getGender().equals("MEN")) men = p.getHourlyRate(); else ladies = p.getHourlyRate();
                }
                priceModel.addRow(new Object[]{type, Theme.money0(men), Theme.money0(ladies)});
            }
            selectRowOfType();
        } catch (SQLException e) {
            showDbError(e);
        }
    }

    private void selectRowOfType() {
        int row = typeBox.getSelectedIndex();
        if (row >= 0 && row < priceModel.getRowCount()) priceTable.setRowSelectionInterval(row, row);
    }

    private void fillPriceForm() {
        int row = priceTable.getSelectedRow();
        if (row < 0) return;
        typeBox.setSelectedIndex(row);
        menSpinner.setValue(parseMoney(priceModel.getValueAt(row, 1)));
        ladiesSpinner.setValue(parseMoney(priceModel.getValueAt(row, 2)));
    }

    private double parseMoney(Object text) {
        return Double.parseDouble(text.toString().replaceAll("[^0-9.]", ""));
    }

    private void savePrices() {
        String type = (String) typeBox.getSelectedItem();
        BigDecimal men = BigDecimal.valueOf(((Number) menSpinner.getValue()).doubleValue());
        BigDecimal ladies = BigDecimal.valueOf(((Number) ladiesSpinner.getValue()).doubleValue());
        try {
            priceDAO.update(type, "MEN", men);
            priceDAO.update(type, "LADIES", ladies);
            loadPrices();
            frame.setStatus(type + " prices saved");
            Theme.info(this, type + " prices saved:\nMen " + Theme.money0(men) + "  |  Ladies " + Theme.money0(ladies)
                    + "\n\nRunning sessions keep their old price.", "Prices Saved");
        } catch (SQLException e) {
            showDbError(e);
        }
    }

    // ---------------- Billing rules + test calculator ----------------

    private JPanel billingTab() {
        JTextArea rules = new JTextArea("BILLING RULES\n"
                + "1. Minimum billing is one hour.\n"
                + "2. The first 5 minutes after each completed hour are free.\n"
                + "3. After that, every extra minute is charged (rate / 60 per minute).\n"
                + "4. The final bill is rounded DOWN to the nearest Rs. 5.\n"
                + "5. Active members get their discount before rounding.");
        rules.setEditable(false);
        rules.setOpaque(false);
        rules.setForeground(Theme.TEXT);
        rules.setFont(Theme.BODY);

        calcResult.setEditable(false);
        calcResult.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        calcResult.setBackground(Theme.FIELD);
        calcResult.setForeground(Theme.TEXT);
        calcResult.setBorder(new EmptyBorder(8, 10, 8, 10));

        JPanel form = Theme.panel(new GridBagLayout());
        Theme.addRow(form, 0, "Hours played", calcHours);
        Theme.addRow(form, 1, "Extra minutes", calcMinutes);
        Theme.addRow(form, 2, "Hourly rate (₹)", calcRate);
        Theme.addRow(form, 3, "Member discount (%)", calcDiscount);
        Theme.addRow(form, 4, "Result", calcResult);

        JPanel content = Theme.panel(new BorderLayout(0, 12));
        content.add(rules, BorderLayout.NORTH);
        content.add(form, BorderLayout.CENTER);
        return tab(content, Theme.button("Test Calculator", Theme.GREEN, e -> runCalculator()));
    }

    private void runCalculator() {
        long minutes = ((Number) calcHours.getValue()).longValue() * 60 + ((Number) calcMinutes.getValue()).longValue();
        BigDecimal rate = BigDecimal.valueOf(((Number) calcRate.getValue()).doubleValue());
        BigDecimal discount = BigDecimal.valueOf(((Number) calcDiscount.getValue()).doubleValue());
        BigDecimal subtotal = calculator.calculateSubtotal(minutes, rate);
        BigDecimal discountAmount = calculator.calculateDiscount(subtotal, discount);
        BigDecimal total = calculator.calculateFinalAmount(minutes, rate, discount);
        calcResult.setText(String.format("Total minutes : %d%nSubtotal      : %s%nDiscount      : %s%nFINAL (round down to 5): %s",
                minutes, Theme.money(subtotal), Theme.money(discountAmount), Theme.money(total)));
    }

    // ---------------- Appearance (saved; used the next time the program starts) ----------------

    private JPanel appearanceTab() {
        sizeSlider.setOpaque(false);
        sizeSlider.setMajorTickSpacing(1);
        sizeSlider.setSnapToTicks(true);
        sizeSlider.setPaintTicks(true);
        sizeSlider.setPaintLabels(true);
        sizeSlider.setForeground(Theme.MUTED);
        notifyToggle.setFont(Theme.BOLD);
        notifyToggle.setForeground(Color.WHITE);
        notifyToggle.setFocusPainted(false);

        preview.setBorder(new EmptyBorder(14, 14, 14, 14));
        preview.setPreferredSize(new Dimension(300, 70));
        preview.add(previewText, BorderLayout.CENTER);

        sizeSlider.addChangeListener(e -> updatePreview());
        themeBox.addActionListener(e -> updatePreview());
        notifyToggle.addActionListener(e -> {
            boolean on = notifyToggle.isSelected();
            AppSettings.setNotificationsOn(on);   // saved at once, works at once
            showToggle();
            frame.refreshNotifications();
            frame.setStatus("Notifications switched " + (on ? "on" : "off"));
        });

        JPanel form = Theme.panel(new GridBagLayout());
        Theme.addRow(form, 0, "Theme colour", themeBox);
        Theme.addRow(form, 1, "Text size", sizeSlider);
        Theme.addRow(form, 2, "Notifications", notifyToggle);
        Theme.addRow(form, 3, "Preview", preview);
        Theme.addRow(form, 4, "", Theme.label("Colour and text size are used the next time CUE 147 starts.", Theme.SMALL, Theme.MUTED));
        return tab(form,
                Theme.button("Apply", Theme.GREEN, e -> {
                    AppSettings.setTheme((String) themeBox.getSelectedItem());
                    AppSettings.setFontSize(sizeSlider.getValue());
                    frame.setStatus("Appearance saved");
                    Theme.info(this, "Saved: " + themeBox.getSelectedItem() + ", text size " + sizeSlider.getValue()
                            + ".\nClose CUE 147 and start it again to see the new look.", "Appearance");
                }),
                Theme.button("Reset", Theme.NEUTRAL, e -> {
                    themeBox.setSelectedIndex(0);
                    sizeSlider.setValue(14);
                    frame.setStatus("Normal look selected - press Apply to save it");
                }));
    }

    /** Shows the saved appearance settings in the controls. */
    private void loadAppearance() {
        themeBox.setSelectedItem(AppSettings.getTheme());
        sizeSlider.setValue(AppSettings.getFontSize());
        notifyToggle.setSelected(AppSettings.isNotificationsOn());
        showToggle();
        updatePreview();
    }

    private void showToggle() {
        boolean on = notifyToggle.isSelected();
        notifyToggle.setText("Notifications: " + (on ? "ON" : "OFF"));
        notifyToggle.setBackground(on ? Theme.GREEN : Theme.RED);
    }

    private void updatePreview() {
        preview.setFill(Theme.accentFor((String) themeBox.getSelectedItem()));
        previewText.setForeground(Color.WHITE);
        previewText.setFont(new Font("SansSerif", Font.BOLD, sizeSlider.getValue()));
    }

    // ---------------- Club settings (saved on this computer; name and phone are printed on bills) ----------------

    private JPanel clubTab() {
        JPanel form = Theme.panel(new GridBagLayout());
        Theme.addRow(form, 0, "Club name", clubName);
        Theme.addRow(form, 1, "Phone", clubPhone);
        Theme.addRow(form, 2, "Opening time", openSpinner);
        Theme.addRow(form, 3, "Closing time", closeSpinner);
        Theme.addRow(form, 4, "", Theme.label("The club name and phone are printed at the top of every bill.", Theme.SMALL, Theme.MUTED));
        return tab(form,
                Theme.button("Save", Theme.GREEN, e -> {
                    try {
                        AppSettings.setClubName(Validator.requireLabel(clubName.getText(), "Club name"));
                        AppSettings.setClubPhone(clubPhone.getText().trim());
                        AppSettings.setOpenTime(Theme.timeText(openSpinner));
                        AppSettings.setCloseTime(Theme.timeText(closeSpinner));
                        frame.setStatus("Club settings saved");
                        Theme.info(this, "Club settings saved on this computer.", "Settings");
                    } catch (InvalidInputException ex) {
                        Theme.error(this, ex.getMessage());
                    }
                }),
                Theme.button("Reset", Theme.NEUTRAL, e -> { loadClubValues(); frame.setStatus("Club settings reset"); }));
    }

    private void loadClubValues() {
        clubName.setText(AppSettings.getClubName());
        clubPhone.setText(AppSettings.getClubPhone());
        openSpinner.setValue(timeValue(AppSettings.getOpenTime(), 10, 0));
        closeSpinner.setValue(timeValue(AppSettings.getCloseTime(), 23, 30));
    }

    /** Turns "18:30" into a value for a time spinner (or the default time if the text is wrong). */
    private Object timeValue(String text, int defaultHour, int defaultMinute) {
        try {
            String[] parts = text.split(":");
            return Theme.timeSpinner(Integer.parseInt(parts[0]), Integer.parseInt(parts[1])).getValue();
        } catch (RuntimeException e) {
            return Theme.timeSpinner(defaultHour, defaultMinute).getValue();
        }
    }

    @Override
    public void refresh() {
        loadPrices();
        loadClubValues();
        loadAppearance();
    }
}
