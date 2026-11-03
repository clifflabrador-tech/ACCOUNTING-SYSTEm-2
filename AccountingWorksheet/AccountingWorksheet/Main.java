import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumn;
import javax.swing.table.TableRowSorter;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.IntUnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LedgerBuddy - Accounting Worksheet System (the ONE main file).
 *
 *   Journal Entry  ->  Chart of Accounts  ->  Ledger  ->  12-Column Worksheet
 *
 * Data access lives in bridges/DbBridge.java, CSV export in bridges/ExportBridge.java.
 * Everything else (design, quick prompt, ledger, worksheet maths) is in this file.
 */
public class Main extends JFrame {

    // ============================================================ THEME (yellow + blue)
    static final Color NAVY   = new Color(0x0B2A6F);
    static final Color BLUE   = new Color(0x1F57C3);
    static final Color BLUE2  = new Color(0x2F6FE0);
    static final Color SKY    = new Color(0xE3EDFF);
    static final Color YELLOW = new Color(0xFFC928);
    static final Color SUN    = new Color(0xFFE585);
    static final Color BUTTER = new Color(0xFFF3BF);
    static final Color CREAM  = new Color(0xFFFBEA);
    static final Color INK    = new Color(0x14213D);
    static final Color LINE   = new Color(0xD9E2F5);
    static final Color STRIPE = new Color(0xF5F8FF);
    static final Color RED    = new Color(0xC62828);
    static final Color OK     = new Color(0x167A3A);

    static final Font F      = new Font("Segoe UI", Font.PLAIN, 13);
    static final Font FB     = new Font("Segoe UI", Font.BOLD, 13);
    static final Font FSMALL = new Font("Segoe UI", Font.PLAIN, 12);
    static final Font FSB    = new Font("Segoe UI", Font.BOLD, 12);
    static final Font FTITLE = new Font("Segoe UI", Font.BOLD, 20);
    static final Font FBIG   = new Font("Segoe UI", Font.BOLD, 16);

    static final DecimalFormat MONEY = new DecimalFormat("#,##0.00");
    static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    static String fmt(BigDecimal v) { return MONEY.format(v); }

    /** Parses "5,000", "₱5000.50", "$12" ... Empty text gives null. */
    static BigDecimal parseMoney(String s) {
        String t = s == null ? "" : s.replaceAll("[₱$,\\s]", "");
        return t.isEmpty() ? null : new BigDecimal(t);
    }

    // ================================================================ WORKSHEET MATHS
    static final String[] GROUPS = {"Unadjusted TB", "Adjustments", "Adjusted TB", "Income Statement", "Balance Sheet"};
    static final String[] SHORT  = {"Unadj. TB", "Adjustments", "Adjusted TB", "Income Stmt", "Balance Sheet"};

    /** One line of the worksheet. v = 10 numbers: (Dr,Cr) x [Unadj, Adjustments, Adjusted, IS, BS]. */
    static final class WRow {
        String code, title;
        int kind;                 // 0 account, 1 total, 6 net income, 3 grand total, 4 difference
        boolean diff;
        BigDecimal[] v = new BigDecimal[10];

        WRow(String code, String title, int kind) {
            this.code = code; this.title = title; this.kind = kind;
            Arrays.fill(v, ZERO);
        }
    }

    static final class Sheet {
        final List<WRow> rows = new ArrayList<>();
        BigDecimal netIncome = ZERO;
        final boolean[] groupOk = new boolean[5];
        BigDecimal balanceDifference = ZERO;      // balance sheet Dr - Cr after net income, must be 0.00
        boolean balanced;
        boolean empty;

        boolean isLoss() { return netIncome.signum() < 0; }
    }

    static BigDecimal[] pair(BigDecimal net) {
        return net.signum() >= 0 ? new BigDecimal[]{net, ZERO} : new BigDecimal[]{ZERO, net.negate()};
    }

    static Sheet buildSheet(List<DbBridge.Acct> accts, List<DbBridge.Line> lines) {
        java.util.Map<String, BigDecimal[]> m = new java.util.HashMap<>();   // regDr, regCr, adjDr, adjCr
        for (DbBridge.Line l : lines) {
            BigDecimal[] a = m.computeIfAbsent(l.code, k -> new BigDecimal[]{ZERO, ZERO, ZERO, ZERO});
            int o = l.adjusting ? 2 : 0;
            a[o] = a[o].add(l.dr);
            a[o + 1] = a[o + 1].add(l.cr);
        }
        Sheet s = new Sheet();
        BigDecimal[] tot = new BigDecimal[10];
        Arrays.fill(tot, ZERO);

        for (DbBridge.Acct ac : accts) {
            BigDecimal[] a = m.get(ac.code);
            if (a == null) continue;
            WRow r = new WRow(ac.code, ac.title, 0);
            BigDecimal unadj = a[0].subtract(a[1]);
            BigDecimal adjusted = unadj.add(a[2]).subtract(a[3]);
            BigDecimal[] p;
            p = pair(unadj);      r.v[0] = p[0]; r.v[1] = p[1];
            r.v[2] = a[2];        r.v[3] = a[3];
            p = pair(adjusted);   r.v[4] = p[0]; r.v[5] = p[1];
            int o = ac.income() ? 6 : 8;
            r.v[o] = p[0]; r.v[o + 1] = p[1];
            for (int i = 0; i < 10; i++) tot[i] = tot[i].add(r.v[i]);
            s.rows.add(r);
        }
        s.empty = s.rows.isEmpty();

        WRow total = new WRow("", "TOTAL", 1);
        total.v = tot.clone();
        s.netIncome = tot[7].subtract(tot[6]);
        boolean loss = s.netIncome.signum() < 0;
        WRow ni = new WRow("", loss ? "NET LOSS" : "NET INCOME", 6);
        if (!loss) { ni.v[6] = s.netIncome; ni.v[9] = s.netIncome; }
        else       { ni.v[7] = s.netIncome.negate(); ni.v[8] = s.netIncome.negate(); }
        WRow grand = new WRow("", "GRAND TOTAL", 3);
        for (int i = 6; i < 10; i++) grand.v[i] = tot[i].add(ni.v[i]);

        WRow diff = new WRow("", "DIFFERENCE (Dr - Cr)", 4);
        diff.diff = true;
        diff.v[0] = tot[0].subtract(tot[1]);
        diff.v[2] = tot[2].subtract(tot[3]);
        diff.v[4] = tot[4].subtract(tot[5]);
        diff.v[6] = grand.v[6].subtract(grand.v[7]);
        diff.v[8] = grand.v[8].subtract(grand.v[9]);
        boolean all = true;
        for (int g = 0; g < 5; g++) {
            s.groupOk[g] = diff.v[g * 2].signum() == 0;
            all &= s.groupOk[g];
        }
        s.balanced = all;
        s.balanceDifference = diff.v[8];
        s.rows.add(total);
        s.rows.add(ni);
        s.rows.add(grand);
        s.rows.add(diff);
        return s;
    }

    // ================================================================ STATE
    private final List<DbBridge.Acct> accts;
    private List<DbBridge.Line> lines = new ArrayList<>();
    private LocalDate from = LocalDate.of(LocalDate.now().getYear(), 1, 1);
    private LocalDate to = LocalDate.of(LocalDate.now().getYear(), 12, 31);

