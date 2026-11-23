package ui;

import dao.PriceDAO;
import dao.MembershipPlanDAO;
import dao.MenuItemDAO;
import dao.StaffMemberDAO;
import model.MembershipPlan;
import model.MenuItem;
import model.GameTable;
import model.Price;
import model.StaffMember;
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

/** Screen 9 (owner only) - settings in a JTabbedPane: Pricing, Billing Rules, Appearance, Club, Staff List. */
public class SettingsPanel extends ScreenPanel {
    // Staff List tab: the names shown in "Billed by" at the checkout
    private final StaffMemberDAO staffDAO = new StaffMemberDAO();
    private final DefaultTableModel staffModel = Theme.tableModel("Name", "Status");
    private final JTable staffTable = new JTable(staffModel);
    private final List<StaffMember> staffShown = new java.util.ArrayList<>();
    private final JTextField staffName = Theme.field(20);

    // Membership Plans tab
    private final MembershipPlanDAO planDAO = new MembershipPlanDAO();
    private final DefaultTableModel planModel = Theme.tableModel("Plan", "Months", "Price", "Member discount");
    private final JTable planTable = new JTable(planModel);
    private final List<MembershipPlan> plansShown = new java.util.ArrayList<>();
    private final JSpinner planMonths = Theme.styleSpinner(new JSpinner(new SpinnerNumberModel(1, 1, 36, 1)));
    private final JSpinner planPrice = Theme.numberSpinner(0, 0, 1_000_000, 50);
    private final JSpinner planDiscount = Theme.numberSpinner(0, 0, 100, 1);

