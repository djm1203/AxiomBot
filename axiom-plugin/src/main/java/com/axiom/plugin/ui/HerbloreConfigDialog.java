package com.axiom.plugin.ui;

import com.axiom.scripts.herblore.HerbloreSettings;
import com.axiom.scripts.herblore.HerbloreSettings.HerbType;
import com.axiom.scripts.herblore.HerbloreSettings.Method;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;

/**
 * Configuration dialog for the Herblore script.
 *
 * Convention: HerbloreScript → HerbloreConfigDialog
 * Discovered at runtime via Class.forName() in AxiomPanel.openConfigDialog().
 */
public class HerbloreConfigDialog extends ScriptConfigDialog<HerbloreSettings>
{
    // ── Controls ──────────────────────────────────────────────────────────────
    private JComboBox<String> methodCombo;
    private JComboBox<String> herbCombo;
    private JSpinner          secondaryIdSpinner;
    private JTextField        secondaryNameField;
    private JCheckBox         bankBox;
    private JSpinner          breakIntervalSpinner;
    private JSpinner          breakDurationSpinner;

    public HerbloreConfigDialog(JComponent parent)
    {
        super(parent);
    }

    @Override
    public String getScriptName() { return "Axiom Herblore"; }

    @Override
    protected JPanel buildContent()
    {
        JPanel root = new JPanel();
        root.setLayout(new javax.swing.BoxLayout(root, javax.swing.BoxLayout.Y_AXIS));
        root.setBackground(AxiomTheme.BG_PANEL);
        root.setBorder(new javax.swing.border.EmptyBorder(
            AxiomTheme.PAD_MD, AxiomTheme.PAD_LG, AxiomTheme.PAD_MD, AxiomTheme.PAD_LG));

        AxiomSectionPanel methodSection = new AxiomSectionPanel("METHOD");
        methodCombo = makeCombo(methodDisplayNames());
        methodSection.addRow("Mode", methodCombo);
        root.add(methodSection);
        root.add(javax.swing.Box.createRigidArea(new java.awt.Dimension(0, AxiomTheme.PAD_SM)));

        // ── Section: Herb Type ──────────────────────────────────────────────
        AxiomSectionPanel herbSection = new AxiomSectionPanel("HERB TYPE");

        herbCombo = makeCombo(herbDisplayNames());
        herbCombo.setSelectedItem(herbDisplayName(HerbType.GUAM));
        herbSection.addRow("Herb", herbCombo);

        root.add(herbSection);
        root.add(javax.swing.Box.createRigidArea(new java.awt.Dimension(0, AxiomTheme.PAD_SM)));

        AxiomSectionPanel secondarySection = new AxiomSectionPanel("SECONDARY");
        secondaryIdSpinner = makeSpinner(0, 50000, 0);
        secondarySection.addRow("Secondary item ID", secondaryIdSpinner);
        secondaryNameField = makeTextField("Optional label for finished-potion mode");
        secondarySection.addRow("Secondary label", secondaryNameField);
        root.add(secondarySection);
        root.add(javax.swing.Box.createRigidArea(new java.awt.Dimension(0, AxiomTheme.PAD_SM)));

        // ── Section: Banking ──────────────────────────────────────────────
        AxiomSectionPanel bankSection = new AxiomSectionPanel("BANKING");

        bankBox = makeCheckBox("Bank for more herbs and vials when inventory is empty", false);
        bankSection.addCheckRow("", bankBox);

        root.add(bankSection);
        root.add(javax.swing.Box.createRigidArea(new java.awt.Dimension(0, AxiomTheme.PAD_SM)));

        // ── Section: Antiban ──────────────────────────────────────────────
        AxiomSectionPanel antibanSection = new AxiomSectionPanel("ANTIBAN / BREAKS");

        breakIntervalSpinner = makeSpinner(10, 240, 60);
        antibanSection.addRow("Break every (min)", breakIntervalSpinner);

        breakDurationSpinner = makeSpinner(1, 30, 5);
        antibanSection.addRow("Break length (min)", breakDurationSpinner);

        root.add(antibanSection);
        root.add(javax.swing.Box.createRigidArea(new java.awt.Dimension(0, AxiomTheme.PAD_SM)));

        return root;
    }

    @Override
    public HerbloreSettings getSettings()
    {
        Method   method             = methodFromIndex(methodCombo.getSelectedIndex());
        HerbType herbType          = herbTypeFromIndex(herbCombo.getSelectedIndex());
        int      secondaryItemId   = (Integer) secondaryIdSpinner.getValue();
        String   secondaryItemName = secondaryNameField.getText().trim();
        boolean  bankForIngredients = bankBox.isSelected();
        int      breakInterval      = (Integer) breakIntervalSpinner.getValue();
        int      breakDuration      = (Integer) breakDurationSpinner.getValue();

        return new HerbloreSettings(
            method, herbType, secondaryItemId, secondaryItemName,
            bankForIngredients, breakInterval, breakDuration);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String[] herbDisplayNames()
    {
        HerbType[] types = HerbType.values();
        String[]   names = new String[types.length];
        for (int i = 0; i < types.length; i++) names[i] = herbDisplayName(types[i]);
        return names;
    }

    private static String herbDisplayName(HerbType type)
    {
        return type.herbName + " (Lv. " + type.levelRequired + ")";
    }

    private static HerbType herbTypeFromIndex(int idx)
    {
        HerbType[] types = HerbType.values();
        if (idx >= 0 && idx < types.length) return types[idx];
        return HerbType.GUAM;
    }

    private static String[] methodDisplayNames()
    {
        Method[] methods = Method.values();
        String[] names = new String[methods.length];
        for (int i = 0; i < methods.length; i++) names[i] = methods[i].displayName;
        return names;
    }

    private static Method methodFromIndex(int idx)
    {
        Method[] methods = Method.values();
        if (idx >= 0 && idx < methods.length) return methods[idx];
        return Method.UNFINISHED;
    }
}
