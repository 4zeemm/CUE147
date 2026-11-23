package ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/** A small dashboard card: a coloured value on top of a grey title. */
public class StatCard extends RoundedPanel {
    private final JLabel valueLabel;
    private final JLabel noteLabel = Theme.label(" ", Theme.SMALL, Theme.MUTED);

    public StatCard(String title, String value, Color accent) {
        super(new BorderLayout(0, 6));
        setBorder(new EmptyBorder(16, 18, 16, 18));
        JLabel titleLabel = Theme.label(title, Theme.SMALL, Theme.MUTED);
        valueLabel = Theme.label(value, new Font("SansSerif", Font.BOLD, 26), accent);
        JPanel bar = new JPanel();
        bar.setBackground(accent);
        bar.setPreferredSize(new Dimension(36, 3));
        JPanel top = Theme.panel(new BorderLayout(0, 8));
        top.add(bar, BorderLayout.NORTH);
        top.add(titleLabel, BorderLayout.CENTER);
        add(top, BorderLayout.NORTH);
        add(valueLabel, BorderLayout.CENTER);
        add(noteLabel, BorderLayout.SOUTH);
    }

    public void setValue(String value) { valueLabel.setText(value); }

    /** Small grey line under the value, e.g. "vs Oct 2026: +12.5%". */
    public void setNote(String note) { noteLabel.setText(note); }
}
