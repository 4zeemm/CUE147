package ui;

import dao.MenuItemDAO;
import model.MenuItem;
import util.AppLogger;

import javax.swing.*;
import java.awt.*;
import java.sql.SQLException;

/** A small row to add food or drinks: [menu item v] [quantity] [Add Item]. Used at the table and at the checkout. */
public class ItemAdder extends JPanel {
    /** What happens when "Add Item" is pressed. */
    public interface Listener { void add(MenuItem item, int quantity); }

    private final JComboBox<MenuItem> menu = new JComboBox<>();
    private final JSpinner quantity = Theme.styleSpinner(new JSpinner(new SpinnerNumberModel(1, 1, 99, 1)));

    public ItemAdder(Listener listener) {
        super(new BorderLayout(6, 0));
        setOpaque(false);
        menu.setFont(Theme.BODY);
        quantity.setName("itemQty");
        quantity.setPreferredSize(new Dimension(64, 34));
        try {
            for (MenuItem m : new MenuItemDAO().findAvailable()) menu.addItem(m);
        } catch (SQLException e) {
            AppLogger.error("Could not load the menu", e);
        }
        if (menu.getItemCount() == 0) {
            menu.setEnabled(false);
            menu.setToolTipText("The owner adds the menu in Settings > Food & Drinks Menu");
        }
        JPanel right = Theme.panel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        right.add(quantity);
        right.add(Theme.button("Add Item", Theme.BLUE, e -> {
            MenuItem item = (MenuItem) menu.getSelectedItem();
            if (item == null) { Theme.error(this, "The menu is empty. The owner adds items in Settings > Food & Drinks Menu."); return; }
            listener.add(item, ((Number) quantity.getValue()).intValue());
            quantity.setValue(1);
        }));
        add(menu, BorderLayout.CENTER);
        add(right, BorderLayout.EAST);
    }
}
