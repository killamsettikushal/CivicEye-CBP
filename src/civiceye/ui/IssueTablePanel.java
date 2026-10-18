
package civiceye.ui;

import civiceye.model.Issue;
import civiceye.util.AppConstants;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.table.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Reusable panel that displays a list of {@link Issue} objects in a styled table.
 *
 * <p>Used by both {@link CitizenDashboard} (citizen's own reports) and
 * {@link AdminDashboard} (all reports). The admin version additionally calls
 * {@link #getSelectedIssue()} to drive its status-update workflow.
 *
 * <p>Features:
 * <ul>
 *   <li>Alternating row colours</li>
 *   <li>Coloured status badge per row (Pending / In Progress / Resolved)</li>
 *   <li>Description column auto-truncated at 45 chars with full-text tooltip</li>
 *   <li>Click-to-sort on any column header</li>
 *   <li>Empty-state illustration when no issues exist</li>
 *   <li>Dark custom scroll bar</li>
 * </ul>
 */
public class IssueTablePanel extends JPanel {

    // ── Colour palette ────────────────────────────────────────────────
    private static final Color BG       = new Color(10,  18,  38);
    static final Color TABLE_BG         = new Color(14,  22,  50);
    static final Color ROW_ALT          = new Color(17,  27,  58);
    static final Color ROW_SEL          = new Color(28,  55, 120);
    private static final Color HDR_BG   = new Color(22,  34,  74);
    private static final Color HDR_FG   = new Color(160, 180, 240);
    static final Color FG_PRI           = new Color(220, 228, 255);
    private static final Color BORDER   = new Color(35,  48,  95);

    // Status badge colours [background, foreground]
    static final Color[] PEND_COLORS = { new Color(55, 40, 8),   new Color(245, 180, 50)  };
    static final Color[] PROG_COLORS = { new Color(12, 35, 90),  new Color(100, 165, 255) };
    static final Color[] RESV_COLORS = { new Color(12, 50, 30),  new Color(80,  215, 130) };

    // Column definitions: name, min, pref, max
    private static final Object[][] COL_DEF = {
        { "#",           40,  45,   50 },
        { "Category",    80, 110,  140 },
        { "Location",   100, 150,  220 },
        { "Description",160, 250, 9999 },
        { "Status",     110, 120,  120 },
        { "Submitted",  120, 135,  145 },
    };

    // ── State ─────────────────────────────────────────────────────────
    private final DefaultTableModel model;
    private final JTable            table;
    private final List<Issue>       issues = new ArrayList<>();
    private final JPanel            emptyPanel;
    private final JScrollPane       scrollPane;
    private final JPanel            contentArea; // CardLayout: "TABLE" / "EMPTY"

    // ─────────────────────────────────────────────────────────────────
    public IssueTablePanel(String title) {
        super(new BorderLayout());
        setBackground(BG);

        // Title bar
        add(buildTitleBar(title), BorderLayout.NORTH);

        // Non-editable model; column-0 class is Integer so numeric sorting works
        model = new DefaultTableModel(colNames(), 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
            @Override public Class<?> getColumnClass(int c) {
                return c == 0 ? Integer.class : String.class;
            }
        };

        table     = buildTable();
        emptyPanel = buildEmptyPanel();

        scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setBackground(TABLE_BG);
        scrollPane.getViewport().setBackground(TABLE_BG);
        scrollPane.getVerticalScrollBar().setUI(new DarkScrollBarUI());

        contentArea = new JPanel(new CardLayout());
        contentArea.setBackground(BG);
        contentArea.add(scrollPane,  "TABLE");
        contentArea.add(emptyPanel,  "EMPTY");
        add(contentArea, BorderLayout.CENTER);

        showEmpty();
    }

    // ── Public API ────────────────────────────────────────────────────

    /**
     * Replaces the current contents with the supplied issue list.
     * Safe to call from any thread — internally dispatches to the EDT.
     */
    public void loadIssues(List<Issue> newIssues) {
        issues.clear();
        if (newIssues != null) issues.addAll(newIssues);

        SwingUtilities.invokeLater(() -> {
            model.setRowCount(0);
            if (issues.isEmpty()) { showEmpty(); return; }
            for (int i = 0; i < issues.size(); i++) {
                Issue iss = issues.get(i);
                model.addRow(new Object[]{
                    i + 1,
                    iss.getCategory(),
                    iss.getLocation(),
                    iss.getDescription(),
                    iss.getStatus(),
                    iss.getFormattedCreatedAt()
                });
            }
            showTable();
        });
    }

    /**
     * Returns the {@link Issue} corresponding to the currently selected row,
     * or {@code null} if nothing is selected.
     */
    public Issue getSelectedIssue() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) return null;
        int modelRow = table.convertRowIndexToModel(viewRow);
        return (modelRow >= 0 && modelRow < issues.size()) ? issues.get(modelRow) : null;
    }

    /** Programmatically clears the row selection. */
    public void clearSelection() { table.clearSelection(); }

    /** Returns the raw {@link JTable} for attaching external listeners. */
    public JTable getTable() { return table; }

    /** Returns the current number of displayed rows. */
    public int getIssueCount() { return issues.size(); }

    // ── Build helpers ─────────────────────────────────────────────────

    private JTable buildTable() {
        JTable t = new JTable(model);
        t.setBackground(TABLE_BG);
        t.setForeground(FG_PRI);
        t.setFont(new Font("SansSerif", Font.PLAIN, 13));
        t.setRowHeight(36);
        t.setShowGrid(false);
        t.setIntercellSpacing(new Dimension(0, 1));
        t.setSelectionBackground(ROW_SEL);
        t.setSelectionForeground(FG_PRI);
        t.setAutoCreateRowSorter(true);
        t.setFillsViewportHeight(true);
        t.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // ── Header ──
        JTableHeader header = t.getTableHeader();
        header.setBackground(HDR_BG);
        header.setForeground(HDR_FG);
        header.setFont(new Font("SansSerif", Font.BOLD, 12));
        header.setPreferredSize(new Dimension(0, 40));
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER));
        header.setReorderingAllowed(false);
        header.setDefaultRenderer(new HeaderRenderer());

        // ── Column widths & renderers ──
        for (int c = 0; c < COL_DEF.length; c++) {
            TableColumn tc = t.getColumnModel().getColumn(c);
            tc.setMinWidth  ((int) COL_DEF[c][1]);
            tc.setPreferredWidth((int) COL_DEF[c][2]);
            tc.setMaxWidth  ((int) COL_DEF[c][3]);
        }
        t.getColumnModel().getColumn(0).setCellRenderer(new CenteredRenderer());
        t.getColumnModel().getColumn(1).setCellRenderer(new PaddedRenderer());
        t.getColumnModel().getColumn(2).setCellRenderer(new PaddedRenderer());
        t.getColumnModel().getColumn(3).setCellRenderer(new DescriptionRenderer());
        t.getColumnModel().getColumn(4).setCellRenderer(new StatusBadgeRenderer());
        t.getColumnModel().getColumn(5).setCellRenderer(new PaddedRenderer());

        return t;
    }

    private JPanel buildTitleBar(String title) {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 10));
        bar.setBackground(new Color(16, 25, 56));
        bar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER));
        JLabel lbl = new JLabel(title);
        lbl.setFont(new Font("SansSerif", Font.BOLD, 13));
        lbl.setForeground(new Color(160, 180, 240));
        bar.add(lbl);
        return bar;
    }

    private JPanel buildEmptyPanel() {
        JPanel outer = new JPanel(new GridBagLayout());
        outer.setBackground(TABLE_BG);

        JPanel inner = new JPanel();
        inner.setOpaque(false);
        inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));

        JLabel ico = new JLabel("\uD83D\uDCCB", SwingConstants.CENTER);
        ico.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 52));
        ico.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel h = new JLabel("No issues yet", SwingConstants.CENTER);
        h.setFont(new Font("SansSerif", Font.BOLD, 17));
        h.setForeground(FG_PRI);
        h.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel sub = new JLabel(
            "<html><div style='text-align:center; color:#6e82be; width:260px;'>"
            + "Submit a civic issue using the form<br>to start tracking it here.</div></html>",
            SwingConstants.CENTER);
        sub.setFont(new Font("SansSerif", Font.PLAIN, 13));
        sub.setAlignmentX(Component.CENTER_ALIGNMENT);

        inner.add(ico);
        inner.add(Box.createVerticalStrut(10));
        inner.add(h);
        inner.add(Box.createVerticalStrut(8));
        inner.add(sub);

        outer.add(inner);
        return outer;
    }

    private void showEmpty() {
        ((CardLayout) contentArea.getLayout()).show(contentArea, "EMPTY");
    }
    private void showTable() {
        ((CardLayout) contentArea.getLayout()).show(contentArea, "TABLE");
    }

    private static String[] colNames() {
        String[] n = new String[COL_DEF.length];
        for (int i = 0; i < n.length; i++) n[i] = (String) COL_DEF[i][0];
        return n;
    }

    // ── Cell renderers ────────────────────────────────────────────────

    static class PaddedRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object v,
                boolean sel, boolean foc, int r, int c) {
            super.getTableCellRendererComponent(t, v, sel, foc, r, c);
            setForeground(sel ? new Color(230, 238, 255) : new Color(190, 205, 240));
            setBackground(sel ? ROW_SEL : (r % 2 == 0 ? TABLE_BG : ROW_ALT));
            setBorder(new EmptyBorder(0, 12, 0, 12));
            setFont(new Font("SansSerif", Font.PLAIN, 13));
            setOpaque(true);
            return this;
        }
    }

    static class CenteredRenderer extends PaddedRenderer {
        CenteredRenderer() { setHorizontalAlignment(SwingConstants.CENTER); }
    }

    static class DescriptionRenderer extends PaddedRenderer {
        private static final int MAX = 45;
        @Override
        public Component getTableCellRendererComponent(JTable t, Object v,
                boolean sel, boolean foc, int r, int c) {
            String full  = v != null ? v.toString() : "";
            String shown = full.length() > MAX ? full.substring(0, MAX) + "\u2026" : full;
            super.getTableCellRendererComponent(t, shown, sel, foc, r, c);
            setToolTipText(
                "<html><body style='width:300px; padding:4px;'>" + full + "</body></html>");
            return this;
        }
    }

    /** Renders the Status column as a coloured pill badge. */
    static class StatusBadgeRenderer implements TableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object v,
                boolean sel, boolean foc, int r, int c) {

            String status = v != null ? v.toString() : "";
            final Color[] cols;
            switch (status) {
                case AppConstants.STATUS_IN_PROGRESS: cols = PROG_COLORS; break;
                case AppConstants.STATUS_RESOLVED:    cols = RESV_COLORS; break;
                default:                              cols = PEND_COLORS; break;
            }
            final Color rowBg = sel ? ROW_SEL : (r % 2 == 0 ? TABLE_BG : ROW_ALT);

            // Outer wrapper (sets the alternating row colour)
            JPanel wrap = new JPanel(new GridBagLayout()) {
                @Override protected void paintComponent(Graphics g) {
                    g.setColor(rowBg);
                    g.fillRect(0, 0, getWidth(), getHeight());
                }
            };
            wrap.setOpaque(false);

            // The pill
            JLabel badge = new JLabel(status, SwingConstants.CENTER) {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g;
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                        RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(cols[0]);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                    super.paintComponent(g);
                }
            };
            badge.setFont(new Font("SansSerif", Font.BOLD, 11));
            badge.setForeground(cols[1]);
            badge.setOpaque(false);
            badge.setPreferredSize(new Dimension(100, 22));

            wrap.add(badge);
            return wrap;
        }
    }

    /** Styled table-header renderer. */
    static class HeaderRenderer extends DefaultTableCellRenderer {
        HeaderRenderer() {
            setHorizontalAlignment(SwingConstants.LEFT);
            setOpaque(true);
        }
        @Override
        public Component getTableCellRendererComponent(JTable t, Object v,
                boolean sel, boolean foc, int r, int c) {
            super.getTableCellRendererComponent(t, v, sel, foc, r, c);
            setBackground(HDR_BG);
            setForeground(HDR_FG);
            setFont(new Font("SansSerif", Font.BOLD, 12));
            setBorder(new EmptyBorder(0, 12, 0, 12));
            return this;
        }
    }

    /** Minimal dark scrollbar — no arrow buttons, slim thumb. */
    static class DarkScrollBarUI extends BasicScrollBarUI {
        @Override protected void configureScrollBarColors() {
            thumbColor     = new Color(55, 75, 140);
            thumbDarkShadowColor = new Color(55, 75, 140);
            thumbHighlightColor  = new Color(55, 75, 140);
            thumbLightShadowColor= new Color(55, 75, 140);
            trackColor     = new Color(14, 22, 50);
            trackHighlightColor  = new Color(14, 22, 50);
        }
        @Override protected JButton createDecreaseButton(int o) { return zeroButton(); }
        @Override protected JButton createIncreaseButton(int o) { return zeroButton(); }
        private static JButton zeroButton() {
            JButton b = new JButton();
            b.setPreferredSize(new Dimension(0, 0));
            b.setMinimumSize(new Dimension(0, 0));
            return b;
        }
    }
}
