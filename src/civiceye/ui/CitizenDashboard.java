
package civiceye.ui;

import civiceye.model.Citizen;
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
 * Full Citizen Dashboard — main screen shown after citizen login.
 *
 * <p>Layout:
 * <pre>
 * ┌─ Header ────────────────────────────────────────────────────────────────┐
 * │ 🏛 CivicEye │ Welcome, NAME          ⭐ N pts  [Refresh ↺]  [Logout]   │
 * ├─ Left (form) ──────────────────────┬─ Right (table) ────────────────────┤
 * │  📝 Report a New Issue             │  📋 My Reported Issues              │
 * │  Category  [combobox]              │  # │ Category │ Location │ Status  │
 * │  Location  [textfield]             │  ──┼──────────┼──────────┼──────── │
 * │  Description [textarea] 0/2000     │  … rows …                          │
 * │  [Submit Issue] [Clear]            │                                    │
 * ├────────────────────────────────────┴────────────────────────────────────┤
 * │ Status bar  •  Last refreshed: 10:30 AM  •  N issues                   │
 * └─────────────────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <p>F5 / Refresh button reloads the issue list from the database.
 * Points badge updates in-memory after each successful submission.
 */
public class CitizenDashboard extends JFrame {

    // ── Colour palette ────────────────────────────────────────────────
    private static final Color BG_DARK   = new Color(10,  18,  38);
    private static final Color HDR_BG    = new Color(18,  28,  60);
    private static final Color HDR_BDR   = new Color(35,  50, 100);
    private static final Color FG_PRI    = new Color(220, 228, 255);
    private static final Color FG_MUTED  = new Color(110, 130, 190);
    private static final Color ACCENT    = new Color(90,  150, 255);
    private static final Color SUCCESS   = new Color(74,  222, 128);
    private static final Color DIVIDER   = new Color(35,   50, 100);
    private static final Color BAR_BG    = new Color(14,   22,  50);

    // ── State ─────────────────────────────────────────────────────────
    private final Citizen      citizen;
    private final IssueService issueService = new IssueService();

    // ── Widget refs (need updating after submit) ───────────────────────
    private JLabel         pointsLabel;
    private JLabel         statusBarLabel;
    private IssueTablePanel tablePanel;

    private static final DateTimeFormatter TIME_FMT =
        DateTimeFormatter.ofPattern("hh:mm a");

