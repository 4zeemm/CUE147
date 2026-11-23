package ui;

import javax.swing.*;
import java.awt.*;

/** The green "felt" branding panel on the login screen. Painted with Graphics2D. */
public class BilliardsArtPanel extends JPanel {
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int w = getWidth(), h = getHeight();

        // felt background
        g2.setPaint(new GradientPaint(0, 0, new Color(0x0F5A3A), w, h, new Color(0x06271A)));
        g2.fillRect(0, 0, w, h);

        // wooden rail on the right edge
        g2.setColor(new Color(0x3B2314));
        g2.fillRect(w - 14, 0, 14, h);
        g2.setColor(Theme.GOLD);
        g2.fillRect(w - 14, 0, 2, h);

        // snooker balls
        Color[] colours = {new Color(0xC0392B), new Color(0xF1C40F), new Color(0x1E8449), new Color(0x7B4A1E),
                new Color(0x2471A3), new Color(0xE75480), new Color(0x111111)};
        int r = 18, startX = 60, y = h - 110;
        for (int i = 0; i < colours.length; i++) drawBall(g2, startX + i * 46, y, r, colours[i]);
        drawBall(g2, w - 90, y - 60, r, Color.WHITE);

        // cue stick
        g2.setStroke(new BasicStroke(6, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(new Color(0xC9A26B));
        g2.drawLine(w - 120, y - 90, w - 40, y - 150);
        g2.setColor(Theme.GOLD);
        g2.drawLine(w - 124, y - 87, w - 118, y - 91);

        // title text
        g2.setColor(Theme.GOLD);
        g2.setFont(new Font("Serif", Font.BOLD, 60));
        center(g2, "CUE 147", w, 170);
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("SansSerif", Font.PLAIN, 20));
        center(g2, "Snooker & Pool Club", w, 210);
        g2.setColor(new Color(0xA8D8BD));
        g2.setFont(new Font("SansSerif", Font.ITALIC, 16));
        center(g2, "Break. Pot. Repeat.", w, 245);
        g2.dispose();
    }

    private void center(Graphics2D g2, String s, int w, int y) {
        int x = (w - 14 - g2.getFontMetrics().stringWidth(s)) / 2;
        g2.drawString(s, x, y);
    }

    private void drawBall(Graphics2D g2, int x, int y, int r, Color c) {
        g2.setColor(new Color(0, 0, 0, 70));
        g2.fillOval(x - r + 4, y - r + 5, r * 2, r * 2);
        g2.setPaint(new RadialGradientPaint(x - r / 3f, y - r / 3f, r * 1.4f, new float[]{0f, 1f},
                new Color[]{c.brighter().brighter(), c.darker()}));
        g2.fillOval(x - r, y - r, r * 2, r * 2);
        g2.setColor(new Color(255, 255, 255, 140));
        g2.fillOval(x - r / 2, y - r / 2 - 2, r / 3, r / 4);
    }
}
