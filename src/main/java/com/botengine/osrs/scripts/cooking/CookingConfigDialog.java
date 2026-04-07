package com.botengine.osrs.scripts.cooking;

import com.botengine.osrs.ui.AxiomSectionPanel;
import com.botengine.osrs.ui.AxiomTheme;
import com.botengine.osrs.ui.ScriptConfigDialog;

import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

/** Config dialog for CookingScript. */
public class CookingConfigDialog extends ScriptConfigDialog<CookingSettings>
{
    private JCheckBox cbBankingMode;

    public CookingConfigDialog(JComponent parent)
    {
        super(parent);
        CookingSettings saved = CookingSettings.load(CookingSettings.class, getScriptName());
        applySettings(saved);
    }

    @Override public String getScriptName() { return "Cooking"; }

    @Override
    protected JPanel buildContent()
    {
        JPanel root = new JPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBackground(AxiomTheme.BG_PANEL);
        root.setBorder(new EmptyBorder(8, 8, 8, 8));

        AxiomSectionPanel behaviour = new AxiomSectionPanel("BEHAVIOUR");
        cbBankingMode = makeCheckBox("Banking mode (restock raw food from bank)", true);
        behaviour.addCheckRow("", cbBankingMode);
        root.add(behaviour);

        return root;
    }

    @Override
    public CookingSettings getSettings()
    {
        CookingSettings s = new CookingSettings();
        s.bankingMode = cbBankingMode.isSelected();
        settings = s;
        return s;
    }

    private void applySettings(CookingSettings s)
    {
        if (s == null || cbBankingMode == null) return;
        cbBankingMode.setSelected(s.bankingMode);
    }
}
