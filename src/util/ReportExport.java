package util;

import model.MonthlyReport;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import javax.swing.table.DefaultTableModel;
import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/** Saves a monthly report as CSV (for Excel) or PDF (to print or send). */
public class ReportExport {
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMMM yyyy");

    /** The main lines of the report: {item, this month, previous month}. Used by the screen, the CSV and the PDF. */
    public static Object[][] summaryRows(MonthlyReport m, MonthlyReport p) {
        return new Object[][]{
                {"Revenue (money received)", m.revenue, p.revenue},
                {"Expenses", m.expenses, p.expenses},
                {"Profit", m.getProfit(), p.getProfit()},
                {"Table time", m.tableTime, p.tableTime},
                {"Food & drinks", m.foodAndDrinks, p.foodAndDrinks},
                {"Discounts given", m.billDiscounts, p.billDiscounts},
                {"Membership sales", m.memberships, p.memberships},
                {"Cash", m.cash, p.cash},
                {"UPI", m.upi, p.upi},
                {"Paid bills (count)", BigDecimal.valueOf(m.paidBills), BigDecimal.valueOf(p.paidBills)},
                {"Memberships sold (count)", BigDecimal.valueOf(m.membershipsSold), BigDecimal.valueOf(p.membershipsSold)}};
    }

    /** One table with every number of the report, ready for CsvExporter. */
    public static DefaultTableModel csvModel(MonthlyReport m, MonthlyReport p) {
        DefaultTableModel t = new DefaultTableModel(new String[]{"Section", "Item", m.month.format(MONTH), p.month.format(MONTH), "Change"}, 0);
        for (Object[] r : summaryRows(m, p)) {
            t.addRow(new Object[]{"Summary", r[0], plain((BigDecimal) r[1]), plain((BigDecimal) r[2]),
                    MonthlyReport.change((BigDecimal) r[1], (BigDecimal) r[2])});
        }
        for (MonthlyReport.StaffTotal s : m.byStaff) {
            t.addRow(new Object[]{"Billed by", s.name + " (" + s.sales + " sales, discounts " + plain(s.discounts) + ")", plain(s.revenue), "", ""});
        }
        for (Map.Entry<String, BigDecimal> e : m.expensesByCategory.entrySet()) t.addRow(new Object[]{"Expenses", e.getKey(), plain(e.getValue()), "", ""});
        for (Map.Entry<String, BigDecimal> e : m.discountsByOccasion.entrySet()) t.addRow(new Object[]{"Discounts", e.getKey(), plain(e.getValue()), "", ""});
        for (Map.Entry<Integer, BigDecimal> e : m.revenueByDay.entrySet()) {
            t.addRow(new Object[]{"Revenue per day", m.month.atDay(e.getKey()).toString(), plain(e.getValue()), "", ""});
        }
        return t;
    }

    // ------------------------------------------------------------------ PDF

    /** Writes the PDF. sixMonths = {YearMonth, revenue, expenses}, oldest first. */
    public static void writePdf(File file, MonthlyReport m, MonthlyReport p, List<Object[]> sixMonths) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            Pdf pdf = new Pdf(doc);
            pdf.text(AppSettings.getClubName(), 18, true, 50);
            pdf.text("Monthly report - " + m.month.format(MONTH), 14, true, 50);
            pdf.text("Made on " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm"))
                    + ". Money counts on the day it was received.", 9, false, 50);
            pdf.gap(10);

            pdf.text("Summary", 12, true, 50);
            pdf.row(new String[]{"", m.month.format(MONTH), p.month.format(MONTH), "Change"}, new int[]{50, 260, 370, 480}, true);
            for (Object[] r : summaryRows(m, p)) {
                boolean count = ((String) r[0]).contains("(count)");
                pdf.row(new String[]{(String) r[0], count ? plain((BigDecimal) r[1]) : rs((BigDecimal) r[1]),
                        count ? plain((BigDecimal) r[2]) : rs((BigDecimal) r[2]), MonthlyReport.change((BigDecimal) r[1], (BigDecimal) r[2])},
                        new int[]{50, 260, 370, 480}, false);
            }
            pdf.gap(8);

            pdf.text("Billed by", 12, true, 50);
            pdf.row(new String[]{"Name", "Sales", "Revenue", "Discounts"}, new int[]{50, 260, 330, 450}, true);
            if (m.byStaff.isEmpty()) pdf.text("No sales this month.", 10, false, 50);
            for (MonthlyReport.StaffTotal s : m.byStaff) {
                pdf.row(new String[]{s.name, String.valueOf(s.sales), rs(s.revenue), rs(s.discounts)}, new int[]{50, 260, 330, 450}, false);
            }
            pdf.gap(8);

