package com.axiom.plugin.ui;

import com.axiom.scripts.thieving.ThievingSettings;
import com.axiom.scripts.thieving.ThievingSettings.ThievingMethod;
import com.axiom.scripts.thieving.ThievingSettings.StallType;
import com.axiom.scripts.thieving.ThievingSettings.NpcTarget;

import javax.swing.Box;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import java.awt.Dimension;
import java.awt.event.ActionListener;

/**
 * Configuration dialog for the Thieving script.
 *
 * Method combo (Stall / Pickpocket) drives which sub-combo is enabled.
 * Selecting "Steal from stall" enables the stall type combo and disables
 * the NPC target combo, and vice versa.
 */
public class ThievingConfigDialog extends ScriptConfigDialog<ThievingSettings>
{
    // ── Controls ──────────────────────────────────────────────────────────────
    private JComboBox<String> methodCombo;
    private JComboBox<String> stallCombo;
    private JComboBox<String> npcCombo;
    private JCheckBox         dropJunkBox;
    private JSpinner          breakIntervalSpinner;
    private JSpinner          breakDurationSpinner;

    public ThievingConfigDialog(JComponent parent)
    {
        super(parent);
    }

    @Override
    public String getScriptName() { return "Axiom Thieving"; }

    @Override
    protected JPanel buildContent()
    {
        JPanel root = new JPanel();
        root.setLayout(new javax.swing.BoxLayout(root, javax.swing.BoxLayout.Y_AXIS));
        root.setBackground(AxiomTheme.BG_PANEL);
        root.setBorder(new javax.swing.border.EmptyBorder(
            AxiomTheme.PAD_MD, AxiomTheme.PAD_LG, AxiomTheme.PAD_MD, AxiomTheme.PAD_LG));

        // ── Section: Method ───────────────────────────────────────────────────
        AxiomSectionPanel methodSection = new AxiomSectionPanel("METHOD");
        methodCombo = makeCombo(methodDisplayNames());
        methodSection.addRow("Method", methodCombo);
        root.add(methodSection);
        root.add(Box.createRigidArea(new Dimension(0, AxiomTheme.PAD_SM)));

        // ── Section: Stall ────────────────────────────────────────────────────
        AxiomSectionPanel stallSection = new AxiomSectionPanel("STALL TYPE");
        stallCombo = makeCombo(stallDisplayNames());
        stallCombo.setSelectedIndex(1); // default: Tea stall (index 1)
        stallSection.addRow("Stall", stallCombo);
        root.add(stallSection);
        root.add(Box.createRigidArea(new Dimension(0, AxiomTheme.PAD_SM)));

        // ── Section: NPC ──────────────────────────────────────────────────────
        AxiomSectionPanel npcSection = new AxiomSectionPanel("NPC TARGET");
        npcCombo = makeCombo(npcDisplayNames());
        npcSection.addRow("NPC", npcCombo);
        root.add(npcSection);
        root.add(Box.createRigidArea(new Dimension(0, AxiomTheme.PAD_SM)));

        // ── Section: Options ──────────────────────────────────────────────────
        AxiomSectionPanel optionsSection = new AxiomSectionPanel("OPTIONS");
        dropJunkBox = makeCheckBox("Drop items when inventory full (XP mode)", true);
        optionsSection.addCheckRow("", dropJunkBox);
        root.add(optionsSection);
        root.add(Box.createRigidArea(new Dimension(0, AxiomTheme.PAD_SM)));

        // ── Section: Antiban ──────────────────────────────────────────────────
        AxiomSectionPanel antibanSection = new AxiomSectionPanel("ANTIBAN / BREAKS");
        breakIntervalSpinner = makeSpinner(10, 240, 60);
        antibanSection.addRow("Break every (min)", breakIntervalSpinner);
        breakDurationSpinner = makeSpinner(1, 30, 5);
        antibanSection.addRow("Break length (min)", breakDurationSpinner);
        root.add(antibanSection);
        root.add(Box.createRigidArea(new Dimension(0, AxiomTheme.PAD_SM)));

        // Wire cascading: method change → enable/disable relevant sub-combo
        ActionListener cascadeListener = e -> refreshMethodUI();
        methodCombo.addActionListener(cascadeListener);
        refreshMethodUI(); // set initial state

        return root;
    }

    @Override
    public ThievingSettings getSettings()
    {
        return new ThievingSettings(
            ThievingMethod.values()[methodCombo.getSelectedIndex()],
            StallType.values()[stallCombo.getSelectedIndex()],
            NpcTarget.values()[npcCombo.getSelectedIndex()],
            dropJunkBox.isSelected(),
            new int[0],
            (Integer) breakIntervalSpinner.getValue(),
            (Integer) breakDurationSpinner.getValue()
        );
    }

    // ── Cascading helpers ─────────────────────────────────────────────────────

    private void refreshMethodUI()
    {
        boolean isStall = methodCombo.getSelectedIndex() == ThievingMethod.STALL.ordinal();
        stallCombo.setEnabled(isStall);
        npcCombo.setEnabled(!isStall);
    }

    // ── Display name builders ─────────────────────────────────────────────────

    private static String[] methodDisplayNames()
    {
        ThievingMethod[] methods = ThievingMethod.values();
        String[] names = new String[methods.length];
        for (int i = 0; i < methods.length; i++) names[i] = methods[i].displayName;
        return names;
    }

    private static String[] stallDisplayNames()
    {
        StallType[] stalls = StallType.values();
        String[] names = new String[stalls.length];
        for (int i = 0; i < stalls.length; i++)
        {
            names[i] = stalls[i].stallName + " (Lv. " + stalls[i].levelRequired + ")";
        }
        return names;
    }

    private static String[] npcDisplayNames()
    {
        NpcTarget[] targets = NpcTarget.values();
        String[] names = new String[targets.length];
        for (int i = 0; i < targets.length; i++)
        {
            names[i] = targets[i].npcName + " (Lv. " + targets[i].levelRequired + ")";
        }
        return names;
    }
}
