package ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDate;
import javax.swing.table.TableModel;
import util.AppLogger;
import util.CsvExporter;

/**
 * Base class for every screen. Each screen overrides refresh() so
 * the MainFrame can simply call refresh() on any of them (polymorphism).
 */
public abstract class ScreenPanel extends JPanel implements Refreshable {
    protected final MainFrame frame;

    public ScreenPanel(MainFrame frame) {
        super(new BorderLayout(0, 16));
        this.frame = frame;
        setBackground(Theme.BG);
        setBorder(new EmptyBorder(20, 24, 20, 24));
    }

    /** Re-reads the data from the database (the Refreshable contract; every screen must write its own). */
    @Override
    public abstract void refresh();

    /** Called every second by the Swing Timer. Screens with live timers override it. */
    @Override
    public void tick() { }

    /** Called every 15 seconds. Screens that show live data (Tables, Dashboard) override it. */
    public void autoRefresh() { }

    /** Shows a friendly message when a database operation fails. */
    protected void showDbError(SQLException e) {
        AppLogger.error("Database operation failed", e);   // saved in cue147.log
        Theme.error(this, "Database problem:\n" + e.getMessage()
                + "\n\nIs MySQL running? (./start-mysql.sh)");
    }

    /** Saves the rows of a table into a CSV file chosen by the user (Excel can open it). */
    protected void exportTable(String baseName, TableModel model) {
        if (model.getRowCount() == 0) {
            Theme.info(this, "There is nothing to export yet.", "Export");
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File(System.getProperty("user.home"), baseName + "_" + LocalDate.now() + ".csv"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File file = chooser.getSelectedFile();
        if (file.exists() && !Theme.confirm(this, file.getName() + " already exists. Replace it?", "Export")) return;
        try {
            CsvExporter.write(file, model);
            frame.setStatus("Exported " + model.getRowCount() + " rows to " + file.getName());
            Theme.info(this, "Saved " + model.getRowCount() + " rows to:\n" + file.getAbsolutePath(), "Export Done");
        } catch (IOException e) {
            AppLogger.error("CSV export failed", e);
            Theme.error(this, "The file could not be saved:\n" + e.getMessage());
        }
    }

    /** Creates the "title + buttons" bar used at the top of every screen. */
    protected JPanel header(String title, String subtitle, Component... rightSide) {
        JPanel bar = Theme.panel(new BorderLayout());
        JPanel texts = Theme.panel(new GridLayout(2, 1));
        texts.add(Theme.label(title, Theme.TITLE, Theme.TEXT));
        texts.add(Theme.label(subtitle, Theme.BODY, Theme.MUTED));
        bar.add(texts, BorderLayout.WEST);
        JPanel right = Theme.panel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        for (Component c : rightSide) right.add(c);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }
}
