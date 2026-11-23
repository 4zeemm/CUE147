package model;

import java.math.BigDecimal;

/** One item of the food & drinks menu (the owner manages the menu in Settings). */
public class MenuItem {
    private final int id;
    private final String name;
    private final String category;   // Coffee, Beverages, Snacks ...
    private final BigDecimal price;
    private final boolean available;

    public MenuItem(int id, String name, String category, BigDecimal price, boolean available) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.price = price;
        this.available = available;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public BigDecimal getPrice() { return price; }
    public boolean isAvailable() { return available; }

    @Override
    public String toString() { return name + "  -  " + String.format("₹%,.2f", price); }   // shown in drop-downs
}
