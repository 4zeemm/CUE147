package ui;

import dao.OrderItemDAO;
import model.MenuItem;
import model.OrderItem;
import util.AppLogger;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Food & drinks of a RUNNING game. Everything added here appears on the same bill as the table time. */
public class FoodDrinksDialog extends JDialog {
    private final OrderItemDAO orderDAO = new OrderItemDAO();
    private final int sessionId;
    private final DefaultTableModel model = Theme.tableModel("Item", "Qty", "Amount", "Ordered at");
    private final JTable table = new JTable(model);
    private final List<OrderItem> shown = new ArrayList<>();
    private final JLabel totalLabel = Theme.label("", Theme.HEADING, Theme.GOLD);

    private FoodDrinksDialog(Window owner, String tableName, String player, int sessionId) {
        super(owner, "Food & Drinks - " + tableName, ModalityType.APPLICATION_MODAL);
        this.sessionId = sessionId;
        Theme.styleTable(table);
        table.getColumnModel().getColumn(0).setPreferredWidth(220);

        JPanel root = Theme.panel(new BorderLayout(0, 12));
        root.setBorder(new EmptyBorder(16, 18, 14, 18));
        root.add(Theme.label(tableName + "  -  " + player, Theme.HEADING, Theme.TEXT), BorderLayout.NORTH);
        root.add(Theme.scroll(table), BorderLayout.CENTER);

        JPanel south = Theme.panel(new BorderLayout(0, 10));
        south.add(new ItemAdder(this::addItem), BorderLayout.NORTH);
        JPanel bottom = Theme.panel(new BorderLayout());
        bottom.add(totalLabel, BorderLayout.WEST);
        JPanel buttons = Theme.panel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(Theme.button("Remove Item", Theme.RED, e -> removeItem()));
        buttons.add(Theme.button("Close", Theme.NEUTRAL, e -> dispose()));
        bottom.add(buttons, BorderLayout.EAST);
        south.add(bottom, BorderLayout.SOUTH);
        root.add(south, BorderLayout.SOUTH);
        setContentPane(root);
        load();
        setSize(620, 460);
        setLocationRelativeTo(owner);
    }

    public static void open(Window owner, String tableName, String player, int sessionId) {
        new FoodDrinksDialog(owner, tableName, player, sessionId).setVisible(true);
    }

    private void load() {
        try {
            shown.clear();
            shown.addAll(orderDAO.findBySession(sessionId));
        } catch (SQLException e) {
            AppLogger.error("Could not load the order", e);
            Theme.error(this, "Could not load the order:\n" + e.getMessage());
        }
        model.setRowCount(0);
        BigDecimal total = BigDecimal.ZERO;
        DateTimeFormatter t = DateTimeFormatter.ofPattern("HH:mm");
        for (OrderItem i : shown) {
            model.addRow(new Object[]{i.getName(), i.getQuantity(), Theme.money(i.getAmount()), i.getAddedAt().format(t)});
            total = total.add(i.getAmount());
        }
        totalLabel.setText("Food & drinks: " + Theme.money(total));
    }

    private void addItem(MenuItem item, int quantity) {
        try {
            orderDAO.add(sessionId, OrderItem.of(item, quantity));
        } catch (SQLException e) {
            AppLogger.error("Could not add the item", e);
            Theme.error(this, "Could not add the item:\n" + e.getMessage());
        }
        load();
    }

    private void removeItem() {
        int row = table.getSelectedRow();
        if (row < 0) { Theme.error(this, "Select the item to remove first."); return; }
        OrderItem i = shown.get(row);
        if (!Theme.confirm(this, "Remove " + i.getName() + " x" + i.getQuantity() + "?", "Remove Item")) return;
        try {
            orderDAO.delete(i.getId());
        } catch (SQLException e) {
            AppLogger.error("Could not remove the item", e);
            Theme.error(this, "Could not remove the item:\n" + e.getMessage());
        }
        load();
    }
}
