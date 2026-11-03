import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JTable;
import java.awt.Component;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * BRIDGE 2 - the export bridge.
 * Saves whatever table is on screen (journal, ledger, 12-column worksheet) as a CSV that opens in Excel.
 */
public final class ExportBridge {

    private ExportBridge() { }

    /** Asks where to save, then writes the table. Returns the file or null when cancelled. */
    public static Path exportTable(Component parent, JTable table, String suggestedName) {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Export to CSV (opens in Excel)");
        fc.setSelectedFile(new File(suggestedName + ".csv"));
        if (fc.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) return null;
        Path file = fc.getSelectedFile().toPath();
        if (!file.getFileName().toString().toLowerCase().endsWith(".csv")) {
            file = file.resolveSibling(file.getFileName() + ".csv");
        }
        try {
            String[] header = new String[table.getColumnCount()];
            for (int c = 0; c < header.length; c++) header[c] = table.getColumnName(c).replace('|', ' ');
            List<String[]> rows = new ArrayList<>();
            for (int r = 0; r < table.getRowCount(); r++) {
                String[] row = new String[header.length];
                for (int c = 0; c < header.length; c++) {
                    Object v = table.getValueAt(r, c);
                    String s = v == null ? "" : v.toString().trim();
                    if (s.matches("-?[\\d,]+\\.\\d{2}")) s = s.replace(",", "");   // real numbers for Excel
                    row[c] = s;
                }
                rows.add(row);
            }
            writeCsv(file, header, rows);
            return file;
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(parent, "Could not write the file:\n" + ex.getMessage(),
                    "Export failed", JOptionPane.ERROR_MESSAGE);
            return null;
        }
    }

    public static void writeCsv(Path file, String[] header, List<String[]> rows) throws IOException {
        try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            w.write('\uFEFF');                       // BOM so Excel reads UTF-8
            w.write(join(header));
            w.newLine();
            for (String[] r : rows) {
                w.write(join(r));
                w.newLine();
            }
        }
    }

    private static String join(String[] cells) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) sb.append(',');
            String s = cells[i] == null ? "" : cells[i];
            if (s.contains(",") || s.contains("\"") || s.contains("\n")) s = "\"" + s.replace("\"", "\"\"") + "\"";
            sb.append(s);
        }
        return sb.toString();
    }
}
