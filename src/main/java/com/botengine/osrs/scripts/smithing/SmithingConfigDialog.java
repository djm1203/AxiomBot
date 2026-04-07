package com.botengine.osrs.scripts.smithing;

import com.botengine.osrs.ui.AxiomSectionPanel;
import com.botengine.osrs.ui.AxiomTheme;
import com.botengine.osrs.ui.ScriptConfigDialog;

import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

/** Config dialog for SmithingScript. */
public class SmithingConfigDialog extends ScriptConfigDialog<SmithingSettings>
{
    private JCheckBox cbBankingMode;
    private JCheckBox cbBlastFurnace;
    private JCheckBox cbCoalBag;

    public SmithingConfigDialog(JComponent parent)
    {
        super(parent);
        SmithingSettings saved = SmithingSettings.load(SmithingSettings.class, getScriptName());
        applySettings(saved);
    }

    @Override public String getScriptName() { return "Smithing"; }

    @Override
    protected JPanel buildContent()
    {
        JPanel root = new JPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBackground(AxiomTheme.BG_PANEL);
        root.setBorder(new EmptyBorder(8, 8, 8, 8));

        AxiomSectionPanel mode = new AxiomSectionPanel("MODE");
        cbBankingMode  = makeCheckBox("Banking mode (restock bars from bank)", false);
        mode.addCheckRow("", cbBankingMode);
        cbBlastFurnace = makeCheckBox("Blast Furnace mode (Keldagrim)", false);
        mode.addCheckRow("", cbBlastFurnace);
        cbCoalBag      = makeCheckBox("Use coal bag (BF mode only)", false);
        mode.addCheckRow("", cbCoalBag);
        root.add(mode);

        return root;
    }

    @Override
    public SmithingSettings getSettings()
    {
        SmithingSettings s = new SmithingSettings();
        s.bankingMode      = cbBankingMode.isSelected();
        s.blastFurnaceMode = cbBlastFurnace.isSelected();
        s.useCoalBag       = cbCoalBag.isSelected();
        settings = s;
        return s;
    }

    private void applySettings(SmithingSettings s)
    {
        if (s == null || cbBankingMode == null) return;
        cbBankingMode.setSelected(s.bankingMode);
        cbBlastFurnace.setSelected(s.blastFurnaceMode);
        cbCoalBag.setSelected(s.useCoalBag);
    }
}
