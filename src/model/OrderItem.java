package model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Food or drink ordered by a table, for example "Cold Coffee x 2".
 * Name, category and price are COPIED from the menu, so changing the menu later does not change old bills.
 */
public class OrderItem {
    private final int id;              // order_item_id in MySQL (0 = not saved yet)
    private final int menuItemId;
    private final String name;
    private final String category;
    private final BigDecimal unitPrice;
    private final int quantity;
    private final LocalDateTime addedAt;

    public OrderItem(int menuItemId, String name, String category, BigDecimal unitPrice, int quantity, LocalDateTime addedAt) {
        this(0, menuItemId, name, category, unitPrice, quantity, addedAt);
    }

    public OrderItem(int id, int menuItemId, String name, String category, BigDecimal unitPrice, int quantity, LocalDateTime addedAt) {
        this.id = id;
        this.menuItemId = menuItemId;
        this.name = name;
        this.category = category;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
        this.addedAt = addedAt;
    }

    /** A new order of a menu item, ordered now. */
    public static OrderItem of(MenuItem item, int quantity) {
        return new OrderItem(item.getId(), item.getName(), item.getCategory(), item.getPrice(), quantity, LocalDateTime.now().withNano(0));
    }

    public OrderItem withQuantity(int newQuantity) {
        return new OrderItem(id, menuItemId, name, category, unitPrice, newQuantity, addedAt);
    }

    public int getId() { return id; }
    public int getMenuItemId() { return menuItemId; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public int getQuantity() { return quantity; }
    public LocalDateTime getAddedAt() { return addedAt; }
    public BigDecimal getAmount() { return unitPrice.multiply(BigDecimal.valueOf(quantity)); }
}
