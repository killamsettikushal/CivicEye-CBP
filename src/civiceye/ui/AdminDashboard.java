
package civiceye.ui;

import civiceye.model.Admin;
import civiceye.model.Issue;
import civiceye.service.IssueService;
import civiceye.util.AppConstants;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.plaf.SplitPaneUI;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * Full Admin Dashboard — shown after admin login.
 *
 * <p>Layout:
 * <pre>
 * ┌─ Header ─────────────────────────────────────────────────────────────────┐
 * │ 🏛 CivicEye  ⚡ ADMIN  Admin Name      [↺ Refresh]  [🔍 Filter]  [Logout]│
 * ├─ Top: Issue Table (all issues) ─────────────────────────────────────────┤
 * │  # │ Category │ Location │ Description │ Status │ Reporter ID │ Date     │
 * │  … rows, click to select …                                              │
 * ├─ Bottom: Status Update Panel ───────────────────────────────────────────┤
 * │  Selected: #3 — Roads — MG Road        [In Progress ▼]  [Update Status] │
 * ├─────────────────────────────────────────────────────────────────────────┤
 * │ CivicEye v1.0 • Admin Portal  •  Last refreshed: 10:30 AM • N issues   │
 * └─────────────────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <p>Features:
 * <ul>
 *   <li>View all civic issues from all reporters</li>
 *   <li>Click a row to load it into the status-update panel</li>
 *   <li>Select a new status (respects workflow Pending → In Progress → Resolved)</li>
 *   <li>Filter table by status (All / Pending / In Progress / Resolved)</li>
 *   <li>F5 / Refresh button reloads from the database</li>
 *   <li>All DB calls run in SwingWorker for a responsive UI</li>
 * </ul>
 */
public class AdminDashboard extends JFrame {

    // ── Colour palette ────────────────────────────────────────────────
    private static final Color BG_DARK   = new Color(10,  18,  38);
    private static final Color HDR_BG    = new Color(18,  28,  60);
    private static final Color HDR_BDR   = new Color(35,  50, 100);
    private static final Color FG_PRI    = new Color(220, 228, 255);
    private static final Color FG_MUTED  = new Color(110, 130, 190);
    private static final Color ACCENT    = new Color(90,  150, 255);
    private static final Color ADMIN_TAG = new Color(240, 160,  60);
    private static final Color DIVIDER   = new Color(35,   50, 100);
    private static final Color BAR_BG    = new Color(14,   22,  50);
    private static final Color PANEL_BG  = new Color(16,   26,  55);
    private static final Color ERROR_CLR = new Color(248, 113, 113);
    private static final Color SUCCESS   = new Color(74,  222, 128);

    // ── State ─────────────────────────────────────────────────────────
    private final Admin        admin;
    private final IssueService issueService = new IssueService();

    // ── Widget refs ───────────────────────────────────────────────────
    private IssueTablePanel tablePanel;
    private JLabel          statusBarLabel;
    private JLabel          selectedInfoLabel;
    private JComboBox<String> statusCombo;
    private JButton           updateBtn;
    private JComboBox<String> filterCombo;
    private JLabel            updateResultLabel;

    private List<Issue>   allIssues  = List.of();   // master list (unfiltered)
    private Issue         selected   = null;         // row currently selected

    private static final DateTimeFormatter TIME_FMT =
        DateTimeFormatter.ofPattern("hh:mm a");

