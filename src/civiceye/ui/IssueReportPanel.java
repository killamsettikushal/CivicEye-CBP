
package civiceye.ui;

import civiceye.model.Citizen;
import civiceye.model.Issue;
import civiceye.service.IssueService;
import civiceye.util.AppConstants;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

/**
 * Citizen issue-submission form panel.
 *
 * <p>Validates inputs using {@link civiceye.service.ValidationService} (via
 * {@link IssueService}), submits through the service layer, then invokes
 * {@code onSuccess} so the parent dashboard can refresh the table and update
 * the reward-points badge.
 *
 * <p>The DB call runs inside a {@link SwingWorker} to keep the UI responsive.
 */
public class IssueReportPanel extends JPanel {

    // ── Colour palette ────────────────────────────────────────────────
    private static final Color BG        = new Color(14,  22,  50);
    private static final Color TITLE_BG  = new Color(16,  25,  56);
    private static final Color FIELD_BG  = new Color(10,  16,  44);
    private static final Color ACCENT    = new Color(90, 150, 255);
    private static final Color ACCENT_H  = new Color(120,175, 255);
    private static final Color ACCENT_P  = new Color(55, 100, 210);
    private static final Color FG_PRI    = new Color(220, 228, 255);
    private static final Color FG_MUTED  = new Color(110, 130, 190);
    private static final Color BORDER    = new Color(35,  48,  95);
    private static final Color ERROR_CLR = new Color(248, 113, 113);
    private static final Color SUCCESS   = new Color(74,  222, 128);
    private static final Color WARN_CLR  = new Color(245, 180,  50);

    // ── Widgets ───────────────────────────────────────────────────────
    private JComboBox<String> categoryBox;
    private JTextField        locationField;
    private JTextArea         descArea;
    private JLabel            charCounter;
    private JLabel            statusLabel;
    private JButton           submitBtn;
    private JButton           clearBtn;

    // ── Dependencies ──────────────────────────────────────────────────
    private final Citizen          citizen;
    private final IssueService     issueService;
    private final Consumer<Issue>  onSuccess; // called after successful submit

