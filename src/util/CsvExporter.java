package util;

import javax.swing.table.TableModel;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Saves the rows of a JTable (its TableModel) into a CSV file that Excel can open.
 * Uses java.io / java.nio.file. try-with-resources closes the file even if an error happens.
 */
public class CsvExporter {

    /** Writes the header line and all rows. Throws IOException if the file cannot be written. */
    public static void write(File file, TableModel model) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
            writer.write('﻿');   // lets Excel read the rupee sign correctly
            for (int c = 0; c < model.getColumnCount(); c++) {
                if (c > 0) writer.write(',');
                writer.write(escape(model.getColumnName(c)));
            }
            writer.newLine();
            for (int r = 0; r < model.getRowCount(); r++) {
                for (int c = 0; c < model.getColumnCount(); c++) {
                    if (c > 0) writer.write(',');
                    writer.write(escape(model.getValueAt(r, c)));
                }
                writer.newLine();
            }
        }
    }

    /** Puts quotes around text that contains a comma or a quote. */
    private static String escape(Object value) {
        String text = value == null ? "" : value.toString();
        if (text.contains(",") || text.contains("\"") || text.contains("\n")) {
            text = "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }
}
