package com.axiom.plugin.ui;

import com.axiom.scripts.crafting.CraftingSettings;
import com.axiom.scripts.crafting.CraftingSettings.CraftingMethod;
import com.axiom.scripts.crafting.CraftingSettings.GemType;
import com.axiom.scripts.crafting.CraftingSettings.LeatherType;

import javax.swing.Box;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;

/**
 * Configuration dialog for the Crafting script.
 *
 * A CardLayout swaps the gem / leather section when the method combo changes.
 */
public class CraftingConfigDialog extends ScriptConfigDialog<CraftingSettings>
{
    // ── Controls ──────────────────────────────────────────────────────────────
    private JComboBox<String> methodCombo;
    private JComboBox<String> gemCombo;
    private JComboBox<String> leatherCombo;
    private JPanel            itemCardPanel;
    private JCheckBox         bankBox;
    private JSpinner          breakIntervalSpinner;
    private JSpinner          breakDurationSpinner;

    public CraftingConfigDialog(JComponent parent)
    {
        super(parent);
    }

    @Override
    public String getScriptName() { return "Axiom Crafting"; }

    @Override
    protected JPanel buildContent()
    {
        JPanel root = new JPanel();
        root.setLayout(new javax.swing.BoxLayout(root, javax.swing.BoxLayout.Y_AXIS));
        root.setBackground(AxiomTheme.BG_PANEL);
        root.setBorder(new javax.swing.border.EmptyBorder(
            AxiomTheme.PAD_MD, AxiomTheme.PAD_LG, AxiomTheme.PAD_MD, AxiomTheme.PAD_LG));

        // ── Section: Method ──────────────────────────────────────────────────
        AxiomSectionPanel methodSection = new AxiomSectionPanel("METHOD");
        methodCombo = makeCombo(methodDisplayNames());
        methodSection.addRow("Method", methodCombo);
        root.add(methodSection);
        root.add(Box.createRigidArea(new Dimension(0, AxiomTheme.PAD_SM)));

        // ── Dynamic item section (CardLayout) ────────────────────────────────
        itemCardPanel = new JPanel(new CardLayout());
        itemCardPanel.setBackground(AxiomTheme.BG_PANEL);
        itemCardPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        AxiomSectionPanel gemSection = new AxiomSectionPanel("GEM TYPE");
        gemCombo = makeCombo(gemDisplayNames());
        gemCombo.setSelectedItem(gemDisplayName(GemType.SAPPHIRE));
        gemSection.addRow("Gem type", gemCombo);
        itemCardPanel.add(gemSection, "gem");

        AxiomSectionPanel leatherSection = new AxiomSectionPanel("LEATHER TYPE");
        leatherCombo = makeCombo(leatherDisplayNames());
        leatherSection.addRow("Item type", leatherCombo);
        itemCardPanel.add(leatherSection, "leather");

        root.add(itemCardPanel);
        root.add(Box.createRigidArea(new Dimension(0, AxiomTheme.PAD_SM)));

        // Wire method combo → card switch
        methodCombo.addActionListener(e ->
        {
            CardLayout cl = (CardLayout) itemCardPanel.getLayout();
            switch (methodFromIndex(methodCombo.getSelectedIndex()))
            {
                case GEM_CUTTING: cl.show(itemCardPanel, "gem");     break;
                case LEATHER:     cl.show(itemCardPanel, "leather"); break;
            }
        });

        // ── Section: Banking ──────────────────────────────────────────────────
        AxiomSectionPanel bankSection = new AxiomSectionPanel("BANKING");
        bankBox = makeCheckBox("Bank for more materials when inventory is empty", false);
        bankSection.addCheckRow("", bankBox);
        root.add(bankSection);
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
    public CraftingSettings getSettings()
    {
        return new CraftingSettings(
            methodFromIndex(methodCombo.getSelectedIndex()),
            gemTypeFromIndex(gemCombo.getSelectedIndex()),
            leatherTypeFromIndex(leatherCombo.getSelectedIndex()),
            bankBox.isSelected(),
            (Integer) breakIntervalSpinner.getValue(),
            (Integer) breakDurationSpinner.getValue()
        );
    }

    // ── Display name builders ─────────────────────────────────────────────────

    private static String[] methodDisplayNames()
    {
        CraftingMethod[] ms = CraftingMethod.values();
        String[] names = new String[ms.length];
        for (int i = 0; i < ms.length; i++) names[i] = ms[i].displayName;
        return names;
    }

    private static String[] gemDisplayNames()
    {
        GemType[] types = GemType.values();
        String[] names = new String[types.length];
        for (int i = 0; i < types.length; i++) names[i] = gemDisplayName(types[i]);
        return names;
    }

    private static String gemDisplayName(GemType t)
    {
        return t.uncutName + " (Lv. " + t.levelRequired + ")";
    }

    private static String[] leatherDisplayNames()
    {
        LeatherType[] types = LeatherType.values();
        String[] names = new String[types.length];
        for (int i = 0; i < types.length; i++)
            names[i] = types[i].productName + " (Lv. " + types[i].levelRequired + ")";
        return names;
    }

    // ── Index → enum ─────────────────────────────────────────────────────────

    private static CraftingMethod methodFromIndex(int idx)
    {
        CraftingMethod[] ms = CraftingMethod.values();
        return (idx >= 0 && idx < ms.length) ? ms[idx] : CraftingMethod.GEM_CUTTING;
    }

    private static GemType gemTypeFromIndex(int idx)
    {
        GemType[] types = GemType.values();
        return (idx >= 0 && idx < types.length) ? types[idx] : GemType.SAPPHIRE;
    }

    private static LeatherType leatherTypeFromIndex(int idx)
    {
        LeatherType[] types = LeatherType.values();
        return (idx >= 0 && idx < types.length) ? types[idx] : LeatherType.LEATHER_GLOVES;
    }
}
