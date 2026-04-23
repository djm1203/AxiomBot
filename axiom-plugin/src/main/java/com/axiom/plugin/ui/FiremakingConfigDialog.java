package com.axiom.plugin.ui;

import com.axiom.scripts.firemaking.FiremakingSettings;
import com.axiom.scripts.firemaking.FiremakingSettings.FiremakingMode;
import com.axiom.scripts.firemaking.FiremakingSettings.LaneDirection;
import com.axiom.scripts.firemaking.FiremakingSettings.LogType;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSpinner;

/**
 * Configuration dialog for the Firemaking script.
 *
 * Convention: FiremakingScript → FiremakingConfigDialog
 * Discovered at runtime via Class.forName() in AxiomPanel.openConfigDialog().
 */
public class FiremakingConfigDialog extends ScriptConfigDialog<FiremakingSettings>
{
    // ── Controls ──────────────────────────────────────────────────────────────
    private JComboBox<String> logCombo;
    private JComboBox<String> modeCombo;
    private JComboBox<String> laneCombo;
    private JCheckBox         bankBox;
    private JSpinner          breakIntervalSpinner;
    private JSpinner          breakDurationSpinner;

    public FiremakingConfigDialog(JComponent parent)
    {
        super(parent);
    }

    @Override
    public String getScriptName() { return "Axiom Firemaking"; }

    @Override
    protected JPanel buildContent()
    {
        JPanel root = new JPanel();
        root.setLayout(new javax.swing.BoxLayout(root, javax.swing.BoxLayout.Y_AXIS));
        root.setBackground(AxiomTheme.BG_PANEL);
        root.setBorder(new javax.swing.border.EmptyBorder(
            AxiomTheme.PAD_MD, AxiomTheme.PAD_LG, AxiomTheme.PAD_MD, AxiomTheme.PAD_LG));

        AxiomSectionPanel modeSection = new AxiomSectionPanel("MODE");
        modeCombo = makeCombo(modeDisplayNames());
        laneCombo = makeCombo(laneDisplayNames());
        modeSection.addRow("Mode", modeCombo);
        modeSection.addRow("Lane direction", laneCombo);
        root.add(modeSection);
        root.add(javax.swing.Box.createRigidArea(new java.awt.Dimension(0, AxiomTheme.PAD_SM)));

        // ── Section: Log Type ──────────────────────────────────────────────
        AxiomSectionPanel logSection = new AxiomSectionPanel("LOG TYPE");

        logCombo = makeCombo(logDisplayNames());
        logCombo.setSelectedItem(logDisplayName(LogType.NORMAL));
        logSection.addRow("Log type", logCombo);

        root.add(logSection);
        root.add(javax.swing.Box.createRigidArea(new java.awt.Dimension(0, AxiomTheme.PAD_SM)));

        // ── Section: Banking ──────────────────────────────────────────────
        AxiomSectionPanel bankSection = new AxiomSectionPanel("BANKING");

        bankBox = makeCheckBox("Bank for more logs when inventory is empty", false);
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
    public FiremakingSettings getSettings()
    {
        FiremakingMode mode = modeFromIndex(modeCombo.getSelectedIndex());
        LaneDirection laneDirection = laneDirectionFromIndex(laneCombo.getSelectedIndex());
        LogType logType      = logTypeFromIndex(logCombo.getSelectedIndex());
        boolean bankForLogs  = bankBox.isSelected();
        int breakInterval    = (Integer) breakIntervalSpinner.getValue();
        int breakDuration    = (Integer) breakDurationSpinner.getValue();

        return new FiremakingSettings(mode, laneDirection, logType, bankForLogs, breakInterval, breakDuration);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String[] modeDisplayNames()
    {
        FiremakingMode[] modes = FiremakingMode.values();
        String[] names = new String[modes.length];
        for (int i = 0; i < modes.length; i++) names[i] = modes[i].displayName;
        return names;
    }

    private static String[] laneDisplayNames()
    {
        LaneDirection[] directions = LaneDirection.values();
        String[] names = new String[directions.length];
        for (int i = 0; i < directions.length; i++) names[i] = directions[i].displayName;
        return names;
    }

    private static String[] logDisplayNames()
    {
        LogType[] types = LogType.values();
        String[]  names = new String[types.length];
        for (int i = 0; i < types.length; i++) names[i] = logDisplayName(types[i]);
        return names;
    }

    private static String logDisplayName(LogType type)
    {
        switch (type)
        {
            case NORMAL:    return "Logs (Normal)";
            case OAK:       return "Oak Logs";
            case WILLOW:    return "Willow Logs";
            case TEAK:      return "Teak Logs";
            case MAPLE:     return "Maple Logs";
            case MAHOGANY:  return "Mahogany Logs";
            case YEW:       return "Yew Logs";
            case MAGIC:     return "Magic Logs";
            case REDWOOD:   return "Redwood Logs";
            default:        return type.name();
        }
    }

    private static FiremakingMode modeFromIndex(int idx)
    {
        FiremakingMode[] modes = FiremakingMode.values();
        return (idx >= 0 && idx < modes.length) ? modes[idx] : FiremakingMode.STATIONARY;
    }

    private static LaneDirection laneDirectionFromIndex(int idx)
    {
        LaneDirection[] directions = LaneDirection.values();
        return (idx >= 0 && idx < directions.length) ? directions[idx] : LaneDirection.WEST;
    }

    private static LogType logTypeFromIndex(int idx)
    {
        LogType[] types = LogType.values();
        if (idx >= 0 && idx < types.length) return types[idx];
        return LogType.NORMAL;
    }
}
