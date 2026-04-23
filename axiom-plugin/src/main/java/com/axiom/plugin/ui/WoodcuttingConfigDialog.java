package com.axiom.plugin.ui;

import com.axiom.api.util.Progression;
import com.axiom.scripts.woodcutting.WoodcuttingSettings;
import com.axiom.scripts.woodcutting.WoodcuttingSettings.BankAction;
import com.axiom.scripts.woodcutting.WoodcuttingSettings.LocationPreset;
import com.axiom.scripts.woodcutting.WoodcuttingSettings.TreeType;

import javax.swing.Box;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import java.awt.Dimension;

/**
 * Configuration dialog for the Woodcutting script.
 *
 * Lives in axiom-plugin (not axiom-woodcutting) to avoid a circular
 * dependency — axiom-plugin bundles axiom-woodcutting via the shade plugin,
 * so plugin-side code can reference WoodcuttingSettings freely.
 *
 * Opened by AxiomPanel.openConfigDialog() via an instanceof check.
 */
public class WoodcuttingConfigDialog extends ScriptConfigDialog<WoodcuttingSettings>
{
    // ── Controls ──────────────────────────────────────────────────────────────
    private JComboBox<String> treeCombo;
    private JComboBox<String> locationCombo;
    private JCheckBox         autoModeBox;
    private JTextField        progressionField;
    private JComboBox<String> bankCombo;
    private JCheckBox         powerChopBox;
    private JCheckBox         forestryBox;
    private JSpinner          breakIntervalSpinner;
    private JSpinner          breakDurationSpinner;

    public WoodcuttingConfigDialog(JComponent parent)
    {
        super(parent);
    }

    @Override
    public String getScriptName() { return "Axiom Woodcutting"; }

    @Override
    protected JPanel buildContent()
    {
        JPanel root = new JPanel();
        root.setLayout(new javax.swing.BoxLayout(root, javax.swing.BoxLayout.Y_AXIS));
        root.setBackground(AxiomTheme.BG_PANEL);
        root.setBorder(new javax.swing.border.EmptyBorder(
            AxiomTheme.PAD_MD, AxiomTheme.PAD_LG, AxiomTheme.PAD_MD, AxiomTheme.PAD_LG));

        // ── Section: Tree ──────────────────────────────────────────────────
        AxiomSectionPanel treeSection = new AxiomSectionPanel("TREE SELECTION");

        autoModeBox = makeCheckBox("Auto-select tree by Woodcutting level", false);
        treeSection.addCheckRow("", autoModeBox);

        treeCombo = makeCombo(treeNames());
        treeCombo.setSelectedItem("Oak");
        treeSection.addRow("Tree type", treeCombo);

        progressionField = new JTextField(Progression.DEFAULT_WOODCUTTING);
        progressionField.setFont(progressionField.getFont().deriveFont(11f));
        progressionField.setBackground(AxiomTheme.BG_INPUT);
        progressionField.setForeground(AxiomTheme.TEXT);
        progressionField.setBorder(javax.swing.BorderFactory.createLineBorder(AxiomTheme.BORDER));
        progressionField.setEnabled(false); // enabled when auto-mode is ticked
        treeSection.addRow("Progression", progressionField);

        // Toggle tree-combo / progression-field based on auto-mode checkbox
        autoModeBox.addActionListener(e -> {
            boolean auto = autoModeBox.isSelected();
            treeCombo.setEnabled(!auto);
            progressionField.setEnabled(auto);
        });

        root.add(treeSection);
        root.add(Box.createRigidArea(new Dimension(0, AxiomTheme.PAD_SM)));

        AxiomSectionPanel locationSection = new AxiomSectionPanel("LOCATION");
        locationCombo = makeCombo(locationDisplayNames());
        locationSection.addRow("Loop preset", locationCombo);
        root.add(locationSection);
        root.add(Box.createRigidArea(new Dimension(0, AxiomTheme.PAD_SM)));

        // ── Section: Inventory ─────────────────────────────────────────────
        AxiomSectionPanel invSection = new AxiomSectionPanel("WHEN INVENTORY FULL");

        bankCombo = makeCombo("Drop Logs (Power-chop)", "Bank Logs");
        invSection.addRow("Action", bankCombo);

        powerChopBox = makeCheckBox("Power-chop (drop each log immediately)", false);
        invSection.addCheckRow("", powerChopBox);

        root.add(invSection);
        root.add(Box.createRigidArea(new Dimension(0, AxiomTheme.PAD_SM)));

        AxiomSectionPanel forestrySection = new AxiomSectionPanel("FORESTRY");
        forestryBox = makeCheckBox("Join nearby Forestry events when idle", false);
        forestrySection.addCheckRow("", forestryBox);
        root.add(forestrySection);
        root.add(Box.createRigidArea(new Dimension(0, AxiomTheme.PAD_SM)));

        // ── Section: Antiban ──────────────────────────────────────────────
        AxiomSectionPanel antibanSection = new AxiomSectionPanel("ANTIBAN / BREAKS");

        breakIntervalSpinner = makeSpinner(10, 240, 60);
        antibanSection.addRow("Break every (min)", breakIntervalSpinner);

        breakDurationSpinner = makeSpinner(1, 30, 5);
        antibanSection.addRow("Break length (min)", breakDurationSpinner);

        root.add(antibanSection);
        root.add(Box.createRigidArea(new Dimension(0, AxiomTheme.PAD_SM)));

        return root;
    }

    @Override
    public WoodcuttingSettings getSettings()
    {
        TreeType treeType = treeTypeFromIndex(treeCombo.getSelectedIndex());

        boolean powerChop = powerChopBox.isSelected()
            || bankCombo.getSelectedIndex() == 0;
        BankAction bankAction = powerChop ? BankAction.DROP_LOGS : BankAction.BANK;

        boolean autoMode         = autoModeBox.isSelected();
        String  progressionStr   = progressionField.getText().trim();
        if (progressionStr.isEmpty()) progressionStr = Progression.DEFAULT_WOODCUTTING;

        int breakInterval = (Integer) breakIntervalSpinner.getValue();
        int breakDuration = (Integer) breakDurationSpinner.getValue();

        return new WoodcuttingSettings(
            treeType, bankAction, locationPresetFromIndex(locationCombo.getSelectedIndex()), powerChop,
            forestryBox.isSelected(),
            autoMode, progressionStr,
            breakInterval, breakDuration);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String[] treeNames()
    {
        TreeType[] types = TreeType.values();
        String[] names = new String[types.length];
        for (int i = 0; i < types.length; i++)
        {
            // Capitalise nicely: "OAK" → "Oak"
            String raw = types[i].name();
            names[i] = raw.charAt(0) + raw.substring(1).toLowerCase();
        }
        return names;
    }

    private static TreeType treeTypeFromIndex(int idx)
    {
        TreeType[] types = TreeType.values();
        if (idx >= 0 && idx < types.length) return types[idx];
        return TreeType.OAK;
    }

    private static String[] locationDisplayNames()
    {
        LocationPreset[] presets = LocationPreset.values();
        String[] names = new String[presets.length];
        for (int i = 0; i < presets.length; i++)
        {
            names[i] = presets[i].displayName;
        }
        return names;
    }

    private static LocationPreset locationPresetFromIndex(int idx)
    {
        LocationPreset[] presets = LocationPreset.values();
        return (idx >= 0 && idx < presets.length) ? presets[idx] : LocationPreset.CUSTOM_START;
    }
}
