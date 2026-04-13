package com.botengine.osrs.ui;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;

/**
 * Abstract base for all per-script Axiom configuration dialogs.
 *
 * Visual structure:
 *
 *   ╔════════════════════════════════════╗
 *   ║  [ICON]  Script Name              ║  ← buildHeader()
 *   ╠════════════════════════════════════╣
 *   ║  (scrollable section area)        ║  ← buildContent() — subclass fills
 *   ║  AxiomSectionPanel × N            ║
 *   ╠════════════════════════════════════╣
 *   ║  [Save Profile]      [▶ Start]    ║  ← buildFooter()
 *   ╚════════════════════════════════════╝
 *
 * Concrete subclasses must implement:
 *   - {@link #buildContent()} — returns the scrollable form panel with sections
 *   - {@link #getSettings()}  — returns the settings object populated from the UI
 *   - {@link #getScriptName()} — display name ("Combat", "Woodcutting", …)
 *
 * The dialog is APPLICATION_MODAL — {@link #showDialog()} blocks the EDT until
 * the user dismisses via Start, Save, or the window close button.
 *
 * Example usage in AxiomPanel:
 *   CombatConfigDialog dialog = new CombatConfigDialog(this);
 *   if (dialog.showDialog()) {
 *       runner.start(script, dialog.getSettings());
 *   }
 */
public abstract class ScriptConfigDialog<T extends ScriptSettings> extends JDialog
{
    private static final int DIALOG_WIDTH  = 460;
    private static final int DIALOG_HEIGHT = 580;

    private boolean started = false;
    protected T settings;

    protected ScriptConfigDialog(JComponent parent)
    {
        super(resolveWindow(parent), getTitle(parent), Dialog.ModalityType.APPLICATION_MODAL);

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);

        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(AxiomTheme.BG_PANEL);
        setContentPane(root);

        root.add(buildHeader(), BorderLayout.NORTH);
        root.add(buildScrollWrapper(), BorderLayout.CENTER);
        root.add(buildFooter(), BorderLayout.SOUTH);

