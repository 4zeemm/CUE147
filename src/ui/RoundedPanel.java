package ui;

import javax.swing.*;
import java.awt.*;

/** A panel with rounded corners and an optional coloured outline - used for all "cards". */
public class RoundedPanel extends JPanel {
    private Color fill;
    private Color outline = Theme.BORDER;

    public RoundedPanel(LayoutManager layout, Color fill) {
        super(layout);
        this.fill = fill;
        setOpaque(false);
    }

    public RoundedPanel(LayoutManager layout) { this(layout, Theme.CARD); }

    public void setOutline(Color c) { outline = c; repaint(); }
    public void setFill(Color c) { fill = c; repaint(); }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(fill);
        g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 18, 18);
        g2.setColor(outline);
        g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 18, 18);
        g2.dispose();
        super.paintComponent(g);
    }
}
