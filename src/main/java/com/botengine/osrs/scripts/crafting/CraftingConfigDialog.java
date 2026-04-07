package com.botengine.osrs.scripts.crafting;

import com.botengine.osrs.ui.AxiomSectionPanel;
import com.botengine.osrs.ui.AxiomTheme;
import com.botengine.osrs.ui.ScriptConfigDialog;

import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

/** Config dialog for CraftingScript. */
public class CraftingConfigDialog extends ScriptConfigDialog<CraftingSettings>
{
    private JCheckBox cbBankingMode;

    public CraftingConfigDialog(JComponent parent)
    {
        super(parent);
        CraftingSettings saved = CraftingSettings.load(CraftingSettings.class, getScriptName());
        applySettings(saved);
    }

    @Override public String getScriptName() { return "Crafting"; }

    @Override
    protected JPanel buildContent()
    {
        JPanel root = new JPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBackground(AxiomTheme.BG_PANEL);
        root.setBorder(new EmptyBorder(8, 8, 8, 8));

        AxiomSectionPanel behaviour = new AxiomSectionPanel("BEHAVIOUR");
        cbBankingMode = makeCheckBox("Banking mode (restock gems from bank)", true);
        behaviour.addCheckRow("", cbBankingMode);
        root.add(behaviour);

        return root;
    }

    @Override
    public CraftingSettings getSettings()
    {
        CraftingSettings s = new CraftingSettings();
        s.bankingMode = cbBankingMode.isSelected();
        settings = s;
        return s;
    }

    private void applySettings(CraftingSettings s)
    {
        if (s == null || cbBankingMode == null) return;
        cbBankingMode.setSelected(s.bankingMode);
    }
}
