package com.botengine.osrs.scripts.thieving;

import com.botengine.osrs.ui.AxiomSectionPanel;
import com.botengine.osrs.ui.AxiomTheme;
import com.botengine.osrs.ui.ScriptConfigDialog;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Dimension;

public class ThievingConfigDialog extends ScriptConfigDialog<ThievingSettings>
{
    private static final int[] STALL_IDS  = { 11730, 11731, 11732, 11734, 11733, 11736 };
    private static final int[] NPC_IDS    = { 1, 2, 7, 2234, 2235, 25379 };

    private JComboBox<String> cbMode;
    private JComboBox<String> cbStall;
    private JComboBox<String> cbNpc;
    private JSpinner          spEatPct;
    private JTextField        tfFoodId;
    private JCheckBox         cbBankFull;

    // Rows that toggle with mode — built manually so we can store references
    private JPanel stallRow;
    private JPanel npcRow;

    public ThievingConfigDialog(JComponent parent)
    {
        super(parent);
    }

    @Override
    public String getScriptName() { return "Thieving"; }

    @Override
    protected JPanel buildContent()
    {
        JPanel root = new JPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setOpaque(false);

        cbMode  = makeCombo("Stall", "Pickpocket");
        cbStall = makeCombo("Bakery stall", "Silk stall", "Fur stall",
                            "Silver stall", "Spice stall", "Gem stall");
        cbNpc   = makeCombo("Man (1)", "Woman (2)", "Farmer (7)",
                            "HAM Member M (2234)", "HAM Member F (2235)",
                            "Ardy Knight (25379)");
        spEatPct   = makeSpinner(1, 99, 50);
        tfFoodId   = makeTextField("1993");
        cbBankFull = makeCheckBox("Bank when full", false);

        // Build stall/npc rows manually to retain references for visibility toggling
        stallRow = buildLabelRow("Stall", cbStall);
        npcRow   = buildLabelRow("Target NPC", cbNpc);

        AxiomSectionPanel modeSection = new AxiomSectionPanel("MODE");
        modeSection.addRow("Mode", cbMode);
        modeSection.addRow(stallRow);
        modeSection.addRow(npcRow);
        root.add(modeSection);

        AxiomSectionPanel food = new AxiomSectionPanel("FOOD & HEALING");
        food.addRow("Eat at HP%", spEatPct);
        food.addRow("Food item ID", tfFoodId);
        root.add(food);

        AxiomSectionPanel behaviour = new AxiomSectionPanel("BEHAVIOUR");
        behaviour.addCheckRow("Bank when full", cbBankFull);
        root.add(behaviour);

        // Toggle stall/NPC rows based on mode selection
        updateModeVisibility();
        cbMode.addActionListener(e -> updateModeVisibility());

        return root;
    }

    /** Replicates the layout used by AxiomSectionPanel.addRow(String, Component). */
    private JPanel buildLabelRow(String labelText, JComponent control)
    {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setBackground(AxiomTheme.BG_PANEL);
        row.setBorder(new EmptyBorder(2, 0, 2, 0));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));

        JLabel label = new JLabel(labelText);
        label.setFont(AxiomTheme.fontSmall());
        label.setForeground(AxiomTheme.TEXT_DIM);
        label.setPreferredSize(new Dimension(115, 22));

        row.add(label, BorderLayout.WEST);
        row.add(control, BorderLayout.CENTER);
        return row;
    }

    private void updateModeVisibility()
    {
        boolean isStall = cbMode.getSelectedIndex() == 0;
        if (stallRow != null) stallRow.setVisible(isStall);
        if (npcRow   != null) npcRow.setVisible(!isStall);
    }

    @Override
    public ThievingSettings getSettings()
    {
        ThievingSettings s = new ThievingSettings();
        s.mode            = (String) cbMode.getSelectedItem();
        s.stallId         = STALL_IDS[cbStall.getSelectedIndex()];
        s.targetNpcId     = NPC_IDS[cbNpc.getSelectedIndex()];
        s.eatThresholdPct = (int) spEatPct.getValue();
        try { s.foodItemId = Integer.parseInt(tfFoodId.getText().trim()); }
        catch (NumberFormatException ignored) {}
        s.bankWhenFull    = cbBankFull.isSelected();
        return s;
    }
}