        pack();
        setSize(DIALOG_WIDTH, DIALOG_HEIGHT);
        setLocationRelativeTo(parent);
    }

    // ── Abstract contract ─────────────────────────────────────────────────────

    /** Returns the human-readable script name, e.g. "Combat". */
    public abstract String getScriptName();

    /**
     * Builds and returns the scrollable form panel.
     * Add {@link AxiomSectionPanel} instances to a BoxLayout.Y_AXIS JPanel.
     *
     * Called once from the constructor — components are created here and stored
     * as instance fields so {@link #getSettings()} can read them later.
     */
    protected abstract JPanel buildContent();

    /**
     * Constructs and returns the settings object from the current UI state.
     * Called by the caller after {@link #showDialog()} returns {@code true}.
     */
    public abstract T getSettings();

    // ── Dialog control ────────────────────────────────────────────────────────

    /**
     * Shows the dialog modally and blocks until the user dismisses.
     *
     * @return {@code true} if the user clicked "Start Script" (script should run),
     *         {@code false} if the user closed or cancelled.
     */
    public boolean showDialog()
    {
        setVisible(true); // blocks — modal
        return started;
    }

    /** Called by the Start button to record that the user confirmed. */
    protected void onStartClicked()
    {
        started = true;
        if (settings != null) settings.save(getScriptName());
        dispose();
    }

    /** Called by the Save Profile button — persists without starting. */
    protected void onSaveClicked()
    {
        T s = getSettings();
        if (s != null) s.save(getScriptName());
    }

    // ── UI construction ───────────────────────────────────────────────────────

    private JPanel buildHeader()
    {
        JPanel header = new JPanel(new BorderLayout(10, 0));
        header.setBackground(AxiomTheme.BG_DEEP);
        header.setBorder(new EmptyBorder(
            AxiomTheme.PAD_LG, AxiomTheme.PAD_LG,
            AxiomTheme.PAD_LG, AxiomTheme.PAD_LG));

        // Icon badge
        String iconText  = AxiomTheme.scriptIconText(getScriptName());
        Color  iconColor = AxiomTheme.scriptIconColor(getScriptName());

        JLabel iconBadge = new JLabel(iconText, JLabel.CENTER);
        iconBadge.setOpaque(true);
        iconBadge.setBackground(iconColor);
        iconBadge.setForeground(Color.WHITE);
        iconBadge.setFont(new Font("SansSerif", Font.BOLD, 12));
        iconBadge.setPreferredSize(new Dimension(36, 36));
        iconBadge.setBorder(BorderFactory.createLineBorder(iconColor.darker(), 1));
        header.add(iconBadge, BorderLayout.WEST);

        // Title block
        JPanel titleBlock = new JPanel();
        titleBlock.setLayout(new BoxLayout(titleBlock, BoxLayout.Y_AXIS));
        titleBlock.setBackground(AxiomTheme.BG_DEEP);

        JLabel nameLabel = new JLabel(getScriptName() + " Script");
        nameLabel.setFont(AxiomTheme.fontHeading());
        nameLabel.setForeground(AxiomTheme.TEXT);

        JLabel subLabel = new JLabel("Configure your session settings");
        subLabel.setFont(AxiomTheme.fontSmall());
        subLabel.setForeground(AxiomTheme.TEXT_DIM);

        titleBlock.add(nameLabel);
        titleBlock.add(subLabel);
        header.add(titleBlock, BorderLayout.CENTER);

        // Bottom separator
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(AxiomTheme.BG_DEEP);
        wrapper.add(header, BorderLayout.CENTER);
        wrapper.add(makeSeparator(), BorderLayout.SOUTH);
        return wrapper;
    }

    private JScrollPane buildScrollWrapper()
    {
        JPanel content = buildContent();
        content.setBackground(AxiomTheme.BG_PANEL);

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBackground(AxiomTheme.BG_PANEL);
        scroll.getViewport().setBackground(AxiomTheme.BG_PANEL);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scroll.getVerticalScrollBar().setUnitIncrement(12);

        // Style the scrollbar track to match the dark theme
        scroll.getVerticalScrollBar().setBackground(AxiomTheme.BG_DEEP);
        return scroll;
    }

    private JPanel buildFooter()
    {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(AxiomTheme.BG_DEEP);
        wrapper.add(makeSeparator(), BorderLayout.NORTH);

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        footer.setBackground(AxiomTheme.BG_DEEP);

        AxiomButton saveBtn  = new AxiomButton("💾 Save Profile", AxiomButton.Variant.SECONDARY);
        AxiomButton startBtn = new AxiomButton("▶  Start Script",  AxiomButton.Variant.PRIMARY);

        saveBtn.addActionListener(e -> onSaveClicked());
        startBtn.addActionListener(e -> onStartClicked());

        footer.add(saveBtn);
        footer.add(startBtn);
        wrapper.add(footer, BorderLayout.CENTER);
        return wrapper;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static JPanel makeSeparator()
    {
        JPanel sep = new JPanel();
        sep.setBackground(AxiomTheme.BG_DEEP);
        sep.setBorder(new MatteBorder(1, 0, 0, 0, AxiomTheme.BORDER));
        sep.setPreferredSize(new Dimension(0, 1));
        return sep;
    }

    /** Styled combo box consistent with the Axiom dark theme. */
    protected static javax.swing.JComboBox<String> makeCombo(String... options)
    {
        javax.swing.JComboBox<String> combo = new javax.swing.JComboBox<>(options);
        combo.setBackground(AxiomTheme.BG_INPUT);
        combo.setForeground(AxiomTheme.TEXT);
        combo.setFont(AxiomTheme.fontSmall());
        combo.setBorder(BorderFactory.createLineBorder(AxiomTheme.BORDER));
        return combo;
    }

    /** Styled spinner consistent with the Axiom dark theme. */
    protected static javax.swing.JSpinner makeSpinner(int min, int max, int value)
    {
        javax.swing.JSpinner spinner = new javax.swing.JSpinner(
            new javax.swing.SpinnerNumberModel(value, min, max, 1));
        spinner.setBackground(AxiomTheme.BG_INPUT);
        spinner.setForeground(AxiomTheme.TEXT);
        spinner.setFont(AxiomTheme.fontSmall());
        ((javax.swing.JSpinner.DefaultEditor) spinner.getEditor())
            .getTextField().setBackground(AxiomTheme.BG_INPUT);
        ((javax.swing.JSpinner.DefaultEditor) spinner.getEditor())
            .getTextField().setForeground(AxiomTheme.TEXT);
        return spinner;
    }

    /** Styled text field consistent with the Axiom dark theme. */
    protected static javax.swing.JTextField makeTextField(String placeholder)
    {
        javax.swing.JTextField field = new javax.swing.JTextField(placeholder);
        field.setBackground(AxiomTheme.BG_INPUT);
        field.setForeground(AxiomTheme.TEXT);
        field.setCaretColor(AxiomTheme.TEXT);
        field.setFont(AxiomTheme.fontSmall());
        field.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(AxiomTheme.BORDER),
            new EmptyBorder(3, 6, 3, 6)
        ));
        return field;
    }

    /** Styled checkbox consistent with the Axiom dark theme. */
    protected static javax.swing.JCheckBox makeCheckBox(String label, boolean selected)
    {
        javax.swing.JCheckBox cb = new javax.swing.JCheckBox(label, selected);
        cb.setBackground(AxiomTheme.BG_PANEL);
        cb.setForeground(AxiomTheme.TEXT);
        cb.setFont(AxiomTheme.fontSmall());
        cb.setFocusPainted(false);
        return cb;
    }

    /** Styled JSlider in the range [min, max] with initial value. */
    protected static javax.swing.JSlider makeSlider(int min, int max, int value)
    {
        javax.swing.JSlider slider = new javax.swing.JSlider(min, max, value);
        slider.setBackground(AxiomTheme.BG_PANEL);
        slider.setForeground(AxiomTheme.ACCENT);
        slider.setPaintTicks(false);
        slider.setPaintLabels(false);
        return slider;
    }

    private static Window resolveWindow(JComponent parent)
    {
        if (parent == null) return null;
        return SwingUtilities.getWindowAncestor(parent);
    }

    private static String getTitle(JComponent parent)
    {
        return "Axiom — Script Configuration";
    }
}