    // Food & Drinks Menu tab
    private final MenuItemDAO menuDAO = new MenuItemDAO();
    private final DefaultTableModel menuModel = Theme.tableModel("Item", "Category", "Price", "Available");
    private final JTable menuTable = new JTable(menuModel);
    private final List<MenuItem> menuShown = new java.util.ArrayList<>();
    private final JTextField itemName = Theme.field(20);
    private final JComboBox<String> itemCategory = Theme.combo("Coffee", "Beverages", "Snacks", "Food", "Other");
    private final JSpinner itemPrice = Theme.numberSpinner(0, 0, 100_000, 5);

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
        tabs.addTab("Staff List", staffTab());
        tabs.addTab("Membership Plans", plansTab());
        tabs.addTab("Food & Drinks Menu", menuTab());
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
        loadStaff();
        loadPlans();
        loadMenu();
        loadPrices();
        loadClubValues();
        loadAppearance();
    }

    // ---------------- Staff List (MySQL table staff_members; used by "Billed by") ----------------

    private JPanel staffTab() {
        Theme.styleTable(staffTable);
        staffTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JPanel content = Theme.panel(new BorderLayout(0, 14));
        JScrollPane sp = Theme.scroll(staffTable);
        sp.setPreferredSize(new Dimension(0, 200));
        content.add(sp, BorderLayout.NORTH);
        JPanel form = Theme.panel(new GridBagLayout());
        Theme.addRow(form, 0, "New name", staffName);
        Theme.addRow(form, 1, "", Theme.label("These names appear in \"Billed by\" at the checkout. People who leave are switched off, "
                + "so their old bills keep their name.", Theme.SMALL, Theme.MUTED));
        content.add(form, BorderLayout.CENTER);
        staffName.addActionListener(e -> addStaff());
        return tab(content,
                Theme.button("Add Name", Theme.GREEN, e -> addStaff()),
                Theme.button("Switch On / Off", Theme.NEUTRAL, e -> toggleStaff()));
    }

    private void loadStaff() {
        try {
            staffShown.clear();
            staffShown.addAll(staffDAO.findAll());
            staffModel.setRowCount(0);
            for (StaffMember m : staffShown) staffModel.addRow(new Object[]{m.getName(), m.isActive() ? "Active" : "Switched off"});
        } catch (SQLException e) {
            showDbError(e);
        }
    }

    private void addStaff() {
        try {
            String name = Validator.requireName(staffName.getText());
            if (name.length() > 40) throw new InvalidInputException("A name can have at most 40 characters.");
            staffDAO.add(name);
            staffName.setText("");
            loadStaff();
            frame.setStatus(name + " added to the staff list");
        } catch (InvalidInputException e) {
            Theme.error(this, e.getMessage());
        } catch (SQLException e) {
            if (e.getErrorCode() == 1062) Theme.error(this, "This name is already in the list.");
            else showDbError(e);
        }
    }

    private void toggleStaff() {
        int row = staffTable.getSelectedRow();
        if (row < 0) { Theme.error(this, "Select a name in the list first."); return; }
        StaffMember m = staffShown.get(row);
        try {
            staffDAO.setActive(m.getId(), !m.isActive());
            loadStaff();
            frame.setStatus(m.getName() + (m.isActive() ? " switched off" : " switched on"));
        } catch (SQLException e) {
            showDbError(e);
        }
    }

    // ---------------- Membership Plans (MySQL table membership_plans) ----------------

    private JPanel plansTab() {
        Theme.styleTable(planTable);
        planTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        planTable.getSelectionModel().addListSelectionListener(e -> { if (!e.getValueIsAdjusting()) fillPlanForm(); });
        planMonths.setName("planMonths");
        planPrice.setName("planPrice");
        planDiscount.setName("planDiscount");
        JPanel content = Theme.panel(new BorderLayout(0, 14));
        JScrollPane sp = Theme.scroll(planTable);
        sp.setPreferredSize(new Dimension(0, 120));
        content.add(sp, BorderLayout.NORTH);
        JPanel form = Theme.panel(new GridBagLayout());
        Theme.addRow(form, 0, "Months", planMonths);
        Theme.addRow(form, 1, "Price (₹)", planPrice);
        Theme.addRow(form, 2, "Discount on games (%)", planDiscount);
        Theme.addRow(form, 3, "", Theme.label("Select a plan, change it and press Save Plan. A plan with price ₹0 cannot be sold.",
                Theme.SMALL, Theme.MUTED));
        content.add(form, BorderLayout.CENTER);
        return tab(content, Theme.button("Save Plan", Theme.GREEN, e -> savePlan()));
    }

    private void loadPlans() {
        try {
            int selected = planTable.getSelectedRow();
            plansShown.clear();
            plansShown.addAll(planDAO.findAll());
            planModel.setRowCount(0);
            for (MembershipPlan p : plansShown) {
                planModel.addRow(new Object[]{p.getName(), p.getMonths(), Theme.money0(p.getPrice()),
                        p.getDiscountPercent().stripTrailingZeros().toPlainString() + "%"});
            }
            if (selected >= 0 && selected < plansShown.size()) planTable.setRowSelectionInterval(selected, selected);
        } catch (SQLException e) {
            showDbError(e);
        }
    }

    private void fillPlanForm() {
        int row = planTable.getSelectedRow();
        if (row < 0 || row >= plansShown.size()) return;
        MembershipPlan p = plansShown.get(row);
        planMonths.setValue(p.getMonths());
        planPrice.setValue(p.getPrice().doubleValue());
        planDiscount.setValue(p.getDiscountPercent().doubleValue());
    }

    private void savePlan() {
        int row = planTable.getSelectedRow();
        if (row < 0) { Theme.error(this, "Select a plan in the table first."); return; }
        MembershipPlan p = plansShown.get(row);
        BigDecimal price = BigDecimal.valueOf(((Number) planPrice.getValue()).doubleValue()).setScale(2, java.math.RoundingMode.HALF_UP);
        BigDecimal discount = BigDecimal.valueOf(((Number) planDiscount.getValue()).doubleValue()).setScale(2, java.math.RoundingMode.HALF_UP);
        int months = ((Number) planMonths.getValue()).intValue();
        if (price.signum() <= 0) { Theme.error(this, "The price must be more than ₹0."); return; }
        if (discount.compareTo(new BigDecimal("100")) > 0) { Theme.error(this, "The discount cannot be more than 100%."); return; }
        try {
            planDAO.update(p.getId(), months, price, discount);
            loadPlans();
            frame.setStatus(p.getName() + " plan saved");
            Theme.info(this, p.getName() + " plan: " + months + " month(s), " + Theme.money0(price) + ", "
                    + discount.stripTrailingZeros().toPlainString() + "% off games.\nNew sales use these values; memberships already sold do not change.", "Plan Saved");
        } catch (SQLException e) {
            showDbError(e);
        }
    }

    // ---------------- Food & Drinks Menu (MySQL table menu_items) ----------------

    private JPanel menuTab() {
        Theme.styleTable(menuTable);
        menuTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        menuTable.getSelectionModel().addListSelectionListener(e -> { if (!e.getValueIsAdjusting()) fillMenuForm(); });
        itemCategory.setEditable(true);   // any category can be typed
        itemPrice.setName("itemPrice");
        JPanel content = Theme.panel(new BorderLayout(0, 14));
        JScrollPane sp = Theme.scroll(menuTable);
        sp.setPreferredSize(new Dimension(0, 200));
        content.add(sp, BorderLayout.NORTH);
        JPanel form = Theme.panel(new GridBagLayout());
        Theme.addRow(form, 0, "Item name", itemName);
        Theme.addRow(form, 1, "Category", itemCategory);
        Theme.addRow(form, 2, "Price (₹)", itemPrice);
        Theme.addRow(form, 3, "", Theme.label("Items that are sold out or no longer sold are switched off, not deleted. "
                + "A price change does not change old bills.", Theme.SMALL, Theme.MUTED));
        content.add(form, BorderLayout.CENTER);
        return tab(content,
                Theme.button("Add Item", Theme.GREEN, e -> addMenuItem()),
                Theme.button("Save Changes", Theme.BLUE, e -> updateMenuItem()),
                Theme.button("Available On / Off", Theme.NEUTRAL, e -> toggleMenuItem()));
    }

    private void loadMenu() {
        try {
            menuShown.clear();
            menuShown.addAll(menuDAO.findAll());
            menuModel.setRowCount(0);
            for (MenuItem m : menuShown) menuModel.addRow(new Object[]{m.getName(), m.getCategory(), Theme.money(m.getPrice()), m.isAvailable() ? "Yes" : "No"});
        } catch (SQLException e) {
            showDbError(e);
        }
    }

    private void fillMenuForm() {
        int row = menuTable.getSelectedRow();
        if (row < 0 || row >= menuShown.size()) return;
        MenuItem m = menuShown.get(row);
        itemName.setText(m.getName());
        itemCategory.setSelectedItem(m.getCategory());
        itemPrice.setValue(m.getPrice().doubleValue());
    }

    /** Checks the three boxes; returns null after showing a message when something is wrong. */
    private Object[] readMenuForm() {
        try {
            String name = Validator.requireLabel(itemName.getText(), "Item name");
            if (name.length() > 50) throw new InvalidInputException("The item name can have at most 50 characters.");
            String category = Validator.requireLabel(String.valueOf(itemCategory.getSelectedItem() == null ? "" : itemCategory.getSelectedItem()), "Category");
            if (category.length() > 30) throw new InvalidInputException("The category can have at most 30 characters.");
            BigDecimal price = BigDecimal.valueOf(((Number) itemPrice.getValue()).doubleValue()).setScale(2, java.math.RoundingMode.HALF_UP);
            if (price.signum() <= 0) throw new InvalidInputException("The price must be more than ₹0.");
            return new Object[]{name, category, price};
        } catch (InvalidInputException e) {
            Theme.error(this, e.getMessage());
            return null;
        }
    }

    private void addMenuItem() {
        Object[] v = readMenuForm();
        if (v == null) return;
        try {
            menuDAO.add((String) v[0], (String) v[1], (BigDecimal) v[2]);
            itemName.setText("");
            loadMenu();
            frame.setStatus(v[0] + " added to the menu");
        } catch (SQLException e) {
            if (e.getErrorCode() == 1062) Theme.error(this, "This item is already on the menu.");
            else showDbError(e);
        }
    }

    private void updateMenuItem() {
        int row = menuTable.getSelectedRow();
        if (row < 0) { Theme.error(this, "Select an item in the menu first."); return; }
        Object[] v = readMenuForm();
        if (v == null) return;
        try {
            menuDAO.update(menuShown.get(row).getId(), (String) v[0], (String) v[1], (BigDecimal) v[2]);
            loadMenu();
            frame.setStatus(v[0] + " saved");
        } catch (SQLException e) {
            if (e.getErrorCode() == 1062) Theme.error(this, "Another item already has this name.");
            else showDbError(e);
        }
    }

    private void toggleMenuItem() {
        int row = menuTable.getSelectedRow();
        if (row < 0) { Theme.error(this, "Select an item in the menu first."); return; }
        MenuItem m = menuShown.get(row);
        try {
            menuDAO.setAvailable(m.getId(), !m.isAvailable());
            loadMenu();
            frame.setStatus(m.getName() + (m.isAvailable() ? " switched off" : " available again"));
        } catch (SQLException e) {
            showDbError(e);
        }
    }
}