    private final Field fromF = new Field(9, "yyyy-mm-dd");
    private final Field toF = new Field(9, "yyyy-mm-dd");
    private final JLabel status = new JLabel(" ");
    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);
    private final NavBtn[] nav = new NavBtn[4];
    private JournalView journal;
    private CoaView coa;
    private LedgerView ledger;
    private SheetView sheet;

    // ================================================================ MAIN
    public static void main(String[] args) {
        final boolean demo = Arrays.asList(args).contains("--demo");
        int tab = 0;
        for (String a : args) if (a.startsWith("--tab=")) tab = Integer.parseInt(a.substring(6)) - 1;
        final int startTab = tab;
        SwingUtilities.invokeLater(() -> {
            setupLaf();
            if (!connect(demo)) System.exit(0);
            Main m = new Main();
            m.setVisible(true);
            m.show(startTab);
        });
    }

    static void setupLaf() {
        try { UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName()); } catch (Exception ignored) { }
        UIManager.put("Panel.background", CREAM);
        UIManager.put("OptionPane.background", CREAM);
        UIManager.put("OptionPane.messageFont", F);
        UIManager.put("OptionPane.buttonFont", FB);
        UIManager.put("Button.font", FB);
        UIManager.put("ScrollBar.thumb", new Color(0xB9CDF5));
        UIManager.put("ComboBox.font", F);
        UIManager.put("ComboBox.background", Color.WHITE);
        UIManager.put("ToolTip.font", FSMALL);
    }

    /** Connects to MySQL; on failure offers Retry / Demo mode / Exit. */
    static boolean connect(boolean demo) {
        while (true) {
            try {
                DbBridge.init(demo);
                DbBridge.ping();
                return true;
            } catch (RuntimeException ex) {
                String[] opts = {"Retry", "Try Demo Mode", "Exit"};
                int c = JOptionPane.showOptionDialog(null, ex.getMessage(), "LedgerBuddy - database not reachable",
                        JOptionPane.DEFAULT_OPTION, JOptionPane.ERROR_MESSAGE, null, opts, opts[1]);
                if (c == 1) demo = true;
                else if (c != 0) return false;
            }
        }
    }

    public Main() {
        super("LedgerBuddy - Accounting Worksheet");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        List<DbBridge.Acct> a;
        try { a = DbBridge.accounts(); } catch (RuntimeException ex) { a = new ArrayList<>(); error(ex); }
        accts = a;

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(CREAM);
        root.add(buildHeader(), BorderLayout.NORTH);
        root.add(buildSidebar(), BorderLayout.WEST);

        journal = new JournalView();
        coa = new CoaView();
        ledger = new LedgerView();
        sheet = new SheetView();
        content.setBackground(CREAM);
        content.setBorder(new EmptyBorder(16, 18, 10, 18));
        content.add(journal, "0");
        content.add(coa, "1");
        content.add(ledger, "2");
        content.add(sheet, "3");
        root.add(content, BorderLayout.CENTER);

        status.setFont(FSB);
        status.setForeground(NAVY);
        status.setBorder(new EmptyBorder(6, 16, 6, 16));
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(YELLOW);
        bar.add(status, BorderLayout.WEST);
        JLabel src = new JLabel("Data: " + DbBridge.describe().replaceAll("\\?.*", ""));
        src.setFont(FSMALL);
        src.setForeground(NAVY);
        src.setBorder(new EmptyBorder(6, 16, 6, 16));
        bar.add(src, BorderLayout.EAST);
        root.add(bar, BorderLayout.SOUTH);
        setContentPane(root);

        Dimension scr = Toolkit.getDefaultToolkit().getScreenSize();
        setSize(Math.min(1440, scr.width - 40), Math.min(860, scr.height - 60));
        setMinimumSize(new Dimension(1000, 640));
        setLocationRelativeTo(null);

        for (int i = 0; i < 4; i++) {
            final int idx = i;
            getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                    .put(KeyStroke.getKeyStroke(KeyEvent.VK_1 + i, java.awt.event.InputEvent.CTRL_DOWN_MASK), "tab" + i);
            getRootPane().getActionMap().put("tab" + i, new AbstractAction() {
                public void actionPerformed(java.awt.event.ActionEvent e) { show(idx); }
            });
        }
        reload();
        show(0);
    }

    void show(int i) {
        cards.show(content, String.valueOf(i));
        nav[i].setSelected(true);
    }

    void toast(String msg) { status.setText(msg); }

    void error(Exception ex) {
        JOptionPane.showMessageDialog(this, ex.getMessage(), "Something went wrong", JOptionPane.ERROR_MESSAGE);
    }

    /** Re-reads the journal for the chosen date range and refreshes every screen. */
    void reload() {
        try {
            lines = DbBridge.lines(from, to);
        } catch (RuntimeException ex) {
            lines = new ArrayList<>();
            error(ex);
        }
        if (journal != null) {
            journal.refresh();
            ledger.refresh();
            sheet.refresh();
        }
        fromF.setText(from.toString());
        toF.setText(to.toString());
    }

    void applyRange() {
        try {
            LocalDate f = LocalDate.parse(fromF.getText().trim());
            LocalDate t = LocalDate.parse(toF.getText().trim());
            if (f.isAfter(t)) { toast("'From' must be on or before 'To'."); return; }
            from = f; to = t;
            reload();
            toast("Showing " + from + "  to  " + to);
        } catch (DateTimeParseException ex) {
            toast("Dates must look like 2026-10-31.");
        }
    }

    // ================================================================ HEADER + SIDEBAR
    private JComponent buildHeader() {
        JPanel h = new JPanel(new BorderLayout());
        h.setBackground(NAVY);
        h.setBorder(new EmptyBorder(10, 16, 10, 16));

        JPanel brand = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        brand.setOpaque(false);
        JLabel logo = new JLabel("LB", SwingConstants.CENTER) {
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(YELLOW);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        logo.setFont(new Font("Segoe UI", Font.BOLD, 16));
        logo.setForeground(NAVY);
        logo.setPreferredSize(new Dimension(42, 42));
        JPanel titles = new JPanel(new GridLayout(2, 1));
        titles.setOpaque(false);
        JLabel t1 = new JLabel("LedgerBuddy");
        t1.setFont(FTITLE);
        t1.setForeground(Color.WHITE);
        JLabel t2 = new JLabel("Accounting Worksheet System" + (DbBridge.isDemo() ? "   -   DEMO MODE (nothing is saved)" : ""));
        t2.setFont(FSMALL);
        t2.setForeground(DbBridge.isDemo() ? YELLOW : new Color(0xBFD2FF));
        titles.add(t1);
        titles.add(t2);
        brand.add(logo);
        brand.add(titles);
        h.add(brand, BorderLayout.WEST);

        JPanel range = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 6));
        range.setOpaque(false);
        JLabel l1 = new JLabel("Period:  From");
        l1.setForeground(Color.WHITE);
        l1.setFont(FB);
        JLabel l2 = new JLabel("To");
        l2.setForeground(Color.WHITE);
        l2.setFont(FB);
        fromF.addActionListener(e -> applyRange());
        toF.addActionListener(e -> applyRange());
        Btn apply = new Btn("Apply", YELLOW, NAVY);
        apply.addActionListener(e -> applyRange());
        Btn month = new Btn("This month", BLUE2, Color.WHITE);
        month.addActionListener(e -> {
            LocalDate n = LocalDate.now();
            from = n.withDayOfMonth(1);
            to = n.withDayOfMonth(n.lengthOfMonth());
            reload();
            toast("Showing this month");
        });
        Btn year = new Btn("This year", BLUE2, Color.WHITE);
        year.addActionListener(e -> {
            int y = LocalDate.now().getYear();
            from = LocalDate.of(y, 1, 1);
            to = LocalDate.of(y, 12, 31);
            reload();
            toast("Showing this year");
        });
        Btn all = new Btn("All time", BLUE2, Color.WHITE);
        all.addActionListener(e -> {
            from = LocalDate.of(1990, 1, 1);
            to = LocalDate.of(2999, 12, 31);
            reload();
            toast("Showing all entries");
        });
        for (Component c : new Component[]{l1, fromF, l2, toF, apply, month, year, all}) range.add(c);
        h.add(range, BorderLayout.EAST);
        return h;
    }

    private JComponent buildSidebar() {
        JPanel s = new JPanel();
        s.setLayout(new BoxLayout(s, BoxLayout.Y_AXIS));
        s.setBackground(BLUE);
        s.setBorder(new EmptyBorder(18, 12, 12, 12));
        s.setPreferredSize(new Dimension(215, 0));

        JLabel menu = new JLabel("STEP BY STEP");
        menu.setFont(FSB);
        menu.setForeground(SUN);
        menu.setBorder(new EmptyBorder(0, 8, 8, 0));
        menu.setAlignmentX(Component.LEFT_ALIGNMENT);
        s.add(menu);

        String[] names = {"Journal Entry", "Chart of Accounts", "Ledger", "12-Column Worksheet"};
        ButtonGroup g = new ButtonGroup();
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            nav[i] = new NavBtn((i + 1) + "   " + names[i]);
            nav[i].setToolTipText("Ctrl+" + (i + 1));
            nav[i].addActionListener(e -> show(idx));
            nav[i].setAlignmentX(Component.LEFT_ALIGNMENT);
            nav[i].setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
            g.add(nav[i]);
            s.add(nav[i]);
            s.add(Box.createVerticalStrut(6));
        }
        s.add(Box.createVerticalGlue());

        JTextArea tip = new JTextArea("TIP\nUse the Quick Prompt in Journal Entry:\n\nDr Cash 5000, Cr Capital\n\nType, press Enter, then Post.\n\nShortcuts: Ctrl+1 to Ctrl+4");
        tip.setEditable(false);
        tip.setLineWrap(true);
        tip.setWrapStyleWord(true);
        tip.setFont(FSMALL);
        tip.setForeground(NAVY);
        tip.setBackground(BUTTER);
        tip.setBorder(new EmptyBorder(10, 10, 10, 10));
        tip.setAlignmentX(Component.LEFT_ALIGNMENT);
        tip.setMaximumSize(new Dimension(Integer.MAX_VALUE, 170));
        s.add(tip);
        return s;
    }

    // ================================================================ REUSABLE UI PIECES
    /** Rounded button. */
    static class Btn extends JButton {
        private final Color bg;
        private boolean hover;

        Btn(String text, Color bg, Color fg) {
            super(text);
            this.bg = bg;
            setForeground(fg);
            setFont(FB);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setBorder(new EmptyBorder(8, 16, 8, 16));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                public void mouseExited(MouseEvent e) { hover = false; repaint(); }
            });
        }

        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color c = !isEnabled() ? new Color(0xD5DAE6)
                    : getModel().isPressed() ? bg.darker() : hover ? bg.brighter() : bg;
            g2.setColor(c);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
            g2.dispose();
            if (!isEnabled()) setForeground(new Color(0x8A93A8));
            super.paintComponent(g);
        }
    }

    /** Toggle "pill" (selected = coloured, otherwise white). */
    static class Chip extends JToggleButton {
        private final Color on, onFg;

        Chip(String text, Color on, Color onFg) {
            super(text);
            this.on = on;
            this.onFg = onFg;
            setFont(FB);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setBorder(new EmptyBorder(7, 14, 7, 14));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(isSelected() ? on : Color.WHITE);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 18, 18);
            if (!isSelected()) {
                g2.setColor(LINE);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 18, 18);
            }
            g2.dispose();
            setForeground(isSelected() ? onFg : NAVY);
            super.paintComponent(g);
        }
    }

    /** Sidebar menu button. */
    static class NavBtn extends JToggleButton {
        NavBtn(String text) {
            super(text);
            setFont(FBIG);
            setHorizontalAlignment(SwingConstants.LEFT);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setBorder(new EmptyBorder(10, 14, 10, 10));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (isSelected()) {
                g2.setColor(YELLOW);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
            } else if (getModel().isRollover()) {
                g2.setColor(BLUE2);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
            }
            g2.dispose();
            setForeground(isSelected() ? NAVY : Color.WHITE);
            super.paintComponent(g);
        }
    }

    /** Text field with a grey hint and a blue focus ring. */
    static class Field extends JTextField {
        private final String hint;

        Field(int cols, String hint) {
            super(cols);
            this.hint = hint;
            setFont(F);
            setForeground(INK);
            setBorder(ring(false));
            addFocusListener(new FocusAdapter() {
                public void focusGained(FocusEvent e) { setBorder(ring(true)); }
                public void focusLost(FocusEvent e) { setBorder(ring(false)); }
            });
        }

        static javax.swing.border.Border ring(boolean on) {
            return BorderFactory.createCompoundBorder(new LineBorder(on ? BLUE : LINE, on ? 2 : 1, true),
                    new EmptyBorder(on ? 5 : 6, on ? 8 : 9, on ? 5 : 6, on ? 8 : 9));
        }

        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (hint != null && getText().isEmpty()) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setColor(new Color(0x9AA6C0));
                g2.setFont(getFont());
                Insets in = getInsets();
                g2.drawString(hint, in.left, in.top + g2.getFontMetrics().getAscent() + (getHeight() - in.top - in.bottom - g2.getFontMetrics().getHeight()) / 2);
                g2.dispose();
            }
        }
    }

    static JPanel card(java.awt.LayoutManager lm) {
        JPanel p = new JPanel(lm);
        p.setBackground(Color.WHITE);
        p.setBorder(BorderFactory.createCompoundBorder(new LineBorder(LINE, 1, true), new EmptyBorder(14, 16, 14, 16)));
        return p;
    }

    static JPanel plain(java.awt.LayoutManager lm) {
        JPanel p = new JPanel(lm);
        p.setOpaque(false);
        return p;
    }

    static JLabel title(String text) {
        JLabel l = new JLabel(text);
        l.setFont(FBIG);
        l.setForeground(NAVY);
        return l;
    }

    static JPanel labeled(String text, JComponent c) {
        JPanel p = plain(new BorderLayout(0, 4));
        JLabel l = new JLabel(text);
        l.setFont(FSB);
        l.setForeground(NAVY);
        p.add(l, BorderLayout.NORTH);
        p.add(c, BorderLayout.CENTER);
        return p;
    }

    /** Page heading: big title with a one-line explanation. */
    static JPanel heading(String t, String sub, JComponent... right) {
        JPanel p = plain(new BorderLayout());
        JPanel left = plain(new GridLayout(2, 1));
        JLabel a = new JLabel(t);
        a.setFont(FTITLE);
        a.setForeground(NAVY);
        JLabel b = new JLabel(sub);
        b.setFont(FSMALL);
        b.setForeground(new Color(0x4A5A82));
        left.add(a);
        left.add(b);
        p.add(left, BorderLayout.WEST);
        JPanel r = plain(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        for (JComponent c : right) r.add(c);
        p.add(r, BorderLayout.EAST);
        p.setBorder(new EmptyBorder(0, 0, 10, 0));
        return p;
    }

    /** A table model whose rows are {kind, meta, cell0, cell1, ...}. */
    static class Grid extends AbstractTableModel {
        String[] cols;
        List<Object[]> rows = new ArrayList<>();

        Grid(String... cols) { this.cols = cols; }

        void set(List<Object[]> r) { rows = r; fireTableDataChanged(); }
        void setCols(String[] c, List<Object[]> r) { cols = c; rows = r; fireTableStructureChanged(); }
        int kind(int r) { return r >= 0 && r < rows.size() ? (Integer) rows.get(r)[0] : 0; }
        Object meta(int r) { return rows.get(r)[1]; }

        public int getRowCount() { return rows.size(); }
        public int getColumnCount() { return cols.length; }
        public String getColumnName(int c) { return cols[c]; }
        public Object getValueAt(int r, int c) { return rows.get(r)[c + 2]; }
    }

    /** Cell renderer: zebra rows, right-aligned money, and row "kinds" (headers, totals ...). */
    static class CellR extends DefaultTableCellRenderer {
        private final IntUnaryOperator kind;
        private final List<Integer> right;
        private boolean compact;

        CellR compact() { compact = true; return this; }

        CellR(IntUnaryOperator kind, Integer... rightCols) {
            this.kind = kind;
            this.right = Arrays.asList(rightCols);
        }

        public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int row, int col) {
            super.getTableCellRendererComponent(t, v, sel, false, row, col);
            int k = kind == null ? 0 : kind.applyAsInt(t.convertRowIndexToModel(row));
            Color bg = row % 2 == 0 ? Color.WHITE : STRIPE;
            Color fg = INK;
            Font f = F;
            switch (k) {
                case 1: bg = SUN; f = FB; break;                         // totals
                case 2: bg = SKY; fg = NAVY; f = FB; break;              // entry / account header
                case 3: bg = YELLOW; fg = NAVY; f = FB; break;           // grand total
                case 4: bg = NAVY; fg = Color.WHITE; f = FB; break;      // difference = 0
                case 5: bg = RED; fg = Color.WHITE; f = FB; break;       // difference <> 0
                case 6: bg = BUTTER; fg = NAVY; f = FB; break;           // adjusting header / net income
                default: if (sel) { bg = SUN; } break;
            }
            setBackground(bg);
            setForeground(fg);
            setFont(compact ? f.deriveFont(12f) : f);
            setBorder(new EmptyBorder(0, compact ? 4 : 8, 0, compact ? 6 : 8));
            setHorizontalAlignment(right.contains(col) ? SwingConstants.RIGHT : SwingConstants.LEFT);
            return this;
        }
    }

    /** Applies the blue header, row height and grid look. headerH > 0 allows two-line group headers. */
    static void style(JTable t, int headerH, IntUnaryOperator groupOf) {
        t.setFont(F);
        t.setRowHeight(28);
        t.setShowVerticalLines(false);
        t.setShowHorizontalLines(true);
        t.setGridColor(new Color(0xE8EEFB));
        t.setIntercellSpacing(new Dimension(0, 1));
        t.setSelectionBackground(SUN);
        t.setSelectionForeground(INK);
        t.setFillsViewportHeight(true);
        t.setBackground(Color.WHITE);
        JTableHeader h = t.getTableHeader();
        h.setReorderingAllowed(false);
        h.setPreferredSize(new Dimension(10, headerH));
        h.setDefaultRenderer(new DefaultTableCellRenderer() {
            public Component getTableCellRendererComponent(JTable tb, Object v, boolean s, boolean f, int r, int c) {
                String text = v == null ? "" : v.toString();
                if (text.contains("|")) {
                    String[] p = text.split("\\|", 2);
                    text = "<html><div style='text-align:center'>" + p[0] + "<br><b>" + p[1] + "</b></div></html>";
                }
                super.getTableCellRendererComponent(tb, text, false, false, r, c);
                int g = groupOf == null ? 0 : groupOf.applyAsInt(c);
                setBackground(g % 2 == 0 ? BLUE : NAVY);
                setForeground(Color.WHITE);
                setFont(FSB);
                setHorizontalAlignment(SwingConstants.CENTER);
                setBorder(BorderFactory.createMatteBorder(0, 0, 3, 0, YELLOW));
                return this;
            }
        });
    }

    static JScrollPane scroll(JTable t) {
        JScrollPane sp = new JScrollPane(t);
        sp.setBorder(new LineBorder(LINE, 1, true));
        sp.getViewport().setBackground(Color.WHITE);
        sp.setColumnHeaderView(t.getTableHeader());
        return sp;
    }

    static void widths(JTable t, int... w) {
        for (int i = 0; i < w.length && i < t.getColumnCount(); i++) {
            TableColumn c = t.getColumnModel().getColumn(i);
            c.setPreferredWidth(w[i]);
        }
    }

    // ================================================================ ACCOUNT LOOKUP (used by the prompt + the line table)
    static String norm(String s) {
        return s.toLowerCase(Locale.ROOT).replace("'", "").replace("\u2019", "").replaceAll("\\s+", " ").trim();
    }

    /** Finds accounts by code or by words of the title ("cash", "owners cap", "1010"). */
    List<DbBridge.Acct> matches(String query) {
        String q = norm(query);
        List<DbBridge.Acct> out = new ArrayList<>();
        if (q.isEmpty()) return out;
        for (DbBridge.Acct a : accts) {
            if (a.code.equalsIgnoreCase(q) || norm(a.title).equals(q) || norm(a.code + "  " + a.title).equals(q)) {
                out.add(a);
                return out;
            }
        }
        String[] words = q.split(" ");
        for (DbBridge.Acct a : accts) {
            String hay = norm(a.code + " " + a.title);
            boolean all = true;
            for (String w : words) if (!hay.contains(w)) { all = false; break; }
            if (all) out.add(a);
        }
        if (out.size() > 1) {                     // prefer titles that START with what was typed
            List<DbBridge.Acct> starts = new ArrayList<>();
            for (DbBridge.Acct a : out) if (norm(a.title).startsWith(q)) starts.add(a);
            if (starts.size() == 1) return starts;
        }
        return out;
    }

    static String names(List<DbBridge.Acct> l) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < l.size() && i < 4; i++) sb.append(i > 0 ? ", " : "").append(l.get(i).title);
        if (l.size() > 4) sb.append("...");
        return sb.toString();
    }

    // ================================================================ 1. JOURNAL ENTRY
    static final class LRow { DbBridge.Acct a; BigDecimal dr, cr; }

    final class JournalView extends JPanel {
        final Field dateF = new Field(9, "yyyy-mm-dd");
        final Field descF = new Field(20, "What happened? e.g. Owner invested cash");
        final Field promptF = new Field(30, "Dr Cash 5000, Cr Owner's Capital | Owner invested cash");
        final Chip regB = new Chip("Regular", BLUE, Color.WHITE);
        final Chip adjB = new Chip("Adjusting", YELLOW, NAVY);
        final JLabel hintL = new JLabel(" ");
        final JLabel totalsL = new JLabel(" ");
        final Btn postB = new Btn("Post Entry", YELLOW, NAVY);
        final List<LRow> rows = new ArrayList<>();
        final AbstractTableModel lm;
        final JTable lt;
        final Grid grid = new Grid("Date", "Particulars", "Debit", "Credit");
        final JTable jt = new JTable(grid);

        JournalView() {
            super(new BorderLayout());
            setOpaque(false);
            add(heading("Journal Entry", "Record what happened. Debits must equal credits before you can post."), BorderLayout.NORTH);

            for (int i = 0; i < 2; i++) rows.add(new LRow());
            lm = new AbstractTableModel() {
                public int getRowCount() { return rows.size(); }
                public int getColumnCount() { return 3; }
                public String getColumnName(int c) { return new String[]{"Account  (type a name or code)", "Debit", "Credit"}[c]; }
                public boolean isCellEditable(int r, int c) { return true; }
                public Object getValueAt(int r, int c) {
                    LRow x = rows.get(r);
                    if (c == 0) return x.a == null ? "" : x.a.code + "  " + x.a.title;
                    BigDecimal b = c == 1 ? x.dr : x.cr;
                    return b == null ? "" : b.stripTrailingZeros().scale() > 0 ? b.toPlainString() : b.toBigInteger().toString();
                }
                public void setValueAt(Object v, int r, int c) { setLine(String.valueOf(v == null ? "" : v).trim(), r, c); }
            };
            lt = new JTable(lm);
            style(lt, 34, null);
            lt.setRowHeight(32);
            lt.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
            lt.setDefaultRenderer(Object.class, new CellR(null, 1, 2));
            JComboBox<String> combo = new JComboBox<>();
            combo.setEditable(true);
            for (DbBridge.Acct a : accts) combo.addItem(a.code + "  " + a.title);
            combo.setSelectedItem("");
            lt.getColumnModel().getColumn(0).setCellEditor(new DefaultCellEditor(combo));
            widths(lt, 330, 110, 110);

            // ---- form side
            JPanel form = card(new BorderLayout(0, 12));
            JPanel top = plain(new GridBagLayout());
            GridBagConstraints g = new GridBagConstraints();
            g.insets = new Insets(0, 0, 0, 10);
            g.fill = GridBagConstraints.HORIZONTAL;
            g.anchor = GridBagConstraints.NORTHWEST;
            dateF.setText(LocalDate.now().toString());
            ButtonGroup bg = new ButtonGroup();
            bg.add(regB);
            bg.add(adjB);
            regB.setSelected(true);
            JPanel seg = plain(new FlowLayout(FlowLayout.LEFT, 6, 0));
            seg.add(regB);
            seg.add(adjB);
            regB.setToolTipText("Normal day-to-day transaction (goes to Unadjusted Trial Balance)");
            adjB.setToolTipText("Month-end adjustment (goes to the Adjustments columns)");
            dateF.setPreferredSize(new Dimension(112, 34));
            dateF.setMinimumSize(new Dimension(112, 34));
            g.gridx = 0; g.weightx = 0; top.add(labeled("Date", dateF), g);
            g.gridx = 1; top.add(labeled("Entry type", seg), g);
            g.gridx = 2; g.weightx = 1; g.insets = new Insets(0, 0, 0, 0); top.add(labeled("Description", descF), g);

            JPanel promptCard = new JPanel(new BorderLayout(0, 6));
            promptCard.setBackground(BUTTER);
            promptCard.setBorder(BorderFactory.createCompoundBorder(new LineBorder(YELLOW, 2, true), new EmptyBorder(10, 12, 10, 12)));
            JLabel pl = new JLabel("QUICK PROMPT  -  type the entry in one line, press Enter");
            pl.setFont(FSB);
            pl.setForeground(NAVY);
            JPanel prow = plain(new BorderLayout(8, 0));
            prow.add(promptF, BorderLayout.CENTER);
            Btn fill = new Btn("Fill", BLUE, Color.WHITE);
            prow.add(fill, BorderLayout.EAST);
            hintL.setFont(FSMALL);
            hintL.setForeground(new Color(0x4A5A82));
            hintL.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            hintL.addMouseListener(new MouseAdapter() {
                public void mouseClicked(MouseEvent e) {
                    if (hintL.getClientProperty("example") != null) {
                        promptF.setText((String) hintL.getClientProperty("example"));
                        promptF.requestFocusInWindow();
                    }
                }
            });
            promptCard.add(pl, BorderLayout.NORTH);
            promptCard.add(prow, BorderLayout.CENTER);
            promptCard.add(hintL, BorderLayout.SOUTH);
            promptF.addActionListener(e -> runPrompt());
            fill.addActionListener(e -> runPrompt());

            JPanel north = plain(new BorderLayout(0, 12));
            north.add(top, BorderLayout.NORTH);
            north.add(promptCard, BorderLayout.CENTER);
            form.add(north, BorderLayout.NORTH);

            JPanel mid = plain(new BorderLayout(0, 8));
            JPanel lineBtns = plain(new FlowLayout(FlowLayout.LEFT, 8, 0));
            Btn addL = new Btn("+ Add line", SKY, NAVY);
            Btn delL = new Btn("- Remove line", SKY, NAVY);
            addL.addActionListener(e -> { stopEdit(); rows.add(new LRow()); lm.fireTableDataChanged(); });
            delL.addActionListener(e -> {
                stopEdit();
                int r = lt.getSelectedRow();
                if (r < 0) r = rows.size() - 1;
                if (rows.size() > 2) { rows.remove(r); lm.fireTableDataChanged(); updateTotals(); }
            });
            lineBtns.add(addL);
            lineBtns.add(delL);
            mid.add(scroll(lt), BorderLayout.CENTER);
            mid.add(lineBtns, BorderLayout.SOUTH);
            form.add(mid, BorderLayout.CENTER);

            JPanel foot = plain(new BorderLayout(10, 6));
            totalsL.setFont(FB);
            Btn clear = new Btn("Clear", SKY, NAVY);
            clear.addActionListener(e -> clearForm(true));
            postB.addActionListener(e -> post());
            JPanel fb = plain(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            fb.add(clear);
            fb.add(postB);
            foot.add(totalsL, BorderLayout.NORTH);
            foot.add(fb, BorderLayout.EAST);
            form.add(foot, BorderLayout.SOUTH);
            form.setMinimumSize(new Dimension(470, 300));

            // ---- list side
            style(jt, 34, null);
            jt.setDefaultRenderer(Object.class, new CellR(grid::kind, 2, 3));
            widths(jt, 95, 330, 110, 110);
            jt.getColumnModel().getColumn(0).setMinWidth(104);
            jt.getColumnModel().getColumn(2).setMinWidth(104);
            jt.getColumnModel().getColumn(3).setMinWidth(104);
            jt.setToolTipText(null);
            JPanel list = card(new BorderLayout(0, 10));
            JPanel lh = plain(new BorderLayout());
            lh.add(title("General Journal"), BorderLayout.WEST);
            JPanel lb = plain(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            Btn del = new Btn("Delete entry", RED, Color.WHITE);
            Btn exp = new Btn("Export CSV", BLUE, Color.WHITE);
            del.addActionListener(e -> deleteSelected());
            exp.addActionListener(e -> doExport(jt, "journal"));
            lb.add(del);
            lb.add(exp);
            lh.add(lb, BorderLayout.EAST);
            list.add(lh, BorderLayout.NORTH);
            list.add(scroll(jt), BorderLayout.CENTER);
            JLabel legend = new JLabel("Blue bars = regular entries   |   Yellow bars = adjusting entries   |   click an entry, then Delete");
            legend.setFont(FSMALL);
            legend.setForeground(new Color(0x4A5A82));
            list.add(legend, BorderLayout.SOUTH);
            list.setMinimumSize(new Dimension(520, 300));

            JSplitPane sp = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, form, list);
            sp.setBorder(null);
            sp.setOpaque(false);
            sp.setResizeWeight(0.44);
            sp.setDividerLocation(540);
            sp.setDividerSize(14);
            sp.setContinuousLayout(true);
            add(sp, BorderLayout.CENTER);

            setExampleHint();
            updateTotals();
        }

        void stopEdit() { if (lt.isEditing()) lt.getCellEditor().stopCellEditing(); }

        void say(String msg, boolean bad) {
            hintL.setForeground(bad ? RED : new Color(0x4A5A82));
            hintL.setText(msg);
            hintL.putClientProperty("example", null);
        }

        void setExampleHint() {
            DbBridge.Acct dr = null, cr = null;
            for (DbBridge.Acct a : accts) {
                if (dr == null && a.type.equals("ASSET") && a.side.equals("DEBIT")) dr = a;
                if (cr == null && a.type.equals("REVENUE")) cr = a;
            }
            for (DbBridge.Acct a : accts) if (cr == null && a.type.equals("EQUITY") && a.side.equals("CREDIT")) cr = a;
            hintL.setForeground(new Color(0x4A5A82));
            if (dr != null && cr != null) {
                String ex = "Dr " + dr.title + " 5000, Cr " + cr.title + " | Received payment";
                hintL.setText("Try (click me):  " + ex);
                hintL.putClientProperty("example", ex);
            } else {
                hintL.setText("Format:  Dr <account> <amount>, Cr <account> <amount>");
            }
        }

        void setLine(String s, int r, int c) {
            LRow x = rows.get(r);
            if (c == 0) {
                if (s.isEmpty()) x.a = null;
                else {
                    List<DbBridge.Acct> m = matches(s);
                    if (m.size() == 1) { x.a = m.get(0); say("", false); setExampleHint(); }
                    else say(m.isEmpty() ? "No account matches \"" + s + "\". Check the Chart of Accounts."
                            : "More than one account matches: " + names(m) + ". Type the code instead.", true);
                }
            } else {
                try {
                    BigDecimal b = parseMoney(s);
                    if (b != null && b.signum() == 0) b = null;
                    if (c == 1) { x.dr = b; if (b != null) x.cr = null; }
                    else        { x.cr = b; if (b != null) x.dr = null; }
                } catch (NumberFormatException ex) {
                    say("\"" + s + "\" is not a valid amount.", true);
                }
            }
            lm.fireTableRowsUpdated(r, r);
            updateTotals();
        }

        BigDecimal sum(boolean debit) {
            BigDecimal t = BigDecimal.ZERO;
            for (LRow x : rows) { BigDecimal b = debit ? x.dr : x.cr; if (b != null) t = t.add(b); }
            return t;
        }

        void updateTotals() {
            BigDecimal d = sum(true), c = sum(false);
            boolean ok = d.signum() > 0 && d.compareTo(c) == 0;
            String state = ok ? "   \u2714 Balanced" : d.signum() == 0 && c.signum() == 0 ? "  Enter the amounts"
                    : "  \u2716 Off by " + fmt(d.subtract(c).abs());
            totalsL.setText("Debit " + fmt(d.setScale(2, RoundingMode.HALF_UP)) + "    Credit " + fmt(c.setScale(2, RoundingMode.HALF_UP)) + state);
            totalsL.setForeground(ok ? OK : (d.signum() == 0 && c.signum() == 0) ? NAVY : RED);
            postB.setEnabled(ok);
        }

        // ---------------- quick prompt:  [adj:] Dr Cash 5000, Cr Capital [| description]
        void runPrompt() {
            String raw = promptF.getText().trim();
            if (raw.isEmpty()) return;
            String desc = null;
            int bar = raw.indexOf('|');
            if (bar >= 0) { desc = raw.substring(bar + 1).trim(); raw = raw.substring(0, bar).trim(); }
            boolean adj = false;
            Matcher am = Pattern.compile("^(?i)(adj|adjusting|adjustment)\\s*[:\\-]?\\s+(.*)$").matcher(raw);
            if (am.matches()) { adj = true; raw = am.group(2); }

            List<LRow> out = new ArrayList<>();
            List<Integer> missing = new ArrayList<>();
            Pattern head = Pattern.compile("^(?i)(dr|debit|d|cr|credit|c)\\.?\\s+(.+)$");
            for (String part : raw.split("[;\\n]+|,(?!\\d{3}(?!\\d))")) {
                String p = part.trim();
                if (p.isEmpty()) continue;
                Matcher m = head.matcher(p);
                if (!m.matches()) { say("Start each part with Dr or Cr:  \"" + p + "\"", true); return; }
                boolean debit = m.group(1).toLowerCase().startsWith("d");
                String rest = m.group(2).trim();
                String[] tok = rest.split("\\s+");
                BigDecimal amt = null;
                String acct = rest;
                if (tok.length > 1) {
                    String last = tok[tok.length - 1].replaceAll("[₱$,]", "");
                    try {
                        if (last.matches("(?i)\\d+(\\.\\d+)?k")) amt = new BigDecimal(last.substring(0, last.length() - 1)).multiply(BigDecimal.valueOf(1000));
                        else if (last.matches("\\d*\\.?\\d+")) amt = new BigDecimal(last);
                    } catch (NumberFormatException ignored) { amt = null; }
                    if (amt != null) acct = String.join(" ", Arrays.copyOf(tok, tok.length - 1));
                }
                List<DbBridge.Acct> found = matches(acct);
                if (found.size() != 1) {
                    say(found.isEmpty() ? "I can't find an account called \"" + acct + "\"."
                            : "\"" + acct + "\" matches several accounts: " + names(found) + ". Be more specific or use the code.", true);
                    return;
                }
                LRow x = new LRow();
                x.a = found.get(0);
                if (amt == null) missing.add(out.size());
                else if (debit) x.dr = amt; else x.cr = amt;
                // remember the side of lines that still need an amount
                if (amt == null) x.dr = debit ? BigDecimal.ZERO : null;
                out.add(x);
                if (amt == null && !debit) x.cr = BigDecimal.ZERO;
            }
            if (out.size() < 2) { say("An entry needs at least two lines (a Dr and a Cr).", true); return; }
            if (missing.size() > 1) { say("Only one line can leave out its amount - it takes the balancing figure.", true); return; }
            if (missing.size() == 1) {
                LRow x = out.get(missing.get(0));
                boolean debitSide = x.dr != null;
                BigDecimal d = BigDecimal.ZERO, c = BigDecimal.ZERO;
                for (LRow y : out) {
                    if (y == x) continue;
                    if (y.dr != null) d = d.add(y.dr);
                    if (y.cr != null) c = c.add(y.cr);
                }
                BigDecimal bal = debitSide ? c.subtract(d) : d.subtract(c);
                if (bal.signum() <= 0) { say("Can't work out the missing amount - the other side is not larger.", true); return; }
                if (debitSide) { x.dr = bal; x.cr = null; } else { x.cr = bal; x.dr = null; }
            }
            stopEdit();
            rows.clear();
            rows.addAll(out);
            lm.fireTableDataChanged();
            if (desc != null && !desc.isEmpty()) descF.setText(desc);
            if (adj) adjB.setSelected(true);
            updateTotals();
            promptF.setText("");
            say(descF.getText().trim().isEmpty() ? "Filled " + out.size() + " lines. Add a description, then Post."
                    : "Filled " + out.size() + " lines. Check them, then press Post Entry.", false);
            hintL.setForeground(OK);
        }

        void clearForm(boolean keepDate) {
            stopEdit();
            rows.clear();
            rows.add(new LRow());
            rows.add(new LRow());
            lm.fireTableDataChanged();
            descF.setText("");
            promptF.setText("");
            setExampleHint();
            updateTotals();
        }

        void post() {
            stopEdit();
            List<String> problems = new ArrayList<>();
            LocalDate date = null;
            try { date = LocalDate.parse(dateF.getText().trim()); }
            catch (DateTimeParseException ex) { problems.add("Date must look like 2026-10-31."); }
            String desc = descF.getText().trim();
            if (desc.isEmpty()) problems.add("Enter a description.");
            if (desc.length() > 255) problems.add("Description is too long (max 255 characters).");
            List<DbBridge.Line> out = new ArrayList<>();
            int n = 0;
            for (LRow x : rows) {
                n++;
                boolean empty = x.a == null && x.dr == null && x.cr == null;
                if (empty) continue;
                if (x.a == null) { problems.add("Line " + n + ": choose an account."); continue; }
                BigDecimal d = x.dr == null ? BigDecimal.ZERO : x.dr, c = x.cr == null ? BigDecimal.ZERO : x.cr;
                if (d.signum() <= 0 && c.signum() <= 0) { problems.add("Line " + n + ": enter a debit or a credit."); continue; }
                if (d.scale() > 2 && d.stripTrailingZeros().scale() > 2 || c.scale() > 2 && c.stripTrailingZeros().scale() > 2) {
                    problems.add("Line " + n + ": at most 2 decimal places."); continue;
                }
                out.add(new DbBridge.Line(0, date, desc, adjB.isSelected(), x.a.code, x.a.title,
                        d.setScale(2, RoundingMode.HALF_UP), c.setScale(2, RoundingMode.HALF_UP)));
            }
            if (out.size() < 2) problems.add("An entry needs at least two lines.");
            BigDecimal td = BigDecimal.ZERO, tc = BigDecimal.ZERO;
            for (DbBridge.Line l : out) { td = td.add(l.dr); tc = tc.add(l.cr); }
            if (td.compareTo(tc) != 0) problems.add("Debits (" + fmt(td) + ") do not equal credits (" + fmt(tc) + ").");
            if (!problems.isEmpty()) {
                JOptionPane.showMessageDialog(Main.this, String.join("\n", problems), "Please fix this first", JOptionPane.WARNING_MESSAGE);
                return;
            }
            try {
                int id = DbBridge.saveEntry(date, desc, adjB.isSelected(), out);
                boolean widened = false;
                if (date.isBefore(from)) { from = date; widened = true; }
                if (date.isAfter(to)) { to = date; widened = true; }
                clearForm(true);
                reload();
                toast("\u2714 Entry #" + id + " posted" + (widened ? "  (period widened to show it)" : ""));
                promptF.requestFocusInWindow();
            } catch (RuntimeException ex) {
                error(ex);
            }
        }

        void deleteSelected() {
            int r = jt.getSelectedRow();
            if (r < 0) { toast("Click an entry in the General Journal first."); return; }
            int id = (Integer) grid.meta(jt.convertRowIndexToModel(r));
            if (JOptionPane.showConfirmDialog(Main.this, "Delete journal entry #" + id + "?\nThis cannot be undone.",
                    "Delete entry", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION) return;
            try { DbBridge.deleteEntry(id); reload(); toast("Entry #" + id + " deleted"); }
            catch (RuntimeException ex) { error(ex); }
        }

        void refresh() {
            List<Object[]> out = new ArrayList<>();
            int last = -1;
            for (DbBridge.Line l : lines) {
                if (l.entryId != last) {
                    last = l.entryId;
                    out.add(new Object[]{l.adjusting ? 6 : 2, l.entryId, l.date.toString(),
                            "#" + l.entryId + "   " + l.desc + (l.adjusting ? "   [ADJUSTING]" : ""), "", ""});
                }
                boolean cr = l.cr.signum() > 0;
                out.add(new Object[]{0, l.entryId, "", (cr ? "          " : "   ") + l.code + "  " + l.title,
                        cr ? "" : fmt(l.dr), cr ? fmt(l.cr) : ""});
            }
            grid.set(out);
        }
    }

    void doExport(JTable t, String name) {
        Path p = ExportBridge.exportTable(this, t, name);
        if (p != null) toast("\u2714 Saved " + p.getFileName() + "  - open it in Excel");
    }

    // ================================================================ 2. CHART OF ACCOUNTS
    final class CoaView extends JPanel {
        CoaView() {
            super(new BorderLayout());
            setOpaque(false);
            Field search = new Field(18, "Search code or name...");
            add(heading("Chart of Accounts", "Fixed list given by your instructor - read only. It is loaded from sql/02_chart_of_accounts.sql.", search), BorderLayout.NORTH);
            Grid g = new Grid("Code", "Account Title", "Type", "Normal Balance", "Shows on");
            List<Object[]> rows = new ArrayList<>();
            for (DbBridge.Acct a : accts) {
                rows.add(new Object[]{0, a.code, a.code, a.title, cap(a.type), cap(a.side),
                        a.income() ? "Income Statement" : "Balance Sheet"});
            }
            g.set(rows);
            JTable t = new JTable(g);
            style(t, 34, null);
            t.setDefaultRenderer(Object.class, new CellR(null));
            widths(t, 80, 420, 120, 130, 160);
            TableRowSorter<Grid> sorter = new TableRowSorter<>(g);
            t.setRowSorter(sorter);
            search.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                void go() {
                    String q = search.getText().trim();
                    sorter.setRowFilter(q.isEmpty() ? null : javax.swing.RowFilter.regexFilter("(?i)" + Pattern.quote(q)));
                }
                public void insertUpdate(javax.swing.event.DocumentEvent e) { go(); }
                public void removeUpdate(javax.swing.event.DocumentEvent e) { go(); }
                public void changedUpdate(javax.swing.event.DocumentEvent e) { go(); }
            });
            JPanel c = card(new BorderLayout(0, 8));
            c.add(scroll(t), BorderLayout.CENTER);
            JLabel note = new JLabel(accts.isEmpty() ? "No accounts yet - run sql/02_chart_of_accounts.sql first."
                    : accts.size() + " accounts   |   Revenue and Expense go to the Income Statement, everything else to the Balance Sheet.");
            note.setFont(FSMALL);
            note.setForeground(new Color(0x4A5A82));
            c.add(note, BorderLayout.SOUTH);
            add(c, BorderLayout.CENTER);
        }

        String cap(String s) { return s.charAt(0) + s.substring(1).toLowerCase(); }
    }

    // ================================================================ 3. LEDGER
    final class LedgerView extends JPanel {
        final JComboBox<String> pick = new JComboBox<>();
        final Grid grid = new Grid("Date", "Entry", "Description", "Type", "Debit", "Credit", "Balance");
        final JTable t = new JTable(grid);

        LedgerView() {
            super(new BorderLayout());
            setOpaque(false);
            pick.addItem("All accounts");
            for (DbBridge.Acct a : accts) pick.addItem(a.code + "  " + a.title);
            pick.setPreferredSize(new Dimension(300, 34));
            pick.addActionListener(e -> refresh());
            Btn exp = new Btn("Export CSV", BLUE, Color.WHITE);
            exp.addActionListener(e -> doExport(t, "ledger"));
            JLabel lab = new JLabel("Account:");
            lab.setFont(FB);
            lab.setForeground(NAVY);
            add(heading("Ledger", "Every posting of the period, account by account, with a running balance (Dr / Cr).", lab, pick, exp), BorderLayout.NORTH);
            style(t, 34, null);
            t.setDefaultRenderer(Object.class, new CellR(grid::kind, 4, 5, 6));
            widths(t, 90, 60, 360, 90, 110, 110, 130);
            JPanel c = card(new BorderLayout());
            c.add(scroll(t), BorderLayout.CENTER);
            add(c, BorderLayout.CENTER);
        }

        void refresh() {
            int sel = pick.getSelectedIndex();
            List<Object[]> out = new ArrayList<>();
            for (int i = 0; i < accts.size(); i++) {
                if (sel > 0 && sel - 1 != i) continue;
                DbBridge.Acct a = accts.get(i);
                List<DbBridge.Line> ls = new ArrayList<>();
                for (DbBridge.Line l : lines) if (l.code.equals(a.code)) ls.add(l);
                if (sel == 0 && ls.isEmpty()) continue;
                out.add(new Object[]{2, a.code, a.code, "", a.title + "   (normal balance: " + a.side.toLowerCase() + ")", "", "", "", ""});
                BigDecimal net = BigDecimal.ZERO, td = BigDecimal.ZERO, tc = BigDecimal.ZERO;
                for (DbBridge.Line l : ls) {
                    net = net.add(l.dr).subtract(l.cr);
                    td = td.add(l.dr);
                    tc = tc.add(l.cr);
                    out.add(new Object[]{0, a.code, l.date.toString(), "#" + l.entryId, l.desc,
                            l.adjusting ? "Adjusting" : "Regular",
                            l.dr.signum() > 0 ? fmt(l.dr) : "", l.cr.signum() > 0 ? fmt(l.cr) : "", side(net)});
                }
                out.add(new Object[]{1, a.code, "", "", "Ending balance", "", fmt(td), fmt(tc), side(net)});
            }
            if (out.isEmpty()) out.add(new Object[]{0, "", "", "", "No postings in this period.", "", "", "", ""});
            grid.set(out);
        }

        String side(BigDecimal net) {
            return net.signum() == 0 ? fmt(ZERO) : fmt(net.abs()) + (net.signum() > 0 ? "  Dr" : "  Cr");
        }
    }

    // ================================================================ 4. 12-COLUMN WORKSHEET
    final class SheetView extends JPanel {
        final Grid grid = new Grid("Code");
        final JTable t = new JTable(grid);
        final Grid footGrid = new Grid("Code");
        final JTable foot = new JTable(footGrid);
        final Chip[] chips = new Chip[6];
        final JPanel banner = new JPanel(new BorderLayout(12, 0));
        final JLabel big = new JLabel();
        final JLabel ni = new JLabel();
        final JPanel checks = plain(new FlowLayout(FlowLayout.LEFT, 14, 0));
        Sheet sheet;
        int[] colGroup = new int[0];

        SheetView() {
            super(new BorderLayout());
            setOpaque(false);
            Btn exp = new Btn("Export CSV", BLUE, Color.WHITE);
            exp.addActionListener(e -> doExport(t, "worksheet-12-column"));
            JPanel north = plain(new BorderLayout());
            north.add(heading("12-Column Worksheet", "Unadjusted TB  >  Adjustments  >  Adjusted TB  >  Income Statement  >  Balance Sheet", exp), BorderLayout.NORTH);

            banner.setBackground(SKY);
            banner.setBorder(BorderFactory.createCompoundBorder(new LineBorder(LINE, 1, true), new EmptyBorder(10, 16, 10, 16)));
            big.setFont(new Font("Segoe UI", Font.BOLD, 18));
            ni.setFont(FBIG);
            ni.setForeground(NAVY);
            JPanel left = plain(new GridLayout(2, 1, 0, 4));
            left.add(big);
            checks.setOpaque(false);
            left.add(checks);
            banner.add(left, BorderLayout.CENTER);
            banner.add(ni, BorderLayout.EAST);

            JPanel chipRow = plain(new FlowLayout(FlowLayout.LEFT, 8, 6));
            String[] names = {"All 12 columns", "Unadjusted TB", "Adjustments", "Adjusted TB", "Income Statement", "Balance Sheet"};
            ButtonGroup g = new ButtonGroup();
            for (int i = 0; i < 6; i++) {
                chips[i] = new Chip(names[i], YELLOW, NAVY);
                chips[i].addActionListener(e -> rebuild());
                g.add(chips[i]);
                chipRow.add(chips[i]);
            }
            chips[0].setSelected(true);
            JPanel stack = plain(new BorderLayout(0, 4));
            stack.add(banner, BorderLayout.NORTH);
            stack.add(chipRow, BorderLayout.SOUTH);
            north.add(stack, BorderLayout.CENTER);
            add(north, BorderLayout.NORTH);

            Integer[] money = {2, 3, 4, 5, 6, 7, 8, 9, 10, 11};
            style(t, 46, c -> c < 2 ? 1 : (colGroup.length > c ? colGroup[c] : 0));
            t.setRowHeight(26);
            t.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
            t.setDefaultRenderer(Object.class, new CellR(grid::kind, money).compact());
            style(foot, 10, null);
            foot.setTableHeader(null);
            foot.setRowHeight(26);
            foot.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
            foot.setRowSelectionAllowed(false);
            foot.setDefaultRenderer(Object.class, new CellR(footGrid::kind, money).compact());
            t.getColumnModel().addColumnModelListener(new javax.swing.event.TableColumnModelListener() {
                public void columnMarginChanged(javax.swing.event.ChangeEvent e) { syncFoot(); }
                public void columnAdded(javax.swing.event.TableColumnModelEvent e) { }
                public void columnRemoved(javax.swing.event.TableColumnModelEvent e) { }
                public void columnMoved(javax.swing.event.TableColumnModelEvent e) { }
                public void columnSelectionChanged(javax.swing.event.ListSelectionEvent e) { }
            });
            JScrollPane sp = scroll(t);
            sp.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
            JPanel footWrap = plain(new BorderLayout());
            footWrap.setBorder(new EmptyBorder(4, 1, 0, 1 + new JScrollBar().getPreferredSize().width));
            footWrap.add(foot, BorderLayout.CENTER);
            JPanel c = card(new BorderLayout());
            c.add(sp, BorderLayout.CENTER);
            c.add(footWrap, BorderLayout.SOUTH);
            add(c, BorderLayout.CENTER);
        }

        void refresh() {
            sheet = buildSheet(accts, lines);
            // banner
            boolean ok = sheet.balanced;
            big.setText(ok ? "\u2714  BALANCED  -  Balance Sheet difference: " + fmt(ZERO)
                    : "\u2716  NOT BALANCED  -  Balance Sheet difference: " + fmt(sheet.balanceDifference));
            big.setForeground(ok ? OK : RED);
            if (sheet.empty) big.setText("No entries in this period yet - post a journal entry first.");
            checks.removeAll();
            for (int i = 0; i < 5; i++) {
                JLabel l = new JLabel((sheet.groupOk[i] ? "\u2714 " : "\u2716 ") + GROUPS[i]);
                l.setFont(FSB);
                l.setForeground(sheet.groupOk[i] ? OK : RED);
                checks.add(l);
            }
            ni.setText((sheet.isLoss() ? "Net Loss  " : "Net Income  ") + fmt(sheet.netIncome.abs()));
            checks.revalidate();
            checks.repaint();
            rebuild();
        }

        void rebuild() {
            if (sheet == null) return;
            int pick = 0;
            for (int i = 0; i < 6; i++) if (chips[i].isSelected()) pick = i;
            List<Integer> groups = new ArrayList<>();
            if (pick == 0) for (int g = 0; g < 5; g++) groups.add(g); else groups.add(pick - 1);

            String[] cols = new String[2 + groups.size() * 2];
            colGroup = new int[cols.length];
            cols[0] = "Code";
            cols[1] = "Account Title";
            for (int i = 0; i < groups.size(); i++) {
                cols[2 + i * 2] = SHORT[groups.get(i)] + "|Debit";
                cols[3 + i * 2] = SHORT[groups.get(i)] + "|Credit";
                colGroup[2 + i * 2] = groups.get(i) + 2;
                colGroup[3 + i * 2] = groups.get(i) + 2;
            }
            List<Object[]> out = new ArrayList<>();
            for (WRow r : sheet.rows) {
                if (r.kind == 0 && groups.size() == 1) {          // single-column view: hide accounts with nothing there
                    int g0 = groups.get(0);
                    if (r.v[g0 * 2].signum() == 0 && r.v[g0 * 2 + 1].signum() == 0) continue;
                }
                Object[] row = new Object[cols.length + 2];
                int kind = r.kind;
                if (r.diff) kind = sheet.balanced ? 4 : 5;
                row[0] = kind;
                row[1] = r.code;
                row[2] = r.code;
                row[3] = r.title;
                for (int i = 0; i < groups.size(); i++) {
                    int g = groups.get(i);
                    BigDecimal d = r.v[g * 2], c = r.v[g * 2 + 1];
                    if (r.diff) {
                        row[4 + i * 2] = fmt(d);
                        row[5 + i * 2] = "";
                    } else {
                        boolean skip = (r.kind == 6 || r.kind == 3) && g < 3;
                        row[4 + i * 2] = skip || d.signum() == 0 ? "" : fmt(d);
                        row[5 + i * 2] = skip || c.signum() == 0 ? "" : fmt(c);
                    }
                }
                out.add(row);
            }
            int tail = Math.min(4, out.size());
            grid.setCols(cols, new ArrayList<>(out.subList(0, out.size() - tail)));      // accounts (scrolls)
            footGrid.setCols(cols, new ArrayList<>(out.subList(out.size() - tail, out.size())));  // totals (always visible)
            widths(t, 55, 210);
            t.getColumnModel().getColumn(0).setMaxWidth(80);
            for (int i = 2; i < cols.length; i++) t.getColumnModel().getColumn(i).setPreferredWidth(92);
            widths(foot, 55, 210);
            for (int i = 2; i < cols.length; i++) foot.getColumnModel().getColumn(i).setPreferredWidth(92);
            syncFoot();
        }

        /** Keeps the pinned totals strip lined up with the scrolling account rows. */
        void syncFoot() {
            int n = Math.min(t.getColumnCount(), foot.getColumnCount());
            for (int i = 0; i < n; i++) {
                int w = t.getColumnModel().getColumn(i).getWidth();
                TableColumn fc = foot.getColumnModel().getColumn(i);
                fc.setPreferredWidth(w);
                fc.setWidth(w);
            }
            foot.revalidate();
            foot.repaint();
        }
    }
}