    // ─────────────────────────────────────────────────────────────────
    public IssueReportPanel(Citizen citizen,
                            IssueService issueService,
                            Consumer<Issue> onSuccess) {
        this.citizen      = citizen;
        this.issueService = issueService;
        this.onSuccess    = onSuccess;

        setBackground(BG);
        setLayout(new BorderLayout(0, 0));
        setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, BORDER));

        add(buildTitleBar(), BorderLayout.NORTH);
        add(buildForm(),     BorderLayout.CENTER);
    }

    // ── Title bar ─────────────────────────────────────────────────────
    private JPanel buildTitleBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 10));
        bar.setBackground(TITLE_BG);
        bar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER));
        JLabel lbl = new JLabel("\uD83D\uDCDD  Report a New Issue");
        lbl.setFont(new Font("SansSerif", Font.BOLD, 13));
        lbl.setForeground(new Color(160, 180, 240));
        bar.add(lbl);
        return bar;
    }

    // ── Form body ─────────────────────────────────────────────────────
    private JPanel buildForm() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(BG);
        form.setBorder(new EmptyBorder(20, 22, 20, 22));

        GridBagConstraints gc = fullWidthGC();

        // ── Category ──────────────────────────────────────────────────
        int row = 0;
        gc.gridy = row++;
        gc.insets = new Insets(0, 0, 6, 0);
        form.add(fieldLabel("CATEGORY"), gc);

        categoryBox = buildComboBox();
        gc.gridy = row++;
        gc.insets = new Insets(0, 0, 18, 0);
        form.add(categoryBox, gc);

        // ── Location ──────────────────────────────────────────────────
        gc.gridy = row++;
        gc.insets = new Insets(0, 0, 6, 0);
        form.add(fieldLabel("LOCATION"), gc);

        locationField = buildTextField();
        LoginFrame.withFocusBorder(locationField);
        gc.gridy = row++;
        gc.insets = new Insets(0, 0, 18, 0);
        form.add(locationField, gc);

        // ── Description ───────────────────────────────────────────────
        gc.gridy = row++;
        gc.insets = new Insets(0, 0, 6, 0);
        form.add(fieldLabel("DESCRIPTION"), gc);

        descArea = buildTextArea();
        JScrollPane descScroll = new JScrollPane(descArea);
        descScroll.setBorder(BorderFactory.createCompoundBorder(
            new MatteBorder(0, 0, 2, 0, BORDER),
            BorderFactory.createEmptyBorder(0, 0, 0, 0)));
        descScroll.setBackground(FIELD_BG);
        descScroll.getViewport().setBackground(FIELD_BG);
        descScroll.setPreferredSize(new Dimension(0, 130));
        descScroll.setMinimumSize(new Dimension(0, 100));
        descScroll.getVerticalScrollBar().setUI(new IssueTablePanel.DarkScrollBarUI());
        descArea.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusGained(java.awt.event.FocusEvent e) {
                descScroll.setBorder(BorderFactory.createCompoundBorder(
                    new MatteBorder(0, 0, 2, 0, ACCENT),
                    BorderFactory.createEmptyBorder(0, 0, 0, 0)));
            }
            @Override public void focusLost(java.awt.event.FocusEvent e) {
                descScroll.setBorder(BorderFactory.createCompoundBorder(
                    new MatteBorder(0, 0, 2, 0, BORDER),
                    BorderFactory.createEmptyBorder(0, 0, 0, 0)));
            }
        });

        gc.gridy  = row++;
        gc.weighty = 1.0; // description area grows vertically with panel resize
        gc.fill    = GridBagConstraints.BOTH;
        gc.insets  = new Insets(0, 0, 4, 0);
        form.add(descScroll, gc);
        gc.weighty = 0;
        gc.fill    = GridBagConstraints.HORIZONTAL;

        // ── Character counter ─────────────────────────────────────────
        charCounter = new JLabel("0 / " + AppConstants.MAX_DESCRIPTION_LENGTH,
                                 SwingConstants.RIGHT);
        charCounter.setFont(new Font("SansSerif", Font.PLAIN, 11));
        charCounter.setForeground(FG_MUTED);
        gc.gridy  = row++;
        gc.insets = new Insets(0, 0, 14, 0);
        form.add(charCounter, gc);

        // Wire character counter
        descArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e)  { updateCounter(); }
            @Override public void removeUpdate(DocumentEvent e)  { updateCounter(); }
            @Override public void changedUpdate(DocumentEvent e) { updateCounter(); }
        });

        // ── Status / error label ──────────────────────────────────────
        statusLabel = new JLabel(" ", SwingConstants.CENTER);
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        statusLabel.setForeground(ERROR_CLR);
        gc.gridy  = row++;
        gc.insets = new Insets(0, 0, 12, 0);
        form.add(statusLabel, gc);

        // ── Buttons ───────────────────────────────────────────────────
        submitBtn = buildSubmitButton("Submit Issue");
        clearBtn  = buildClearButton("Clear");
        submitBtn.addActionListener(e -> attemptSubmit());
        clearBtn.addActionListener(e -> clearForm());

        JPanel btnRow = new JPanel(new GridLayout(1, 2, 10, 0));
        btnRow.setOpaque(false);
        btnRow.add(submitBtn);
        btnRow.add(clearBtn);

        gc.gridy  = row;
        gc.insets = new Insets(0, 0, 0, 0);
        form.add(btnRow, gc);

        return form;
    }

    // ── Submit logic ──────────────────────────────────────────────────
    private void attemptSubmit() {
        String category    = (String) categoryBox.getSelectedItem();
        String location    = locationField.getText().trim();
        String description = descArea.getText().trim();

        if (category == null || category.isBlank() || location.isBlank() || description.isBlank()) {
            showStatus("Please fill in all fields.", true);
            return;
        }

        setFormEnabled(false);
        submitBtn.setText("Submitting\u2026");
        showStatus("", false);

        new SwingWorker<Issue, Void>() {
            @Override
            protected Issue doInBackground() {
                return issueService.submitIssue(
                    citizen.getUserId(), category, location, description);
            }
            @Override
            protected void done() {
                setFormEnabled(true);
                submitBtn.setText("Submit Issue");
                try {
                    Issue submitted = get();
                    if (submitted != null) {
                        showStatus("\u2713 Issue #" + submitted.getIssueId()
                                   + " submitted successfully!", false);
                        clearForm();
                        if (onSuccess != null) onSuccess.accept(submitted);
                    } else {
                        showStatus("Submission failed. Check your inputs and try again.", true);
                    }
                } catch (InterruptedException | ExecutionException ex) {
                    showStatus("Database error during submission.", true);
                }
            }
        }.execute();
    }

    private void clearForm() {
        categoryBox.setSelectedIndex(0);
        locationField.setText("");
        descArea.setText("");
        showStatus("", false);
        categoryBox.requestFocusInWindow();
    }

    private void setFormEnabled(boolean enabled) {
        categoryBox.setEnabled(enabled);
        locationField.setEnabled(enabled);
        descArea.setEnabled(enabled);
        submitBtn.setEnabled(enabled);
        clearBtn.setEnabled(enabled);
    }

    private void showStatus(String msg, boolean isError) {
        statusLabel.setText(msg.isBlank() ? " " : msg);
        statusLabel.setForeground(isError ? ERROR_CLR : SUCCESS);
    }

    private void updateCounter() {
        int len = descArea.getText().length();
        charCounter.setText(len + " / " + AppConstants.MAX_DESCRIPTION_LENGTH);
        if (len > AppConstants.MAX_DESCRIPTION_LENGTH) {
            charCounter.setForeground(ERROR_CLR);
        } else if (len > AppConstants.MAX_DESCRIPTION_LENGTH * 0.9) {
            charCounter.setForeground(WARN_CLR);
        } else {
            charCounter.setForeground(FG_MUTED);
        }
    }

    // ── Component factories ───────────────────────────────────────────
    private JComboBox<String> buildComboBox() {
        JComboBox<String> box = new JComboBox<>(AppConstants.ISSUE_CATEGORIES);
        box.setBackground(FIELD_BG);
        box.setForeground(FG_PRI);
        box.setFont(new Font("SansSerif", Font.PLAIN, 14));
        box.setPreferredSize(new Dimension(0, 42));
        box.setBorder(BorderFactory.createCompoundBorder(
            new MatteBorder(0, 0, 2, 0, BORDER),
            BorderFactory.createEmptyBorder(4, 6, 4, 6)));
        box.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object val,
                    int idx, boolean sel, boolean foc) {
                super.getListCellRendererComponent(list, val, idx, sel, foc);
                setBackground(sel ? new Color(30, 55, 120) : FIELD_BG);
                setForeground(FG_PRI);
                setFont(new Font("SansSerif", Font.PLAIN, 14));
                setBorder(new EmptyBorder(6, 10, 6, 10));
                return this;
            }
        });
        return box;
    }

    private JTextField buildTextField() {
        JTextField f = new JTextField();
        f.setBackground(FIELD_BG);
        f.setForeground(FG_PRI);
        f.setCaretColor(FG_PRI);
        f.setSelectionColor(new Color(90, 150, 255, 120));
        f.setFont(new Font("SansSerif", Font.PLAIN, 14));
        f.setPreferredSize(new Dimension(0, 42));
        LoginFrame.idleBorder(f);
        f.setOpaque(true);
        return f;
    }

    private JTextArea buildTextArea() {
        JTextArea ta = new JTextArea();
        ta.setBackground(FIELD_BG);
        ta.setForeground(FG_PRI);
        ta.setCaretColor(FG_PRI);
        ta.setSelectionColor(new Color(90, 150, 255, 120));
        ta.setFont(new Font("SansSerif", Font.PLAIN, 14));
        ta.setLineWrap(true);
        ta.setWrapStyleWord(true);
        ta.setBorder(new EmptyBorder(8, 10, 8, 10));
        return ta;
    }

    private JButton buildSubmitButton(String text) {
        JButton btn = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                    RenderingHints.VALUE_ANTIALIAS_ON);
                Color base = !isEnabled()              ? new Color(50, 80, 160)
                           : getModel().isPressed()  ? ACCENT_P
                           : getModel().isRollover() ? ACCENT_H
                           : ACCENT;
                g2.setColor(base);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.setColor(isEnabled() ? Color.WHITE : new Color(160, 180, 220));
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
        btn.setPreferredSize(new Dimension(0, 44));
        return btn;
    }

    private JButton buildClearButton(String text) {
        JButton btn = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                    RenderingHints.VALUE_ANTIALIAS_ON);
                Color bg = getModel().isPressed()  ? new Color(50, 65, 115)
                         : getModel().isRollover() ? new Color(40, 55, 100)
                         : new Color(28, 40, 80);
                g2.setColor(bg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.setColor(new Color(55, 80, 150));
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
                g2.setColor(new Color(160, 185, 255));
                g2.setFont(getFont());
                FontMetrics fm = g2.getFontMetrics();
                int tx = (getWidth()  - fm.stringWidth(getText())) / 2;
                int ty = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(getText(), tx, ty);
            }
        };
        btn.setFont(new Font("SansSerif", Font.BOLD, 13));
        btn.setOpaque(false);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(0, 44));
        return btn;
    }

    private static JLabel fieldLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("SansSerif", Font.BOLD, 11));
        l.setForeground(new Color(110, 130, 190));
        return l;
    }

    private static GridBagConstraints fullWidthGC() {
        GridBagConstraints gc = new GridBagConstraints();
        gc.gridx   = 0;
        gc.fill    = GridBagConstraints.HORIZONTAL;
        gc.weightx = 1.0;
        gc.anchor  = GridBagConstraints.NORTHWEST;
        return gc;
    }
}
