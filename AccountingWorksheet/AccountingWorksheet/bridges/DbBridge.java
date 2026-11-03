import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;

/**
 * BRIDGE 1 - the database bridge.
 * The only file that talks to MySQL (or to the built-in demo data when started
 * with --demo). Main.java never sees a SQL statement.
 */
public final class DbBridge {

    /** One account of the (fixed) chart of accounts. */
    public static final class Acct {
        public final String code, title, type, side;   // type: ASSET..EXPENSE, side: DEBIT/CREDIT

        public Acct(String code, String title, String type, String side) {
            this.code = code; this.title = title; this.type = type; this.side = side;
        }

        /** Revenue and expense accounts go to the Income Statement, the rest to the Balance Sheet. */
        public boolean income() { return type.equals("REVENUE") || type.equals("EXPENSE"); }
    }

    /** One journal line together with its entry header. */
    public static final class Line {
        public int entryId;
        public LocalDate date;
        public String desc;
        public boolean adjusting;
        public String code, title;
        public BigDecimal dr, cr;

        public Line(int entryId, LocalDate date, String desc, boolean adjusting,
                    String code, String title, BigDecimal dr, BigDecimal cr) {
            this.entryId = entryId; this.date = date; this.desc = desc; this.adjusting = adjusting;
            this.code = code; this.title = title; this.dr = dr; this.cr = cr;
        }
    }

    public static final class DataException extends RuntimeException {
        public DataException(String msg, Throwable cause) { super(msg, cause); }
    }

    private static boolean demo;
    private static Properties cfg;
    private static final List<Line> demoLines = new ArrayList<>();
    private static int demoNext = 1;

    private DbBridge() { }

    // ------------------------------------------------------------------ setup
    public static synchronized void init(boolean demoMode) {
        demo = demoMode;
        if (demo) {
            if (demoLines.isEmpty()) seedDemo();
            return;
        }
        List<Path> where = new ArrayList<>();
        String custom = System.getProperty("accounting.config");
        if (custom != null && !custom.trim().isEmpty()) where.add(Paths.get(custom));
        where.add(Paths.get("config", "db.properties"));
        try {
            Path loc = Paths.get(DbBridge.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            Path dir = Files.isDirectory(loc) ? loc : loc.getParent();
            if (dir != null) {
                where.add(dir.resolve("config").resolve("db.properties"));
                if (dir.getParent() != null) where.add(dir.getParent().resolve("config").resolve("db.properties"));
            }
        } catch (Exception ignored) { /* use the remaining candidates */ }

        for (Path p : where) {
            if (Files.isRegularFile(p)) {
                Properties props = new Properties();
                try (InputStream in = Files.newInputStream(p)) {
                    props.load(in);
                } catch (IOException ex) {
                    throw new DataException("Cannot read " + p.toAbsolutePath(), ex);
                }
                cfg = props;
                return;
            }
        }
        throw new DataException("config/db.properties was not found. Looked in: " + where, null);
    }

    public static boolean isDemo() { return demo; }

    public static String describe() {
        return demo ? "Demo mode (nothing is saved)" : (cfg == null ? "" : cfg.getProperty("db.url", ""));
    }

    private static Connection open() throws SQLException {
        return DriverManager.getConnection(cfg.getProperty("db.url"),
                cfg.getProperty("db.user", "root"), cfg.getProperty("db.password", ""));
    }

    /** Throws a DataException with a readable message when MySQL cannot be reached. */
    public static void ping() {
        if (demo) return;
        try (Connection ignored = open()) {
            // connected
        } catch (SQLException ex) {
            throw new DataException("Cannot connect to MySQL: " + ex.getMessage()
                    + "\n\nCheck config/db.properties, that MySQL is running, and that the SQL scripts were run.", ex);
        }
    }

    // ------------------------------------------------------------------- read
    public static List<Acct> accounts() {
        if (demo) return demoAccounts();
        List<Acct> list = new ArrayList<>();
        try (Connection c = open();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT account_code, account_title, account_type, normal_side FROM accounts ORDER BY account_code");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new Acct(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)));
            }
            return list;
        } catch (SQLException ex) {
            throw new DataException("Could not load the chart of accounts: " + ex.getMessage(), ex);
        }
    }

