package ui;

import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * A simple bar chart drawn with Java 2D (no chart library needed).
 * Each label (a month, a day) has one bar per series, e.g. Revenue and Expenses side by side.
 */
public class BarChart extends JComponent {
    private String title = "";
    private String[] seriesNames = {};
    private Color[] colors = {};
    private final List<String> labels = new ArrayList<>();
    private final List<BigDecimal[]> values = new ArrayList<>();

    public BarChart() {
        setPreferredSize(new Dimension(400, 240));
        setOpaque(false);
    }

    public void setData(String title, String[] seriesNames, Color[] colors, List<String> labels, List<BigDecimal[]> values) {
        this.title = title;
        this.seriesNames = seriesNames;
        this.colors = colors;
        this.labels.clear();
        this.labels.addAll(labels);
        this.values.clear();
        this.values.addAll(values);
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int w = getWidth(), h = getHeight();
        g.setColor(Theme.CARD);
        g.fillRoundRect(0, 0, w, h, 16, 16);

        // title and legend
        g.setFont(Theme.BOLD);
        g.setColor(Theme.TEXT);
        g.drawString(title, 14, 22);
        int lx = w - 14;
        for (int s = seriesNames.length - 1; s >= 0; s--) {
            int tw = g.getFontMetrics().stringWidth(seriesNames[s]);
            lx -= tw;
            g.setColor(Theme.MUTED);
            g.drawString(seriesNames[s], lx, 22);
            lx -= 16;
            g.setColor(colors[s]);
            g.fillRect(lx, 12, 11, 11);
            lx -= 14;
        }

        if (labels.isEmpty()) { g.dispose(); return; }
        double max = 1;
        for (BigDecimal[] v : values) for (BigDecimal x : v) max = Math.max(max, x.doubleValue());

        int left = 14, right = w - 14, top = 40, bottom = h - 26;
        int plotH = bottom - top;
        g.setColor(Theme.BORDER);
        g.drawLine(left, bottom, right, bottom);
        g.setFont(Theme.SMALL);
        g.setColor(Theme.MUTED);
        g.drawString("max " + String.format("₹%,.0f", max), left, top - 4);

        int groups = labels.size();
        double groupW = (right - left) / (double) groups;
        int series = seriesNames.length;
        double barW = Math.max(2, (groupW * 0.75) / series);
        int every = Math.max(1, (int) Math.ceil(groups / ((right - left) / 38.0)));   // skip labels that would overlap
        FontMetrics fm = g.getFontMetrics();
        for (int i = 0; i < groups; i++) {
            double gx = left + i * groupW + groupW * 0.125;
            for (int s = 0; s < series; s++) {
                double v = values.get(i)[s].doubleValue();
                int bh = (int) Math.round(Math.max(0, v) / max * plotH);
                g.setColor(colors[s]);
                g.fillRect((int) (gx + s * barW), bottom - bh, (int) Math.ceil(barW) - 1, bh);
            }
            if (i % every == 0) {
                g.setColor(Theme.MUTED);
                String text = labels.get(i);
                int tx = (int) (left + i * groupW + groupW / 2 - fm.stringWidth(text) / 2.0);
                g.drawString(text, tx, h - 9);
            }
        }
        g.dispose();
    }
}
