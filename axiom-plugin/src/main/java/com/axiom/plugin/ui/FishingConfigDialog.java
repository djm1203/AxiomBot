package com.axiom.plugin.ui;

import com.axiom.scripts.fishing.FishingSettings;
import com.axiom.scripts.fishing.FishingSettings.BankAction;
import com.axiom.scripts.fishing.FishingSettings.LocationPreset;
import com.axiom.scripts.fishing.FishingSettings.SpotType;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSpinner;

/**
 * Configuration dialog for the Fishing script.
 *
 * Lives in axiom-plugin (not axiom-fishing) to avoid a circular dependency —
 * axiom-plugin bundles axiom-fishing via the shade plugin, so plugin-side code
 * can reference FishingSettings freely, but axiom-fishing cannot reference
 * axiom-plugin (ScriptConfigDialog lives there).
 *
 * Opened by AxiomPanel.openConfigDialog() via an instanceof check.
 */
public class FishingConfigDialog extends ScriptConfigDialog<FishingSettings>
{
    // ── Controls ──────────────────────────────────────────────────────────────
    private JComboBox<String> spotCombo;
    private JComboBox<String> locationCombo;
    private JComboBox<String> bankCombo;
    private JCheckBox         powerFishBox;
    private JCheckBox         fishBarrelBox;
    private JSpinner          breakIntervalSpinner;
    private JSpinner          breakDurationSpinner;

    public FishingConfigDialog(JComponent parent)
    {
        super(parent);
    }

    @Override
    public String getScriptName() { return "Axiom Fishing"; }

    @Override
    protected JPanel buildContent()
    {
        JPanel root = new JPanel();
        root.setLayout(new javax.swing.BoxLayout(root, javax.swing.BoxLayout.Y_AXIS));
        root.setBackground(AxiomTheme.BG_PANEL);
        root.setBorder(new javax.swing.border.EmptyBorder(
            AxiomTheme.PAD_MD, AxiomTheme.PAD_LG, AxiomTheme.PAD_MD, AxiomTheme.PAD_LG));

        // ── Section: Fishing Spot ──────────────────────────────────────────
        AxiomSectionPanel spotSection = new AxiomSectionPanel("FISHING SPOT");

        spotCombo = makeCombo(spotDisplayNames());
        spotCombo.setSelectedItem(spotDisplayName(SpotType.SHRIMP_ANCHOVIES));
        spotSection.addRow("Spot type", spotCombo);

        root.add(spotSection);
        root.add(javax.swing.Box.createRigidArea(new java.awt.Dimension(0, AxiomTheme.PAD_SM)));

        AxiomSectionPanel locationSection = new AxiomSectionPanel("LOCATION");
        locationCombo = makeCombo(locationDisplayNames());
        locationSection.addRow("Spot preset", locationCombo);
        root.add(locationSection);
        root.add(javax.swing.Box.createRigidArea(new java.awt.Dimension(0, AxiomTheme.PAD_SM)));

        // ── Section: Inventory ─────────────────────────────────────────────
        AxiomSectionPanel invSection = new AxiomSectionPanel("WHEN INVENTORY FULL");

        bankCombo = makeCombo("Drop Fish (Power-fish)", "Bank Fish");
        invSection.addRow("Action", bankCombo);

        powerFishBox = makeCheckBox("Power-fish (drop each catch immediately)", false);
        invSection.addCheckRow("", powerFishBox);
        fishBarrelBox = makeCheckBox("Use fish barrel when present", false);
        invSection.addCheckRow("", fishBarrelBox);

        root.add(invSection);
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
    public FishingSettings getSettings()
    {
        SpotType spotType = spotTypeFromIndex(spotCombo.getSelectedIndex());

        boolean   powerFish  = powerFishBox.isSelected() || bankCombo.getSelectedIndex() == 0;
        BankAction bankAction = powerFish ? BankAction.DROP_FISH : BankAction.BANK;

        int breakInterval = (Integer) breakIntervalSpinner.getValue();
        int breakDuration = (Integer) breakDurationSpinner.getValue();

        return new FishingSettings(
            spotType,
            bankAction,
            locationPresetFromIndex(locationCombo.getSelectedIndex()),
            powerFish,
            fishBarrelBox.isSelected(),
            breakInterval,
            breakDuration
        );
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String[] spotDisplayNames()
    {
        SpotType[] types = SpotType.values();
        String[]   names = new String[types.length];
        for (int i = 0; i < types.length; i++) names[i] = spotDisplayName(types[i]);
        return names;
    }

    private static String spotDisplayName(SpotType type)
    {
        switch (type)
        {
            case SHRIMP_ANCHOVIES: return "Shrimp / Anchovies";
            case TROUT_SALMON:     return "Trout / Salmon";
            case LOBSTER:          return "Lobster";
            case SWORDFISH_TUNA:   return "Swordfish / Tuna";
            case SHARK:            return "Shark";
            case MONKFISH:         return "Monkfish";
            case BARBARIAN:        return "Barbarian (rod)";
            default:               return type.name();
        }
    }

    private static SpotType spotTypeFromIndex(int idx)
    {
        SpotType[] types = SpotType.values();
        if (idx >= 0 && idx < types.length) return types[idx];
        return SpotType.SHRIMP_ANCHOVIES;
    }

    private static String[] locationDisplayNames()
    {
        LocationPreset[] presets = LocationPreset.values();
        String[] names = new String[presets.length];
        for (int i = 0; i < presets.length; i++) names[i] = presets[i].displayName;
        return names;
    }

    private static LocationPreset locationPresetFromIndex(int idx)
    {
        LocationPreset[] presets = LocationPreset.values();
        return (idx >= 0 && idx < presets.length) ? presets[idx] : LocationPreset.CUSTOM_START;
    }
}
