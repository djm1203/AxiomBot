package com.axiom.plugin.ui;

import com.axiom.api.script.ScriptSettings;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
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
 * Ported from the monolith ScriptConfigDialog.java — visual design unchanged.
 *
 * Lives in axiom-plugin (not axiom-api) because it has Swing imports.
 * Script modules reference this class via axiom-plugin as a compile dependency.
 *
 * Visual structure:
 *   ╔═════════════════════════╗
 *   ║ [ICON]  Script Name     ║  buildHeader()
 *   ╠═════════════════════════╣
 *   ║  (scrollable sections)  ║  buildContent() — subclass fills
 *   ╠═════════════════════════╣
 *   ║ [Save Profile] [▶ Start]║  buildFooter()
 *   ╚═════════════════════════╝
 */
public abstract class ScriptConfigDialog<T extends ScriptSettings> extends JDialog
{
    private static final int DIALOG_WIDTH  = 460;
    private static final int DIALOG_HEIGHT = 580;

    private boolean started = false;
    protected T settings;

    protected ScriptConfigDialog(JComponent parent)
    {
        super(resolveWindow(parent), "Axiom — Script Configuration", Dialog.ModalityType.APPLICATION_MODAL);

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

    /** Human-readable script name, e.g. "Axiom Woodcutting". */
    public abstract String getScriptName();

    /** Build and return the scrollable form panel with AxiomSectionPanel sections. */
    protected abstract JPanel buildContent();

    /** Return the settings object populated from the current UI state. */
    public abstract T getSettings();

    // ── Dialog control ────────────────────────────────────────────────────────

    /**
     * Shows the dialog modally. Blocks until dismissed.
     * @return true if user clicked "Start Script"
     */
    public boolean showDialog()
    {
        setVisible(true);
        return started;
    }

    protected void onStartClicked()
    {
        started = true;
        dispose();
    }

    protected void onSaveClicked()
    {
        // Settings persistence will be wired in Phase 2
        // For Phase 1, getSettings() is called by the panel after showDialog() returns true
    }

    // ── UI construction ───────────────────────────────────────────────────────

    private JPanel buildHeader()
    {
        JPanel header = new JPanel(new BorderLayout(10, 0));
        header.setBackground(AxiomTheme.BG_DEEP);
        header.setBorder(new EmptyBorder(
            AxiomTheme.PAD_LG, AxiomTheme.PAD_LG,
            AxiomTheme.PAD_LG, AxiomTheme.PAD_LG));

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

        AxiomButton saveBtn  = new AxiomButton("Save Profile",  AxiomButton.Variant.SECONDARY);
        AxiomButton startBtn = new AxiomButton("Start Script",  AxiomButton.Variant.PRIMARY);

        saveBtn.addActionListener(e -> onSaveClicked());
        startBtn.addActionListener(e -> onStartClicked());

        footer.add(saveBtn);
        footer.add(startBtn);
        wrapper.add(footer, BorderLayout.CENTER);
        return wrapper;
    }

    private static JPanel makeSeparator()
    {
        JPanel sep = new JPanel();
        sep.setBackground(AxiomTheme.BG_DEEP);
        sep.setBorder(new MatteBorder(1, 0, 0, 0, AxiomTheme.BORDER));
        sep.setPreferredSize(new Dimension(0, 1));
        return sep;
    }

    // ── Factory helpers for subclasses ────────────────────────────────────────

    protected static JComboBox<String> makeCombo(String... options)
    {
        JComboBox<String> combo = new JComboBox<>(options);
        combo.setBackground(AxiomTheme.BG_INPUT);
        combo.setForeground(AxiomTheme.TEXT);
        combo.setFont(AxiomTheme.fontSmall());
        combo.setBorder(BorderFactory.createLineBorder(AxiomTheme.BORDER));
        return combo;
    }

    protected static JSpinner makeSpinner(int min, int max, int value)
    {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(value, min, max, 1));
        spinner.setBackground(AxiomTheme.BG_INPUT);
        spinner.setForeground(AxiomTheme.TEXT);
        spinner.setFont(AxiomTheme.fontSmall());
        ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField().setBackground(AxiomTheme.BG_INPUT);
        ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField().setForeground(AxiomTheme.TEXT);
        return spinner;
    }

    protected static JTextField makeTextField(String placeholder)
    {
        JTextField field = new JTextField(placeholder);
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

    protected static JCheckBox makeCheckBox(String label, boolean selected)
    {
        JCheckBox cb = new JCheckBox(label, selected);
        cb.setBackground(AxiomTheme.BG_PANEL);
        cb.setForeground(AxiomTheme.TEXT);
        cb.setFont(AxiomTheme.fontSmall());
        cb.setFocusPainted(false);
        return cb;
    }

    protected static JSlider makeSlider(int min, int max, int value)
    {
        JSlider slider = new JSlider(min, max, value);
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
}
