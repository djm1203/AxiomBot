package com.axiom.plugin.ui;

import com.axiom.scripts.mining.MiningSettings;
import com.axiom.scripts.mining.MiningSettings.MiningAction;
import com.axiom.scripts.mining.MiningSettings.MiningMethod;
import com.axiom.scripts.mining.MiningSettings.OreType;

import javax.swing.Box;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import java.awt.Dimension;

/**
 * Configuration dialog for the Mining script.
 */
public class MiningConfigDialog extends ScriptConfigDialog<MiningSettings>
{
    // ── Controls ──────────────────────────────────────────────────────────────
    private JComboBox<String> methodCombo;
    private JComboBox<String> oreCombo;
    private JComboBox<String> actionCombo;
    private JCheckBox         powerMineBox;
    private JCheckBox         coalBagBox;
    private JSpinner          breakIntervalSpinner;
    private JSpinner          breakDurationSpinner;

    public MiningConfigDialog(JComponent parent)
    {
        super(parent);
    }

    @Override
    public String getScriptName() { return "Axiom Mining"; }

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
        methodSection.addRow("Mining mode", methodCombo);
        root.add(methodSection);
        root.add(Box.createRigidArea(new Dimension(0, AxiomTheme.PAD_SM)));

        // ── Section: Ore ─────────────────────────────────────────────────────
        AxiomSectionPanel oreSection = new AxiomSectionPanel("ORE SELECTION");
        oreCombo = makeCombo(oreDisplayNames());
        oreCombo.setSelectedItem(oreDisplayName(OreType.IRON));
        oreSection.addRow("Ore type", oreCombo);
        root.add(oreSection);
        root.add(Box.createRigidArea(new Dimension(0, AxiomTheme.PAD_SM)));

        // ── Section: Action ───────────────────────────────────────────────────
        AxiomSectionPanel actionSection = new AxiomSectionPanel("WHEN INVENTORY FULL");
        actionCombo = makeCombo(actionDisplayNames());
        actionSection.addRow("Action", actionCombo);
        powerMineBox = makeCheckBox("Power-mine (drop ore without waiting for full inventory)", false);
        actionSection.addCheckRow("", powerMineBox);
        coalBagBox = makeCheckBox("Use coal bag when mining coal", false);
        actionSection.addCheckRow("", coalBagBox);
        root.add(actionSection);
        root.add(Box.createRigidArea(new Dimension(0, AxiomTheme.PAD_SM)));

        // ── Section: Antiban ──────────────────────────────────────────────────
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
    public MiningSettings getSettings()
    {
        return new MiningSettings(
            methodFromIndex(methodCombo.getSelectedIndex()),
            oreTypeFromIndex(oreCombo.getSelectedIndex()),
            actionFromIndex(actionCombo.getSelectedIndex()),
            powerMineBox.isSelected(),
            coalBagBox.isSelected(),
            (Integer) breakIntervalSpinner.getValue(),
            (Integer) breakDurationSpinner.getValue()
        );
    }

    // ── Display name builders ─────────────────────────────────────────────────

    private static String[] oreDisplayNames()
    {
        OreType[] types = OreType.values();
        String[] names = new String[types.length];
        for (int i = 0; i < types.length; i++) names[i] = oreDisplayName(types[i]);
        return names;
    }

    private static String oreDisplayName(OreType t)
    {
        return t.oreName + " (Lv. " + t.levelRequired + ")";
    }

    private static String[] methodDisplayNames()
    {
        MiningMethod[] methods = MiningMethod.values();
        String[] names = new String[methods.length];
        for (int i = 0; i < methods.length; i++) names[i] = methods[i].displayName;
        return names;
    }

    private static String[] actionDisplayNames()
    {
        MiningAction[] actions = MiningAction.values();
        String[] names = new String[actions.length];
        for (int i = 0; i < actions.length; i++) names[i] = actions[i].displayName;
        return names;
    }

    // ── Index → enum ─────────────────────────────────────────────────────────

    private static OreType oreTypeFromIndex(int idx)
    {
        OreType[] types = OreType.values();
        return (idx >= 0 && idx < types.length) ? types[idx] : OreType.IRON;
    }

    private static MiningMethod methodFromIndex(int idx)
    {
        MiningMethod[] methods = MiningMethod.values();
        return (idx >= 0 && idx < methods.length) ? methods[idx] : MiningMethod.STANDARD;
    }

    private static MiningAction actionFromIndex(int idx)
    {
        MiningAction[] actions = MiningAction.values();
        return (idx >= 0 && idx < actions.length) ? actions[idx] : MiningAction.BANK;
    }
}
