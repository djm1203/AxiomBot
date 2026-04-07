package com.botengine.osrs.scripts.alchemy;

import com.botengine.osrs.ui.AxiomSectionPanel;
import com.botengine.osrs.ui.AxiomTheme;
import com.botengine.osrs.ui.ScriptConfigDialog;

import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.border.EmptyBorder;

/** Config dialog for AlchemyScript. */
public class AlchemyConfigDialog extends ScriptConfigDialog<AlchemySettings>
{
    private JSpinner  spItemId;
    private JCheckBox cbBankingMode;

    public AlchemyConfigDialog(JComponent parent)
    {
        super(parent);
        AlchemySettings saved = AlchemySettings.load(AlchemySettings.class, getScriptName());
        applySettings(saved);
    }

    @Override public String getScriptName() { return "Alchemy"; }

    @Override
    protected JPanel buildContent()
    {
        JPanel root = new JPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBackground(AxiomTheme.BG_PANEL);
        root.setBorder(new EmptyBorder(8, 8, 8, 8));

        AxiomSectionPanel item = new AxiomSectionPanel("ALCHEMY ITEM");
        spItemId = makeSpinner(0, 99999, 0);
        item.addRow("Item ID (0 = auto-detect)", spItemId);
        root.add(item);

        AxiomSectionPanel behaviour = new AxiomSectionPanel("BEHAVIOUR");
        cbBankingMode = makeCheckBox("Banking mode (restock runes/items)", false);
        behaviour.addCheckRow("", cbBankingMode);
        root.add(behaviour);

        return root;
    }

    @Override
    public AlchemySettings getSettings()
    {
        AlchemySettings s = new AlchemySettings();
        s.alchemyItemId = (int) spItemId.getValue();
        s.bankingMode   = cbBankingMode.isSelected();
        settings = s;
        return s;
    }

    private void applySettings(AlchemySettings s)
    {
        if (s == null || spItemId == null) return;
        spItemId.setValue(s.alchemyItemId);
        cbBankingMode.setSelected(s.bankingMode);
    }
}
