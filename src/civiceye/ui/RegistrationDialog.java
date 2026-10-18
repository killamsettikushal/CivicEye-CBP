
package civiceye.ui;

import civiceye.service.AuthService;

import javax.swing.*;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.util.concurrent.ExecutionException;

import static civiceye.ui.LoginFrame.*;

/**
 * Modal dialog that allows a new user to self-register as a Citizen.
 *
 * <p>The "Admin" role cannot be assigned through this dialog — {@link AuthService}
 * hard-codes the role to "Citizen" during registration to prevent privilege escalation.
 *
 * <p>Shares the styling constants and helper methods from {@link LoginFrame}.
 */
class RegistrationDialog extends JDialog {

    private JTextField     nameField;
    private JTextField     emailField;
    private JPasswordField passwordField;
    private JPasswordField confirmField;
    private JLabel         statusLabel;
    private JButton        registerButton;

    private final AuthService authService;

    // ─────────────────────────────────────────────────────────────────
    RegistrationDialog(Frame parent, AuthService authService) {
        super(parent, "Create Account \u2014 CivicEye", true); // modal
        this.authService = authService;

        setResizable(false);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel bg = buildBackground();
        bg.setPreferredSize(new Dimension(460, 580));
        bg.add(buildCard(), new GridBagConstraints());
        setContentPane(bg);

        pack();
        setLocationRelativeTo(parent);
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
        card.setPreferredSize(new Dimension(370, 530));
        card.setLayout(new GridBagLayout());

        GridBagConstraints gc = new GridBagConstraints();
        gc.gridx   = 0;
        gc.fill    = GridBagConstraints.HORIZONTAL;
        gc.weightx = 1.0;
        gc.anchor  = GridBagConstraints.CENTER;

        // ── Header ───────────────────────────────────────────────────
        JLabel icon  = uiLabel("\u270F\uFE0F", 34, Font.PLAIN, FG_PRIMARY, SwingConstants.CENTER);
        icon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 34));
        JLabel title = uiLabel("Create Account", 22, Font.BOLD, FG_PRIMARY, SwingConstants.CENTER);
        JLabel sub   = uiLabel("Join CivicEye as a Citizen", 12, Font.PLAIN, FG_MUTED,
                               SwingConstants.CENTER);

        JSeparator sep = new JSeparator();
        sep.setForeground(new Color(48, 62, 120));

        // ── Name ─────────────────────────────────────────────────────
        JLabel nameLbl = capsLabel("Full Name");
        nameField      = buildTextField();
        withFocusBorder(nameField);

        // ── Email ────────────────────────────────────────────────────
        JLabel emailLbl = capsLabel("Email Address");
        emailField      = buildTextField();
        withFocusBorder(emailField);

        // ── Password ──────────────────────────────────────────────────
        JLabel passLbl = capsLabel("Password");
        passwordField  = buildPasswordField();
        withFocusBorder(passwordField);

        // ── Confirm ───────────────────────────────────────────────────
        JLabel confirmLbl = capsLabel("Confirm Password");
        confirmField      = buildPasswordField();
        withFocusBorder(confirmField);
        confirmField.addActionListener(e -> attemptRegister());

        // ── Status ────────────────────────────────────────────────────
        statusLabel = uiLabel(" ", 12, Font.PLAIN, ERROR_CLR, SwingConstants.CENTER);

        // ── Buttons ───────────────────────────────────────────────────
        registerButton = primaryButton("Create Account");
        registerButton.addActionListener(e -> attemptRegister());

        JButton cancelBtn = linkButton("Cancel");
        cancelBtn.addActionListener(e -> dispose());

        // ── Layout rows ───────────────────────────────────────────────
        int r = 0;
        addRow(card, gc, r++, icon,          new Insets(32, 0,  5, 0));
        addRow(card, gc, r++, title,         new Insets( 0, 0,  4, 0));
        addRow(card, gc, r++, sub,           new Insets( 0, 0, 16, 0));
        addRow(card, gc, r++, sep,           new Insets( 0,40, 20,40));
        addRow(card, gc, r++, nameLbl,       new Insets( 0,40,  6,40));
        addRow(card, gc, r++, nameField,     new Insets( 0,40,  0,40));
        addRow(card, gc, r++, emailLbl,      new Insets(14,40,  6,40));
        addRow(card, gc, r++, emailField,    new Insets( 0,40,  0,40));
        addRow(card, gc, r++, passLbl,       new Insets(14,40,  6,40));
        addRow(card, gc, r++, passwordField, new Insets( 0,40,  0,40));
        addRow(card, gc, r++, confirmLbl,    new Insets(14,40,  6,40));
        addRow(card, gc, r++, confirmField,  new Insets( 0,40,  0,40));
        addRow(card, gc, r++, statusLabel,   new Insets( 8,40,  0,40));
        addRow(card, gc, r++, registerButton,new Insets(14,40, 10,40));
        addRow(card, gc, r,   cancelBtn,     new Insets( 0, 0, 28, 0));

        return card;
    }

    // ── Register action ───────────────────────────────────────────────
    private void attemptRegister() {
        String name     = nameField.getText().trim();
        String email    = emailField.getText().trim();
        String password = new String(passwordField.getPassword());
        String confirm  = new String(confirmField.getPassword());

        if (name.isEmpty() || email.isEmpty() || password.isEmpty() || confirm.isEmpty()) {
            showStatus("Please fill in all fields.", true);
            return;
        }

        registerButton.setEnabled(false);
        registerButton.setText("Creating account\u2026");
        showStatus("", false);

        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                return authService.registerCitizen(name, email, password, confirm);
            }

            @Override
            protected void done() {
                registerButton.setEnabled(true);
                registerButton.setText("Create Account");
                try {
                    String error = get();
                    if (error == null) {
                        // Success — show message and close after a short delay
                        showStatus("\u2713 Account created! You can now log in.", false);
                        Timer t = new Timer(1800, ev -> dispose());
                        t.setRepeats(false);
                        t.start();
                    } else {
                        showStatus(error, true);
                    }
                } catch (InterruptedException | ExecutionException ex) {
                    showStatus("Registration failed. Check database config.", true);
                }
            }
        }.execute();
    }

    private void showStatus(String msg, boolean isError) {
        statusLabel.setText(msg.isEmpty() ? " " : msg);
        statusLabel.setForeground(isError ? ERROR_CLR : SUCCESS_CLR);
    }

    // ── Helpers ───────────────────────────────────────────────────────
    private static void addRow(JPanel p, GridBagConstraints gc,
                               int row, JComponent comp, Insets insets) {
        gc.gridy  = row;
        gc.insets = insets;
        p.add(comp, gc);
    }
}