            pdf.text("Expenses by category", 12, true, 50);
            if (m.expensesByCategory.isEmpty()) pdf.text("No expenses this month.", 10, false, 50);
            for (Map.Entry<String, BigDecimal> e : m.expensesByCategory.entrySet()) pdf.row(new String[]{e.getKey(), rs(e.getValue())}, new int[]{50, 260}, false);
            pdf.gap(8);

            pdf.text("Discounts by occasion", 12, true, 50);
            if (m.discountsByOccasion.isEmpty()) pdf.text("No discounts this month.", 10, false, 50);
            for (Map.Entry<String, BigDecimal> e : m.discountsByOccasion.entrySet()) pdf.row(new String[]{e.getKey(), rs(e.getValue())}, new int[]{50, 260}, false);
            pdf.gap(12);

            pdf.chart("Last 6 months: revenue (green) and expenses (red)", sixMonths);
            pdf.close();
            doc.save(file);
        }
    }

    /** A tiny page writer: keeps track of the current height and starts a new page when needed. */
    private static class Pdf {
        private final PDDocument doc;
        private final PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        private final PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        private PDPageContentStream cs;
        private float y;

        Pdf(PDDocument doc) throws IOException {
            this.doc = doc;
            newPage();
        }

        private void newPage() throws IOException {
            if (cs != null) cs.close();
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            cs = new PDPageContentStream(doc, page);
            y = page.getMediaBox().getHeight() - 50;
        }

        private void need(float height) throws IOException {
            if (y - height < 50) newPage();
        }

        void text(String s, float size, boolean isBold, float x) throws IOException {
            need(size + 6);
            y -= size + 6;
            cs.beginText();
            cs.setFont(isBold ? bold : regular, size);
            cs.newLineAtOffset(x, y);
            cs.showText(safe(s));
            cs.endText();
        }

        void row(String[] cells, int[] xs, boolean header) throws IOException {
            need(16);
            y -= 15;
            for (int i = 0; i < cells.length; i++) {
                cs.beginText();
                cs.setFont(header ? bold : regular, 10);
                cs.newLineAtOffset(xs[i], y);
                cs.showText(safe(cells[i]));
                cs.endText();
            }
        }

        void gap(float h) { y -= h; }

        void chart(String title, List<Object[]> months) throws IOException {
            need(190);
            text(title, 12, true, 50);
            float bottom = y - 140, left = 60, width = 470, height = 120;
            double max = 1;
            for (Object[] r : months) max = Math.max(max, Math.max(((BigDecimal) r[1]).doubleValue(), ((BigDecimal) r[2]).doubleValue()));
            float groupW = width / months.size();
            for (int i = 0; i < months.size(); i++) {
                Object[] r = months.get(i);
                float x = left + i * groupW + groupW * 0.15f;
                float barW = groupW * 0.32f;
                bar(x, bottom, barW, (float) (((BigDecimal) r[1]).doubleValue() / max * height), new Color(0x1FA866));
                bar(x + barW + 2, bottom, barW, (float) (((BigDecimal) r[2]).doubleValue() / max * height), new Color(0xD65A5A));
                cs.beginText();
                cs.setFont(regular, 9);
                cs.newLineAtOffset(left + i * groupW + groupW * 0.15f, bottom - 12);
                cs.showText(safe(((YearMonth) r[0]).format(DateTimeFormatter.ofPattern("MMM yyyy"))));
                cs.endText();
            }
            cs.setNonStrokingColor(Color.BLACK);
            cs.beginText();
            cs.setFont(regular, 8);
            cs.newLineAtOffset(left, bottom + height + 6);
            cs.showText(safe("highest: " + rs(BigDecimal.valueOf(max))));
            cs.endText();
            y = bottom - 20;
        }

        private void bar(float x, float bottom, float w, float h, Color c) throws IOException {
            cs.setNonStrokingColor(c);
            cs.addRect(x, bottom, w, Math.max(0.5f, h));
            cs.fill();
            cs.setNonStrokingColor(Color.BLACK);
        }

        void close() throws IOException { cs.close(); }
    }

    /** The standard PDF fonts only know Western characters: Rs. instead of the rupee sign, ? for anything else. */
    static String safe(String s) {
        StringBuilder sb = new StringBuilder();
        for (char ch : s.replace("₹", "Rs. ").toCharArray()) sb.append((ch >= 32 && ch <= 126) || (ch >= 160 && ch <= 255) ? ch : '?');
        return sb.toString();
    }

    static String rs(BigDecimal v) { return String.format("Rs. %,.2f", v); }

    static String plain(BigDecimal v) { return v.stripTrailingZeros().scale() <= 0 ? v.setScale(0).toPlainString() : v.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString(); }
}