    // ─────────────────────────────────────────────────────────────────
    public AdminDashboard(Admin admin) {
        this.admin = admin;
        LoginFrame.applyLookAndFeel();

        setTitle(AppConstants.APP_NAME + " \u2014 Admin Dashboard");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(AppConstants.ADMIN_DASH_WIDTH, AppConstants.ADMIN_DASH_HEIGHT);
        setLocationRelativeTo(null);
        setMinimumSize(new Dimension(960, 620));

        getContentPane().setBackground(BG_DARK);
        setLayout(new BorderLayout());

        add(buildHeader(),       BorderLayout.NORTH);
        add(buildMain(),         BorderLayout.CENTER);
        add(buildStatusBar(),    BorderLayout.SOUTH);

        // F5 shortcut
        KeyStroke f5 = KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0);
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(f5, "refresh");
        getRootPane().getActionMap().put("refresh", new javax.swing.AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                refreshTable();
            }
        });

        refreshTable();
    }

    // ── Header ────────────────────────────────────────────────────────
    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(HDR_BG);
        header.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, HDR_BDR),
            new EmptyBorder(12, 22, 12, 22)
        ));

        // ── Left: logo + admin badge + name ───────────────────────────
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        left.setOpaque(false);

        JLabel icon = new JLabel("\uD83C\uDFDB");
        icon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 22));
        icon.setForeground(FG_PRI);

        JLabel appName = new JLabel(AppConstants.APP_NAME);
        appName.setFont(new Font("SansSerif", Font.BOLD, 18));
        appName.setForeground(FG_PRI);

        JLabel sep = new JLabel("\u2502");
        sep.setFont(new Font("SansSerif", Font.PLAIN, 18));
        sep.setForeground(new Color(45, 60, 110));

        JLabel adminBadge = buildAdminBadge();
        JLabel adminName  = new JLabel(admin.getName());
        adminName.setFont(new Font("SansSerif", Font.PLAIN, 14));
        adminName.setForeground(FG_MUTED);

        left.add(icon);
        left.add(appName);
        left.add(sep);
        left.add(adminBadge);
        left.add(adminName);

        // ── Right: filter + refresh + logout ──────────────────────────
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);

        // Status filter combo
        JLabel filterLbl = new JLabel("Filter:");
        filterLbl.setFont(new Font("SansSerif", Font.PLAIN, 12));
        filterLbl.setForeground(FG_MUTED);

        String[] filters = { "All Issues",
                             AppConstants.STATUS_PENDING,
                             AppConstants.STATUS_IN_PROGRESS,
                             AppConstants.STATUS_RESOLVED };
        filterCombo = new JComboBox<>(filters);
        styleHeaderCombo(filterCombo);
        filterCombo.addActionListener(e -> applyFilter());

        JButton refreshBtn = buildHeaderButton("\u21BA  Refresh",
                                              new Color(30, 55, 110), new Color(50, 90, 170));
        refreshBtn.addActionListener(e -> refreshTable());

        JButton logoutBtn = buildHeaderButton("Logout",
                                              new Color(120, 30, 30), new Color(190, 65, 65));
        logoutBtn.addActionListener(e -> logout());

        right.add(filterLbl);
        right.add(filterCombo);
        right.add(Box.createHorizontalStrut(4));
        right.add(refreshBtn);
        right.add(logoutBtn);

        header.add(left,  BorderLayout.WEST);
        header.add(right, BorderLayout.EAST);
        return header;
    }

    private JLabel buildAdminBadge() {
        JLabel badge = new JLabel("  \u26A1 ADMIN  ", SwingConstants.CENTER) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                    RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(55, 33, 8));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.setColor(new Color(110, 65, 18));
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
                super.paintComponent(g);
            }
        };
        badge.setFont(new Font("SansSerif", Font.BOLD, 11));
        badge.setForeground(ADMIN_TAG);
        badge.setOpaque(false);
        badge.setBorder(new EmptyBorder(4, 2, 4, 2));
        return badge;
    }

    // ── Main content: table (top) + update panel (bottom) ─────────────
    private JSplitPane buildMain() {
        tablePanel = new IssueTablePanel("\uD83D\uDEE1\uFE0F  All Civic Issues");
        tablePanel.setMinimumSize(new Dimension(0, 200));

        // Wire row selection → populate update panel
        tablePanel.getTable().getSelectionModel()
            .addListSelectionListener(e -> {
                if (!e.getValueIsAdjusting()) onRowSelected();
            });

        JPanel updatePanel = buildUpdatePanel();
        updatePanel.setPreferredSize(new Dimension(0, 120));
        updatePanel.setMinimumSize(new Dimension(0, 110));

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                                          tablePanel, updatePanel);
        split.setResizeWeight(1.0);   // table gets all extra vertical space
        split.setDividerSize(5);
        split.setContinuousLayout(true);
        split.setBorder(null);
        split.setBackground(DIVIDER);

        SplitPaneUI spui = split.getUI();
        if (spui instanceof javax.swing.plaf.basic.BasicSplitPaneUI bui) {
            bui.getDivider().setBackground(DIVIDER);
        }

        return split;
    }

    // ── Status Update Panel ───────────────────────────────────────────
    private JPanel buildUpdatePanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(PANEL_BG);
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, DIVIDER),
            new EmptyBorder(16, 24, 16, 24)
        ));

        GridBagConstraints gc = new GridBagConstraints();
        gc.insets  = new Insets(0, 0, 0, 14);
        gc.anchor  = GridBagConstraints.WEST;
        gc.fill    = GridBagConstraints.NONE;

        // Section label
        JLabel sectionLbl = new JLabel("UPDATE ISSUE STATUS");
        sectionLbl.setFont(new Font("SansSerif", Font.BOLD, 11));
        sectionLbl.setForeground(FG_MUTED);
        gc.gridx = 0; gc.gridy = 0; gc.gridwidth = 4;
        panel.add(sectionLbl, gc);

        // Selected issue info
        selectedInfoLabel = new JLabel("← Select a row in the table above");
        selectedInfoLabel.setFont(new Font("SansSerif", Font.PLAIN, 13));
        selectedInfoLabel.setForeground(FG_MUTED);
        gc.gridy = 1; gc.gridwidth = 4; gc.insets = new Insets(10, 0, 0, 14);
        panel.add(selectedInfoLabel, gc);

        // Status combo
        String[] statuses = { AppConstants.STATUS_PENDING,
                              AppConstants.STATUS_IN_PROGRESS,
                              AppConstants.STATUS_RESOLVED };
        statusCombo = new JComboBox<>(statuses);
        styleFormCombo(statusCombo);
        statusCombo.setEnabled(false);
        statusCombo.setPreferredSize(new Dimension(160, 36));

        JLabel newStatusLbl = new JLabel("New Status:");
        newStatusLbl.setFont(new Font("SansSerif", Font.BOLD, 12));
        newStatusLbl.setForeground(FG_PRI);

        // Update button
        updateBtn = buildUpdateButton("Update Status");
        updateBtn.setEnabled(false);
        updateBtn.addActionListener(e -> attemptStatusUpdate());

        // Result label
        updateResultLabel = new JLabel(" ");
        updateResultLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        updateResultLabel.setForeground(SUCCESS);

        gc.gridwidth = 1;
        gc.gridy = 2; gc.insets = new Insets(12, 0, 0, 14);

        gc.gridx = 0; panel.add(newStatusLbl,    gc);
        gc.gridx = 1; panel.add(statusCombo,     gc);
        gc.gridx = 2; panel.add(updateBtn,       gc);
        gc.gridx = 3; gc.insets = new Insets(12, 0, 0, 0);
                       panel.add(updateResultLabel, gc);

        // Filler
        gc.gridx = 4; gc.weightx = 1.0; gc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(Box.createHorizontalGlue(), gc);

        return panel;
    }

    // ── Status bar ────────────────────────────────────────────────────
    private JPanel buildStatusBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(BAR_BG);
        bar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, DIVIDER),
            new EmptyBorder(6, 16, 6, 16)
        ));

        JLabel left = new JLabel(AppConstants.APP_NAME + " v1.0  \u2022  Admin Portal");
        left.setFont(new Font("SansSerif", Font.PLAIN, 11));
        left.setForeground(FG_MUTED);

        statusBarLabel = new JLabel("Loading\u2026");
        statusBarLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        statusBarLabel.setForeground(FG_MUTED);

        bar.add(left,           BorderLayout.WEST);
        bar.add(statusBarLabel, BorderLayout.EAST);
        return bar;
    }

    // ── Actions ───────────────────────────────────────────────────────

    /** Loads all issues from DB in a background thread and refreshes the table. */
    public void refreshTable() {
        statusBarLabel.setText("Refreshing\u2026");
        clearUpdatePanel();

        new SwingWorker<List<Issue>, Void>() {
            @Override
            protected List<Issue> doInBackground() {
                return issueService.getAllIssues();
            }
            @Override
            protected void done() {
                try {
                    allIssues = get();
                    applyFilter();       // respects current filterCombo selection
                    String time  = LocalTime.now().format(TIME_FMT);
                    statusBarLabel.setText(
                        "Last refreshed: " + time
                        + "  \u2022  " + allIssues.size() + " total issue(s)"
                        + "  \u2022  Press F5 to refresh");
                } catch (InterruptedException | ExecutionException ex) {
                    statusBarLabel.setText("Error loading issues — check database.");
                }
            }
        }.execute();
    }

    /** Filters the displayed rows based on filterCombo selection. */
    private void applyFilter() {
        String filter = (String) filterCombo.getSelectedItem();
        if (filter == null || filter.equals("All Issues")) {
            tablePanel.loadIssues(allIssues);
        } else {
            List<Issue> filtered = allIssues.stream()
                .filter(i -> i.getStatus().equalsIgnoreCase(filter))
                .toList();
            tablePanel.loadIssues(filtered);
        }
        clearUpdatePanel();
    }

    /** Called when a row is selected in the table. */
    private void onRowSelected() {
        selected = tablePanel.getSelectedIssue();
        if (selected == null) { clearUpdatePanel(); return; }

        selectedInfoLabel.setFont(new Font("SansSerif", Font.PLAIN, 13));
        selectedInfoLabel.setForeground(FG_PRI);
        selectedInfoLabel.setText(
            "Issue #" + selected.getIssueId()
            + "  \u2014  " + selected.getCategory()
            + "  \u2022  " + selected.getLocation()
            + "  \u2022  Current status: "
            + statusBadgeText(selected.getStatus())
        );

        // Pre-select the current status in the combo
        statusCombo.setSelectedItem(selected.getStatus());
        statusCombo.setEnabled(true);
        updateBtn.setEnabled(true);
        updateResultLabel.setText(" ");
    }

    /** Submits the status update to the service layer in a background thread. */
    private void attemptStatusUpdate() {
        if (selected == null) return;

        String newStatus = (String) statusCombo.getSelectedItem();
        if (newStatus == null) return;

        updateBtn.setEnabled(false);
        updateBtn.setText("Updating\u2026");
        updateResultLabel.setText("");

        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                return issueService.updateStatus(selected.getIssueId(), newStatus, admin);
            }
            @Override
            protected void done() {
                updateBtn.setEnabled(true);
                updateBtn.setText("Update Status");
                try {
                    String error = get();
                    if (error == null) {
                        updateResultLabel.setForeground(SUCCESS);
                        updateResultLabel.setText(
                            "\u2713 Issue #" + selected.getIssueId()
                            + " updated to \"" + newStatus + "\"");
                        // Refresh table to show updated badge
                        refreshTable();
                    } else {
                        updateResultLabel.setForeground(ERROR_CLR);
                        updateResultLabel.setText("\u26A0 " + error);
                    }
                } catch (InterruptedException | ExecutionException ex) {
                    updateResultLabel.setForeground(ERROR_CLR);
                    updateResultLabel.setText("\u26A0 Database error during update.");
                }
            }
        }.execute();
    }

    private void clearUpdatePanel() {
        selected = null;
        selectedInfoLabel.setText("\u2190 Select a row in the table above");
        selectedInfoLabel.setForeground(FG_MUTED);
        selectedInfoLabel.setFont(new Font("SansSerif", Font.PLAIN, 13));
        statusCombo.setEnabled(false);
        statusCombo.setSelectedIndex(0);
        updateBtn.setEnabled(false);
        updateResultLabel.setText(" ");
    }

    private void logout() {
        int choice = JOptionPane.showConfirmDialog(
            this,
            "Are you sure you want to log out?",
            "Confirm Logout",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE
        );
        if (choice == JOptionPane.YES_OPTION) {
            dispose();
            SwingUtilities.invokeLater(() -> new LoginFrame().setVisible(true));
        }
    }

    // ── Helper text ───────────────────────────────────────────────────
    private static String statusBadgeText(String s) {
        return switch (s) {
            case AppConstants.STATUS_PENDING     -> "🟡 Pending";
            case AppConstants.STATUS_IN_PROGRESS -> "🔵 In Progress";
            case AppConstants.STATUS_RESOLVED    -> "🟢 Resolved";
            default -> s;
        };
    }

    // ── Component factories ───────────────────────────────────────────
    private static void styleHeaderCombo(JComboBox<String> box) {
        box.setBackground(new Color(28, 42, 88));
        box.setForeground(FG_PRI);
        box.setFont(new Font("SansSerif", Font.PLAIN, 12));
        box.setPreferredSize(new Dimension(140, 30));
        box.setBorder(BorderFactory.createCompoundBorder(
            new MatteBorder(1, 1, 1, 1, new Color(50, 70, 130)),
            BorderFactory.createEmptyBorder(2, 6, 2, 6)
        ));
        box.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object val,
                    int idx, boolean sel, boolean foc) {
                super.getListCellRendererComponent(list, val, idx, sel, foc);
                setBackground(sel ? new Color(40, 65, 140) : new Color(20, 32, 70));
                setForeground(FG_PRI);
                setBorder(new EmptyBorder(5, 10, 5, 10));
                setFont(new Font("SansSerif", Font.PLAIN, 12));
                return this;
            }
        });
    }

    private static void styleFormCombo(JComboBox<String> box) {
        box.setBackground(new Color(10, 16, 44));
        box.setForeground(FG_PRI);
        box.setFont(new Font("SansSerif", Font.PLAIN, 13));
        box.setBorder(BorderFactory.createCompoundBorder(
            new MatteBorder(0, 0, 2, 0, new Color(45, 60, 110)),
            BorderFactory.createEmptyBorder(4, 8, 4, 8)
        ));
        box.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object val,
                    int idx, boolean sel, boolean foc) {
                super.getListCellRendererComponent(list, val, idx, sel, foc);
                setBackground(sel ? new Color(30, 55, 120) : new Color(10, 16, 44));
                setForeground(FG_PRI);
                setBorder(new EmptyBorder(6, 10, 6, 10));
                setFont(new Font("SansSerif", Font.PLAIN, 13));
                return this;
            }
        });
    }

    private JButton buildHeaderButton(String label, Color base, Color hover) {
        JButton btn = new JButton(label) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                    RenderingHints.VALUE_ANTIALIAS_ON);
                Color bg = getModel().isPressed()  ? base.darker()
                         : getModel().isRollover() ? hover
                         : base;
                g2.setColor(bg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.setColor(new Color(255, 255, 255, 55));
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                g2.setColor(Color.WHITE);
                g2.setFont(getFont());
                FontMetrics fm = g2.getFontMetrics();
                int tx = (getWidth()  - fm.stringWidth(getText())) / 2;
                int ty = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(getText(), tx, ty);
            }
        };
        btn.setFont(new Font("SansSerif", Font.BOLD, 12));
        btn.setForeground(Color.WHITE);
        btn.setOpaque(false);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(100, 30));
        return btn;
    }

    private JButton buildUpdateButton(String label) {
        JButton btn = new JButton(label) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                    RenderingHints.VALUE_ANTIALIAS_ON);
                Color bg = !isEnabled()              ? new Color(38, 70, 20)
                         : getModel().isPressed()  ? new Color(55, 140, 28)
                         : getModel().isRollover() ? new Color(70, 180, 35)
                         : new Color(55, 155, 25);
                g2.setColor(bg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.setColor(isEnabled() ? Color.WHITE : new Color(130, 200, 100));
                g2.setFont(getFont());
                FontMetrics fm = g2.getFontMetrics();
                int tx = (getWidth()  - fm.stringWidth(getText())) / 2;
                int ty = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(getText(), tx, ty);
            }
        };
        btn.setFont(new Font("SansSerif", Font.BOLD, 13));
        btn.setForeground(Color.WHITE);
        btn.setOpaque(false);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(148, 36));
        return btn;
    }
}