    public static List<Line> lines(LocalDate from, LocalDate to) {
        List<Line> list = new ArrayList<>();
        if (demo) {
            for (Line l : demoLines) {
                if (!l.date.isBefore(from) && !l.date.isAfter(to)) list.add(l);
            }
            list.sort(Comparator.comparing((Line l) -> l.date).thenComparingInt(l -> l.entryId));
            return list;
        }
        String sql = "SELECT e.entry_id, e.entry_date, e.description, e.entry_type, "
                + "l.account_code, a.account_title, l.debit, l.credit "
                + "FROM journal_entries e JOIN journal_lines l ON l.entry_id = e.entry_id "
                + "JOIN accounts a ON a.account_code = l.account_code "
                + "WHERE e.entry_date BETWEEN ? AND ? ORDER BY e.entry_date, e.entry_id, l.line_id";
        try (Connection c = open(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(from));
            ps.setDate(2, Date.valueOf(to));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new Line(rs.getInt(1), rs.getDate(2).toLocalDate(), rs.getString(3),
                            "ADJUSTING".equals(rs.getString(4)), rs.getString(5), rs.getString(6),
                            rs.getBigDecimal(7), rs.getBigDecimal(8)));
                }
            }
            return list;
        } catch (SQLException ex) {
            throw new DataException("Could not load the journal: " + ex.getMessage(), ex);
        }
    }

    // ------------------------------------------------------------------ write
    /** Saves header + lines in ONE transaction. Only code/dr/cr of each line are used. Returns the entry id. */
    public static int saveEntry(LocalDate date, String desc, boolean adjusting, List<Line> ls) {
        if (demo) {
            int id = demoNext++;
            for (Line l : ls) {
                demoLines.add(new Line(id, date, desc, adjusting, l.code, titleOf(l.code), l.dr, l.cr));
            }
            return id;
        }
        try (Connection c = open()) {
            c.setAutoCommit(false);
            try {
                int id;
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO journal_entries (entry_date, description, entry_type) VALUES (?,?,?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    ps.setDate(1, Date.valueOf(date));
                    ps.setString(2, desc);
                    ps.setString(3, adjusting ? "ADJUSTING" : "REGULAR");
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (!keys.next()) throw new SQLException("No entry id was generated.");
                        id = keys.getInt(1);
                    }
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO journal_lines (entry_id, account_code, debit, credit) VALUES (?,?,?,?)")) {
                    for (Line l : ls) {
                        ps.setInt(1, id);
                        ps.setString(2, l.code);
                        ps.setBigDecimal(3, l.dr);
                        ps.setBigDecimal(4, l.cr);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                c.commit();
                return id;
            } catch (SQLException ex) {
                c.rollback();
                throw ex;
            }
        } catch (SQLException ex) {
            throw new DataException("Could not save the entry: " + ex.getMessage(), ex);
        }
    }

    public static void deleteEntry(int id) {
        if (demo) {
            demoLines.removeIf(l -> l.entryId == id);
            return;
        }
        try (Connection c = open();
             PreparedStatement ps = c.prepareStatement("DELETE FROM journal_entries WHERE entry_id = ?")) {
            ps.setInt(1, id);
            ps.executeUpdate();            // lines go away through ON DELETE CASCADE
        } catch (SQLException ex) {
            throw new DataException("Could not delete the entry: " + ex.getMessage(), ex);
        }
    }

    // ------------------------------------------------------------- demo data
    private static List<Acct> demoAccounts() {
        String[][] a = {
            {"1010", "Cash", "ASSET", "DEBIT"}, {"1020", "Accounts Receivable", "ASSET", "DEBIT"},
            {"1030", "Supplies", "ASSET", "DEBIT"}, {"1040", "Prepaid Insurance", "ASSET", "DEBIT"},
            {"1110", "Office Equipment", "ASSET", "DEBIT"},
            {"1120", "Accumulated Depreciation - Office Equipment", "ASSET", "CREDIT"},
            {"2010", "Accounts Payable", "LIABILITY", "CREDIT"}, {"2020", "Salaries Payable", "LIABILITY", "CREDIT"},
            {"3010", "Owner's Capital", "EQUITY", "CREDIT"}, {"3020", "Owner's Drawings", "EQUITY", "DEBIT"},
            {"4010", "Service Revenue", "REVENUE", "CREDIT"},
            {"5010", "Salaries Expense", "EXPENSE", "DEBIT"}, {"5020", "Utilities Expense", "EXPENSE", "DEBIT"},
            {"5030", "Supplies Expense", "EXPENSE", "DEBIT"}, {"5040", "Insurance Expense", "EXPENSE", "DEBIT"},
            {"5050", "Depreciation Expense", "EXPENSE", "DEBIT"}};
        List<Acct> list = new ArrayList<>();
        for (String[] r : a) list.add(new Acct(r[0], r[1], r[2], r[3]));
        return list;
    }

    private static String titleOf(String code) {
        for (Acct a : demoAccounts()) if (a.code.equals(code)) return a.title;
        return code;
    }

    private static void seedDemo() {
        Object[][] e = {
            {"2026-10-01", "Owner invested cash in the business", false, "1010", 100000, "3010"},
            {"2026-10-02", "Purchased supplies on account", false, "1030", 6000, "2010"},
            {"2026-10-03", "Paid one-year insurance premium", false, "1040", 12000, "1010"},
            {"2026-10-05", "Purchased office equipment for cash", false, "1110", 40000, "1010"},
            {"2026-10-10", "Received cash for services rendered", false, "1010", 30000, "4010"},
            {"2026-10-15", "Services rendered on account", false, "1020", 20000, "4010"},
            {"2026-10-20", "Paid salaries", false, "5010", 15000, "1010"},
            {"2026-10-22", "Paid utilities", false, "5020", 3000, "1010"},
            {"2026-10-25", "Paid part of accounts payable", false, "2010", 2000, "1010"},
            {"2026-10-28", "Owner withdrew cash for personal use", false, "3020", 5000, "1010"},
            {"2026-10-31", "Adjusting: supplies used during the month", true, "5030", 4000, "1030"},
            {"2026-10-31", "Adjusting: insurance expired", true, "5040", 1000, "1040"},
            {"2026-10-31", "Adjusting: depreciation of office equipment", true, "5050", 1000, "1120"},
            {"2026-10-31", "Adjusting: accrued salaries", true, "5010", 2000, "2020"}};
        for (Object[] r : e) {
            BigDecimal amt = new BigDecimal(((Integer) r[4]).intValue()).setScale(2);
            BigDecimal zero = BigDecimal.ZERO.setScale(2);
            int id = demoNext++;
            LocalDate d = LocalDate.parse((String) r[0]);
            demoLines.add(new Line(id, d, (String) r[1], (Boolean) r[2], (String) r[3], titleOf((String) r[3]), amt, zero));
            demoLines.add(new Line(id, d, (String) r[1], (Boolean) r[2], (String) r[5], titleOf((String) r[5]), zero, amt));
        }
    }
}
