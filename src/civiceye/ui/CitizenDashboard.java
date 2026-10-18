
package civiceye.ui;

import civiceye.model.Citizen;
import civiceye.util.AppConstants;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Citizen Dashboard — main screen for logged-in citizens.
 *
 * <p><b>Phase 4 stub:</b> The header, sidebar, and logout logic are fully wired.
 * The content area displays a "coming soon" placeholder that will be
 * replaced by {@link IssueReportPanel} and {@link IssueTablePanel} in Phase 5.
 */
public class CitizenDashboard extends JFrame {

    // ── Palette (shared with LoginFrame) ─────────────────────────────
    private static final Color BG_DARK    = new Color(10,  18,  38);
    private static final Color HEADER_BG  = new Color(18,  28,  60);
    private static final Color ACCENT     = new Color(90, 150, 255);
    private static final Color FG_PRIMARY = new Color(220, 228, 255);
    private static final Color FG_MUTED   = new Color(110, 130, 190);
    private static final Color CARD_BG    = new Color(18,  28,  60);
    private static final Color CARD_BDR   = new Color(45,  60, 110);
    private static final Color SUCCESS    = new Color(74,  222, 128);

    protected final Citizen citizen;

    // ─────────────────────────────────────────────────────────────────
    public CitizenDashboard(Citizen citizen) {
        this.citizen = citizen;
        LoginFrame.applyLookAndFeel();

        setTitle(AppConstants.APP_NAME + " \u2014 Citizen Dashboard");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(AppConstants.CITIZEN_DASH_WIDTH, AppConstants.CITIZEN_DASH_HEIGHT);
        setLocationRelativeTo(null);
        setMinimumSize(new Dimension(800, 560));

        setLayout(new BorderLayout());
        add(buildHeader(), BorderLayout.NORTH);
        add(buildContent(), BorderLayout.CENTER);
    }

    // ── Header bar ────────────────────────────────────────────────────
    protected JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(HEADER_BG);
        header.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(45, 60, 110)),
            new EmptyBorder(14, 24, 14, 24)
        ));

        // Left: app name + citizen name
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        left.setOpaque(false);

        JLabel appIcon = new JLabel("\uD83C\uDFDB");
        appIcon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 22));
        appIcon.setForeground(FG_PRIMARY);

        JLabel appName = new JLabel(AppConstants.APP_NAME);
        appName.setFont(new Font("SansSerif", Font.BOLD, 18));
        appName.setForeground(FG_PRIMARY);

        JLabel separator = new JLabel(" | ");
        separator.setFont(new Font("SansSerif", Font.PLAIN, 16));
        separator.setForeground(FG_MUTED);

        JLabel userName = new JLabel("Welcome, " + citizen.getName());
        userName.setFont(new Font("SansSerif", Font.PLAIN, 14));
        userName.setForeground(FG_MUTED);

        left.add(appIcon);
        left.add(appName);
        left.add(separator);
        left.add(userName);

        // Right: points badge + logout
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        right.setOpaque(false);

        JLabel pointsBadge = buildPointsBadge();
        JButton logoutBtn  = buildLogoutButton();

        right.add(pointsBadge);
        right.add(logoutBtn);

        header.add(left,  BorderLayout.WEST);
        header.add(right, BorderLayout.EAST);
        return header;
    }

    private JLabel buildPointsBadge() {
        JLabel badge = new JLabel(
            "\u2B50 " + citizen.getRewardPoints() + " pts",
            SwingConstants.CENTER
        ) {
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
        badge.setBorder(new EmptyBorder(5, 12, 5, 12));
        badge.setPreferredSize(new Dimension(100, 32));
        return badge;
    }

    private JButton buildLogoutButton() {
        JButton btn = new JButton("Logout") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                    RenderingHints.VALUE_ANTIALIAS_ON);
                Color base = getModel().isPressed()  ? new Color(200, 60, 60)
                           : getModel().isRollover() ? new Color(240, 90, 90)
                           : new Color(210, 70, 70);
                g2.setColor(base);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
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
        btn.setPreferredSize(new Dimension(80, 32));
        btn.addActionListener(e -> logout());
        return btn;
    }

    // ── Content area ──────────────────────────────────────────────────

    /**
     * Builds the main content area.
     *
     * <p>Phase 4: Returns a placeholder panel.
     * Phase 5 will override this to return the real split-pane with
     * {@link IssueReportPanel} and {@link IssueTablePanel}.
     */
    protected JPanel buildContent() {
        JPanel outer = new JPanel(new GridBagLayout());
        outer.setBackground(BG_DARK);

        JPanel card = new JPanel();
        card.setBackground(CARD_BG);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(CARD_BDR, 1),
            new EmptyBorder(40, 60, 40, 60)
        ));

        JLabel phase = new JLabel("\uD83D\uDCCB", SwingConstants.CENTER);
        phase.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 52));
        phase.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel h1 = new JLabel("Citizen Dashboard", SwingConstants.CENTER);
        h1.setFont(new Font("SansSerif", Font.BOLD, 22));
        h1.setForeground(FG_PRIMARY);
        h1.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel info = new JLabel(
            "<html><div style='text-align:center;'>"
            + "Login routing verified \u2713<br/>"
            + "Full dashboard (issue submission &amp; tracking) arriving in Phase 5."
            + "</div></html>",
            SwingConstants.CENTER
        );
        info.setFont(new Font("SansSerif", Font.PLAIN, 14));
        info.setForeground(FG_MUTED);
        info.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel roleTag = new JLabel("Role: " + citizen.getRole()
                + "   |   User ID: " + citizen.getUserId()
                + "   |   Points: " + citizen.getRewardPoints(),
            SwingConstants.CENTER);
        roleTag.setFont(new Font("SansSerif", Font.PLAIN, 12));
        roleTag.setForeground(ACCENT);
        roleTag.setAlignmentX(Component.CENTER_ALIGNMENT);

        card.add(phase);
        card.add(Box.createVerticalStrut(12));
        card.add(h1);
        card.add(Box.createVerticalStrut(12));
        card.add(info);
        card.add(Box.createVerticalStrut(16));
        card.add(roleTag);

        outer.add(card, new GridBagConstraints());
        return outer;
    }

    // ── Logout ────────────────────────────────────────────────────────
    protected void logout() {
        int confirm = JOptionPane.showConfirmDialog(
            this,
            "Are you sure you want to log out?",
            "Confirm Logout",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE
        );
        if (confirm == JOptionPane.YES_OPTION) {
            dispose();
            SwingUtilities.invokeLater(() -> new LoginFrame().setVisible(true));
        }
    }
}
