package com.axiom.plugin.ui;

import com.axiom.scripts.alchemy.AlchemySettings;
import com.axiom.scripts.alchemy.AlchemySettings.AlchemySpell;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;

/**
 * Configuration dialog for the Alchemy script.
 *
 * Convention: AlchemyScript → AlchemyConfigDialog
 * Discovered at runtime via Class.forName() in AxiomPanel.openConfigDialog().
 */
public class AlchemyConfigDialog extends ScriptConfigDialog<AlchemySettings>
{
    // ── Controls ──────────────────────────────────────────────────────────────
    private JComboBox<String> spellCombo;
    private JSpinner          itemIdSpinner;
    private JTextField        itemNameField;
    private JCheckBox         bankBox;
    private JSpinner          breakIntervalSpinner;
    private JSpinner          breakDurationSpinner;

    public AlchemyConfigDialog(JComponent parent)
    {
        super(parent);
    }

    @Override
    public String getScriptName() { return "Axiom Alchemy"; }

    @Override
    protected JPanel buildContent()
    {
        JPanel root = new JPanel();
        root.setLayout(new javax.swing.BoxLayout(root, javax.swing.BoxLayout.Y_AXIS));
        root.setBackground(AxiomTheme.BG_PANEL);
        root.setBorder(new javax.swing.border.EmptyBorder(
            AxiomTheme.PAD_MD, AxiomTheme.PAD_LG, AxiomTheme.PAD_MD, AxiomTheme.PAD_LG));

        // ── Section: Spell ────────────────────────────────────────────────
        AxiomSectionPanel spellSection = new AxiomSectionPanel("SPELL");

        spellCombo = makeCombo("High Level Alchemy (lvl 55)", "Low Level Alchemy (lvl 21)");
        spellSection.addRow("Spell", spellCombo);

        root.add(spellSection);
        root.add(javax.swing.Box.createRigidArea(new java.awt.Dimension(0, AxiomTheme.PAD_SM)));

        // ── Section: Target Item ──────────────────────────────────────────
        AxiomSectionPanel itemSection = new AxiomSectionPanel("TARGET ITEM");

        itemIdSpinner = makeSpinner(1, 50000, 1);
        itemSection.addRow("Item ID", itemIdSpinner);

        itemNameField = makeTextField("e.g. Steel platelegs");
        itemSection.addRow("Label (optional)", itemNameField);

        root.add(itemSection);
        root.add(javax.swing.Box.createRigidArea(new java.awt.Dimension(0, AxiomTheme.PAD_SM)));

        // ── Section: Banking ──────────────────────────────────────────────
        AxiomSectionPanel bankSection = new AxiomSectionPanel("BANKING");

        bankBox = makeCheckBox("Bank for more items when inventory runs out", false);
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
    public AlchemySettings getSettings()
    {
        AlchemySpell spell         = spellCombo.getSelectedIndex() == 0
                                     ? AlchemySpell.HIGH_ALCHEMY : AlchemySpell.LOW_ALCHEMY;
        int          targetItemId  = (Integer) itemIdSpinner.getValue();
        String       targetName    = itemNameField.getText().trim();
        boolean      bankForItems  = bankBox.isSelected();
        int          breakInterval = (Integer) breakIntervalSpinner.getValue();
        int          breakDuration = (Integer) breakDurationSpinner.getValue();

        return new AlchemySettings(spell, targetItemId, targetName,
                                   bankForItems, breakInterval, breakDuration);
    }
}
