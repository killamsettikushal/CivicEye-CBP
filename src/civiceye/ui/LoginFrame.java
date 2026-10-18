
package civiceye.ui;

import civiceye.model.Admin;
import civiceye.model.Citizen;
import civiceye.model.User;
import civiceye.service.AuthService;
import civiceye.util.AppConstants;

import javax.swing.*;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.util.concurrent.ExecutionException;

/**
 * CivicEye login screen.
 *
 * <p>On successful authentication the window closes itself and the correct
 * dashboard opens on the Swing EDT:
 * <ul>
 *   <li>{@link CitizenDashboard} for role "Citizen"</li>
 *   <li>{@link AdminDashboard}   for role "Admin"</li>
 * </ul>
 *
 * <p>The DB round-trip runs inside a {@link SwingWorker} so the UI stays
 * responsive while credentials are being verified.
 */
public class LoginFrame extends JFrame {

    // ── Colour palette ────────────────────────────────────────────────
    static final Color BG_TOP       = new Color(10,  18,  38);
    static final Color BG_BOTTOM    = new Color(24,  20,  65);
    static final Color CARD_BG      = new Color(18,  28,  60);
    static final Color CARD_BORDER  = new Color(60,  75, 140);
    static final Color FIELD_BG     = new Color(10,  16,  44);
    static final Color ACCENT       = new Color(90, 150, 255);
    static final Color ACCENT_HOV   = new Color(120,175, 255);
    static final Color ACCENT_PRESS = new Color(55, 100, 210);
    static final Color FG_PRIMARY   = new Color(220,228, 255);
    static final Color FG_MUTED     = new Color(110,130, 190);
    static final Color BORDER_IDLE  = new Color( 45, 60, 110);
    static final Color ERROR_CLR    = new Color(248,113, 113);
    static final Color SUCCESS_CLR  = new Color( 74,222, 128);

    // ── References ───────────────────────────────────────────────────
    private JTextField     emailField;
    private JPasswordField passwordField;
    private JLabel         statusLabel;
    private JButton        loginButton;

    private final AuthService authService = new AuthService();

    // ─────────────────────────────────────────────────────────────────
    public LoginFrame() {
        applyLookAndFeel();
        setTitle(AppConstants.APP_NAME + " \u2014 Login");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);

        JPanel bg = buildBackground();
        bg.add(buildCard(), new GridBagConstraints());
        setContentPane(bg);