    // ─────────────────────────────────────────────────────────────────
    public CitizenDashboard(Citizen citizen) {
        this.citizen = citizen;
        LoginFrame.applyLookAndFeel();

        setTitle(AppConstants.APP_NAME + " \u2014 Citizen Dashboard");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(AppConstants.CITIZEN_DASH_WIDTH, AppConstants.CITIZEN_DASH_HEIGHT);
        setLocationRelativeTo(null);
        setMinimumSize(new Dimension(900, 600));

        getContentPane().setBackground(BG_DARK);
        setLayout(new BorderLayout());

        add(buildHeader(),    BorderLayout.NORTH);
        add(buildMain(),      BorderLayout.CENTER);
        add(buildStatusBar(), BorderLayout.SOUTH);

        // F5 shortcut for refresh
        KeyStroke f5 = KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0);
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(f5, "refresh");
        getRootPane().getActionMap().put("refresh", new javax.swing.AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                refreshTable();
            }
        });

        // Load issues on open
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

        // ── Left: logo + name ──────────────────────────────────────
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        left.setOpaque(false);

        JLabel icon = new JLabel("\uD83C\uDFDB");
        icon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 22));
        icon.setForeground(FG_PRI);

        JLabel appName = new JLabel(AppConstants.APP_NAME);
        appName.setFont(new Font("SansSerif", Font.BOLD, 18));
        appName.setForeground(FG_PRI);

        JLabel sep1 = new JLabel("\u2502");
        sep1.setFont(new Font("SansSerif", Font.PLAIN, 18));
        sep1.setForeground(new Color(45, 60, 110));

        JLabel welcome = new JLabel("Welcome, " + citizen.getName());
        welcome.setFont(new Font("SansSerif", Font.PLAIN, 14));
        welcome.setForeground(FG_MUTED);

        left.add(icon);
        left.add(appName);
        left.add(sep1);
        left.add(welcome);

        // ── Right: points badge + refresh + logout ─────────────────
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);

        pointsLabel = buildPointsBadge();
        JButton refreshBtn = buildIconButton("\u21BA  Refresh", new Color(40, 60, 120),
                                             new Color(60, 90, 170));
        refreshBtn.addActionListener(e -> refreshTable());

        JButton logoutBtn = buildIconButton("Logout", new Color(130, 35, 35),
                                            new Color(200, 70, 70));
        logoutBtn.addActionListener(e -> logout());

        right.add(pointsLabel);
        right.add(Box.createHorizontalStrut(4));
        right.add(refreshBtn);
        right.add(logoutBtn);

        header.add(left,  BorderLayout.WEST);
        header.add(right, BorderLayout.EAST);
        return header;
    }

    private JLabel buildPointsBadge() {
        JLabel badge = new JLabel("\u2B50 " + citizen.getRewardPoints() + " pts",
                                  SwingConstants.CENTER) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                    RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(30, 50, 100));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
                g2.setColor(new Color(55, 80, 150));
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
                super.paintComponent(g);
            }
        };
        badge.setFont(new Font("SansSerif", Font.BOLD, 12));
        badge.setForeground(SUCCESS);
        badge.setOpaque(false);
        badge.setBorder(new EmptyBorder(5, 14, 5, 14));
        badge.setPreferredSize(new Dimension(110, 32));
        return badge;
    }

    private JButton buildIconButton(String label, Color base, Color hover) {
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
                g2.setColor(new Color(255, 255, 255, 60));
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
        btn.setPreferredSize(new Dimension(90, 32));
        return btn;
    }

    // ── Main content: split form | table ──────────────────────────────
    private JSplitPane buildMain() {
        // Form panel
        IssueReportPanel reportPanel = new IssueReportPanel(
            citizen,
            issueService,
            submittedIssue -> {
                // 1. Update in-memory points
                citizen.addRewardPoints(AppConstants.REWARD_POINTS_PER_ISSUE);
                // 2. Refresh the badge label
                pointsLabel.setText("\u2B50 " + citizen.getRewardPoints() + " pts");
                pointsLabel.repaint();
                // 3. Reload issue list
                refreshTable();
            }
        );
        reportPanel.setPreferredSize(new Dimension(360, 0));
        reportPanel.setMinimumSize(new Dimension(300, 0));

        // Table panel
        tablePanel = new IssueTablePanel("📋  My Reported Issues");
        tablePanel.setMinimumSize(new Dimension(350, 0));

        // Split pane
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                                          reportPanel, tablePanel);
        split.setDividerLocation(360);
        split.setDividerSize(5);
        split.setResizeWeight(0.0);      // table panel absorbs extra width
        split.setContinuousLayout(true);
        split.setBorder(null);
        split.setBackground(DIVIDER);

        // Recolour the divider itself
        SplitPaneUI spui = split.getUI();
        if (spui instanceof javax.swing.plaf.basic.BasicSplitPaneUI bui) {
            bui.getDivider().setBackground(DIVIDER);
        }

        return split;
    }

    // ── Status bar ────────────────────────────────────────────────────
    private JPanel buildStatusBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(BAR_BG);
        bar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, DIVIDER),
            new EmptyBorder(6, 16, 6, 16)
        ));

        JLabel left = new JLabel(AppConstants.APP_NAME + " v1.0  \u2022  Citizen Portal");
        left.setFont(new Font("SansSerif", Font.PLAIN, 11));
        left.setForeground(FG_MUTED);

        statusBarLabel = new JLabel("Loading\u2026");
        statusBarLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        statusBarLabel.setForeground(FG_MUTED);

        bar.add(left,           BorderLayout.WEST);
        bar.add(statusBarLabel, BorderLayout.EAST);
        return bar;
    }

    // ── Data refresh ──────────────────────────────────────────────────

    /**
     * Reloads the citizen's issue list from the database in a background thread.
     * Updates the table and the status bar on the EDT when done.
     */
    public void refreshTable() {
        statusBarLabel.setText("Refreshing\u2026");
        int uid = citizen.getUserId();

        new SwingWorker<List<civiceye.model.Issue>, Void>() {
            @Override
            protected List<civiceye.model.Issue> doInBackground() {
                return issueService.getIssuesByReporter(uid);
            }
            @Override
            protected void done() {
                try {
                    List<civiceye.model.Issue> issues = get();
                    tablePanel.loadIssues(issues);
                    String time = LocalTime.now().format(TIME_FMT);
                    statusBarLabel.setText(
                        "Last refreshed: " + time
                        + "  \u2022  " + issues.size() + " issue(s)  \u2022  "
                        + "Press F5 to refresh");
                } catch (InterruptedException | ExecutionException ex) {
                    statusBarLabel.setText("Error loading issues — check database connection.");
                }
            }
        }.execute();
    }

    // ── Logout ────────────────────────────────────────────────────────
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
}
