
package civiceye.ui;

import civiceye.model.Admin;
import civiceye.util.AppConstants;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Admin Dashboard — main screen for logged-in administrators.
 *
 * <p><b>Phase 4 stub:</b> The header and logout logic are fully wired.
 * The content area will be replaced by a full issue-management table
 * and status-update controls in Phase 6.
 */
public class AdminDashboard extends JFrame {

    // ── Palette ───────────────────────────────────────────────────────
    private static final Color BG_DARK    = new Color(10,  18,  38);
    private static final Color HEADER_BG  = new Color(18,  28,  60);
    private static final Color ACCENT     = new Color(90, 150, 255);
    private static final Color FG_PRIMARY = new Color(220, 228, 255);
    private static final Color FG_MUTED   = new Color(110, 130, 190);
    private static final Color CARD_BG    = new Color(18,  28,  60);
    private static final Color CARD_BDR   = new Color(45,  60, 110);
    private static final Color ADMIN_TAG  = new Color(240, 160,  60);

    protected final Admin admin;

    // ─────────────────────────────────────────────────────────────────
    public AdminDashboard(Admin admin) {
        this.admin = admin;
        LoginFrame.applyLookAndFeel();

        setTitle(AppConstants.APP_NAME + " \u2014 Admin Dashboard");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(AppConstants.ADMIN_DASH_WIDTH, AppConstants.ADMIN_DASH_HEIGHT);
        setLocationRelativeTo(null);
        setMinimumSize(new Dimension(900, 580));

        setLayout(new BorderLayout());
        add(buildHeader(),  BorderLayout.NORTH);
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

        // Left: app name + admin name
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        left.setOpaque(false);

        JLabel appIcon = new JLabel("\uD83C\uDFDB");
        appIcon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 22));
        appIcon.setForeground(FG_PRIMARY);

        JLabel appName = new JLabel(AppConstants.APP_NAME);
        appName.setFont(new Font("SansSerif", Font.BOLD, 18));
        appName.setForeground(FG_PRIMARY);

        JLabel sep = new JLabel(" | ");
        sep.setFont(new Font("SansSerif", Font.PLAIN, 16));
        sep.setForeground(FG_MUTED);

        // Admin role badge
        JLabel adminBadge = new JLabel("  \u26A1 ADMIN  ") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                    RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(60, 35, 10));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.setColor(new Color(120, 70, 20));
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(0, 0, getWidth()-1, getHeight()-1, 10, 10);
                super.paintComponent(g);
            }
        };
        adminBadge.setFont(new Font("SansSerif", Font.BOLD, 11));
        adminBadge.setForeground(ADMIN_TAG);
        adminBadge.setOpaque(false);
        adminBadge.setBorder(new EmptyBorder(4, 0, 4, 0));

        JLabel userName = new JLabel(admin.getName());
        userName.setFont(new Font("SansSerif", Font.PLAIN, 14));
        userName.setForeground(FG_MUTED);

        left.add(appIcon);
        left.add(appName);
        left.add(sep);
        left.add(adminBadge);
        left.add(userName);

        // Right: logout
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        right.setOpaque(false);
        right.add(buildLogoutButton());

        header.add(left,  BorderLayout.WEST);
        header.add(right, BorderLayout.EAST);
        return header;
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
     * Phase 6 will replace this with a full issue-management table,
     * search filters, and status-update controls.
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

        JLabel phase = new JLabel("\uD83D\uDEE1\uFE0F", SwingConstants.CENTER);
        phase.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 52));
        phase.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel h1 = new JLabel("Admin Dashboard", SwingConstants.CENTER);
        h1.setFont(new Font("SansSerif", Font.BOLD, 22));
        h1.setForeground(FG_PRIMARY);
        h1.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel info = new JLabel(
            "<html><div style='text-align:center;'>"
            + "Admin login routing verified \u2713<br/>"
            + "Full issue management (view all, filter, update status) arriving in Phase 6."
            + "</div></html>",
            SwingConstants.CENTER
        );
        info.setFont(new Font("SansSerif", Font.PLAIN, 14));
        info.setForeground(FG_MUTED);
        info.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel roleTag = new JLabel("Role: " + admin.getRole()
                + "   |   User ID: " + admin.getUserId(),
            SwingConstants.CENTER);
        roleTag.setFont(new Font("SansSerif", Font.PLAIN, 12));
        roleTag.setForeground(ADMIN_TAG);
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