        pack();
        setLocationRelativeTo(null);
    }

    // ── Look-and-Feel ────────────────────────────────────────────────
    static void applyLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
            UIManager.put("Button.focus",     new Color(0, 0, 0, 0));
            UIManager.put("TextField.focus",  new Color(0, 0, 0, 0));
        } catch (Exception ignored) {}
    }

    // ── Background panel (diagonal gradient) ─────────────────────────
    static JPanel buildBackground() {
        JPanel p = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                    RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setPaint(new GradientPaint(0, 0, BG_TOP,
                                              getWidth(), getHeight(), BG_BOTTOM));
                g2.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        p.setPreferredSize(new Dimension(500, 640));
        return p;
    }

    // ── Card ─────────────────────────────────────────────────────────
    private JPanel buildCard() {
        JPanel card = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                    RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(CARD_BG);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 24, 24));
                g2.setColor(CARD_BORDER);
                g2.setStroke(new BasicStroke(1.2f));
                g2.draw(new RoundRectangle2D.Float(0.6f, 0.6f,
                         getWidth() - 1.2f, getHeight() - 1.2f, 24, 24));
            }
        };
        card.setOpaque(false);
        card.setPreferredSize(new Dimension(390, 568));

        // Single-column GridBagLayout gives precise spacing control
        card.setLayout(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.gridx   = 0;
        gc.fill    = GridBagConstraints.HORIZONTAL;
        gc.weightx = 1.0;
        gc.anchor  = GridBagConstraints.CENTER;
        Insets outer = new Insets(0, 44, 0, 44);

        // ── Header ──────────────────────────────────────────────────
        JLabel icon = uiLabel("\uD83C\uDFDB", 44, Font.PLAIN, FG_PRIMARY, SwingConstants.CENTER);
        icon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 42));

        JLabel title = uiLabel(AppConstants.APP_NAME, 27, Font.BOLD, FG_PRIMARY,
                               SwingConstants.CENTER);

        JLabel sub = uiLabel("Civic Issue Reporting System", 12, Font.PLAIN, FG_MUTED,
                             SwingConstants.CENTER);

        JSeparator sep = new JSeparator();
        sep.setForeground(new Color(48, 62, 120));

        // ── Fields ──────────────────────────────────────────────────
        JLabel emailLbl = capsLabel("Email Address");
        emailField      = buildTextField();
        withFocusBorder(emailField);

        JLabel passLbl  = capsLabel("Password");
        passwordField   = buildPasswordField();
        withFocusBorder(passwordField);

        // ── Status label ────────────────────────────────────────────
        statusLabel = uiLabel(" ", 12, Font.PLAIN, ERROR_CLR, SwingConstants.CENTER);

        // ── Primary button ──────────────────────────────────────────
        loginButton = primaryButton("Login");
        loginButton.addActionListener(e -> attemptLogin());
        emailField.addActionListener(e -> passwordField.requestFocusInWindow());
        passwordField.addActionListener(e -> attemptLogin());

        // ── Registration link ────────────────────────────────────────
        JLabel prompt = uiLabel("Don\u2019t have an account?", 12, Font.PLAIN,
                                FG_MUTED, SwingConstants.CENTER);
        JButton regBtn = linkButton("Create Account");
        regBtn.addActionListener(e -> new RegistrationDialog(this, authService).setVisible(true));

        // ── Layout rows ──────────────────────────────────────────────
        int r = 0;
        addRow(card, gc, outer, r++, icon,      new Insets(36, 0,  6, 0));
        addRow(card, gc, outer, r++, title,     new Insets( 0, 0,  4, 0));
        addRow(card, gc, outer, r++, sub,       new Insets( 0, 0, 20, 0));
        addRow(card, gc, outer, r++, sep,       new Insets( 0,44, 22,44));
        addRow(card, gc, outer, r++, emailLbl,  new Insets( 0,44,  7,44));
        addRow(card, gc, outer, r++, emailField,new Insets( 0,44,  0,44));
        addRow(card, gc, outer, r++, passLbl,   new Insets(18,44,  7,44));
        addRow(card, gc, outer, r++, passwordField,
                                                new Insets( 0,44,  0,44));
        addRow(card, gc, outer, r++, statusLabel,
                                                new Insets( 8,44,  0,44));
        addRow(card, gc, outer, r++, loginButton,
                                                new Insets(14,44, 22,44));
        addRow(card, gc, outer, r++, prompt,    new Insets( 0, 0,  8, 0));
        addRow(card, gc, outer, r,   regBtn,    new Insets( 0, 0, 36, 0));

        return card;
    }

    private static void addRow(JPanel p, GridBagConstraints gc,
                               Insets defaultInsets, int row,
                               JComponent comp, Insets insets) {
        gc.gridy   = row;
        gc.insets  = insets;
        p.add(comp, gc);
    }

    // ── Login logic ──────────────────────────────────────────────────
    private void attemptLogin() {
        String email    = emailField.getText().trim();
        String password = new String(passwordField.getPassword());

        if (email.isEmpty() || password.isEmpty()) {
            showStatus("Please enter both email and password.", true);
            return;
        }

        loginButton.setEnabled(false);
        loginButton.setText("Logging in\u2026");
        showStatus("", false);

        new SwingWorker<User, Void>() {
            @Override
            protected User doInBackground() {
                return authService.login(email, password);
            }

            @Override
            protected void done() {
                loginButton.setEnabled(true);
                loginButton.setText("Login");
                try {
                    User user = get();
                    if (user != null) {
                        showStatus("Welcome, " + user.getName() + "!", false);
                        // Brief pause so user sees success message
                        Timer t = new Timer(600, ev -> openDashboard(user));
                        t.setRepeats(false);
                        t.start();
                    } else {
                        showStatus("Invalid email or password. Please try again.", true);
                        passwordField.setText("");
                        passwordField.requestFocusInWindow();
                    }
                } catch (InterruptedException | ExecutionException ex) {
                    showStatus("Connection error. Check database config.", true);
                }
            }
        }.execute();
    }

    private void openDashboard(User user) {
        dispose();
        SwingUtilities.invokeLater(() -> {
            if (user instanceof Citizen) {
                new CitizenDashboard((Citizen) user).setVisible(true);
            } else if (user instanceof Admin) {
                new AdminDashboard((Admin) user).setVisible(true);
            }
        });
    }

    void showStatus(String msg, boolean isError) {
        statusLabel.setText(msg.isEmpty() ? " " : msg);
        statusLabel.setForeground(isError ? ERROR_CLR : SUCCESS_CLR);
    }

    // ── Component factories (package-visible so RegistrationDialog can reuse) ──

    static JTextField buildTextField() {
        JTextField f = new JTextField();
        styleInput(f);
        return f;
    }

    static JPasswordField buildPasswordField() {
        JPasswordField f = new JPasswordField();
        styleInput(f);
        return f;
    }

    static void styleInput(JTextField f) {
        f.setBackground(FIELD_BG);
        f.setForeground(FG_PRIMARY);
        f.setCaretColor(FG_PRIMARY);
        f.setSelectionColor(new Color(90, 150, 255, 120));
        f.setFont(new Font("SansSerif", Font.PLAIN, 14));
        f.setPreferredSize(new Dimension(302, 42));
        f.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        f.setOpaque(true);
        idleBorder(f);
    }

    static void idleBorder(JTextField f) {
        f.setBorder(BorderFactory.createCompoundBorder(
            new MatteBorder(0, 0, 2, 0, BORDER_IDLE),
            BorderFactory.createEmptyBorder(8, 10, 6, 10)));
    }

    static void withFocusBorder(JTextField f) {
        f.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) {
                f.setBorder(BorderFactory.createCompoundBorder(
                    new MatteBorder(0, 0, 2, 0, ACCENT),
                    BorderFactory.createEmptyBorder(8, 10, 6, 10)));
            }
            @Override public void focusLost(FocusEvent e) {
                idleBorder(f);
            }
        });
    }

    static JButton primaryButton(String text) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                    RenderingHints.VALUE_ANTIALIAS_ON);
                Color bg = getModel().isPressed()  ? ACCENT_PRESS
                         : getModel().isRollover() ? ACCENT_HOV
                         : ACCENT;
                g2.setColor(bg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.setColor(Color.WHITE);
                g2.setFont(getFont());
                FontMetrics fm = g2.getFontMetrics();
                int tx = (getWidth()  - fm.stringWidth(getText())) / 2;
                int ty = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(getText(), tx, ty);
            }
        };
        btn.setFont(new Font("SansSerif", Font.BOLD, 15));
        btn.setForeground(Color.WHITE);
        btn.setOpaque(false);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(302, 48));
        btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
        return btn;
    }

    static JButton linkButton(String text) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("SansSerif", Font.BOLD, 13));
        btn.setForeground(ACCENT);
        btn.setOpaque(false);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { btn.setForeground(ACCENT_HOV); }
            @Override public void mouseExited(MouseEvent e)  { btn.setForeground(ACCENT); }
        });
        return btn;
    }

    static JLabel uiLabel(String text, int size, int style,
                          Color color, int align) {
        JLabel l = new JLabel(text, align);
        l.setFont(new Font("SansSerif", style, size));
        l.setForeground(color);
        return l;
    }

    static JLabel capsLabel(String text) {
        JLabel l = new JLabel(text.toUpperCase());
        l.setFont(new Font("SansSerif", Font.BOLD, 11));
        l.setForeground(FG_MUTED);
        l.setHorizontalAlignment(SwingConstants.LEFT);
        return l;
    }
}
