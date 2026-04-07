package com.botengine.osrs.scripts.fletching;

import com.botengine.osrs.ui.AxiomSectionPanel;
import com.botengine.osrs.ui.AxiomTheme;
import com.botengine.osrs.ui.ScriptConfigDialog;

import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

/** Config dialog for FletchingScript. */
public class FletchingConfigDialog extends ScriptConfigDialog<FletchingSettings>
{
    private JCheckBox cbBankingMode;

    public FletchingConfigDialog(JComponent parent)
    {
        super(parent);
        FletchingSettings saved = FletchingSettings.load(FletchingSettings.class, getScriptName());
        applySettings(saved);
    }

    @Override public String getScriptName() { return "Fletching"; }

    @Override
    protected JPanel buildContent()
    {
        JPanel root = new JPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBackground(AxiomTheme.BG_PANEL);
        root.setBorder(new EmptyBorder(8, 8, 8, 8));

        AxiomSectionPanel behaviour = new AxiomSectionPanel("BEHAVIOUR");
        cbBankingMode = makeCheckBox("Banking mode (restock materials)", true);
        behaviour.addCheckRow("", cbBankingMode);
        root.add(behaviour);

        return root;
    }

    @Override
    public FletchingSettings getSettings()
    {
        FletchingSettings s = new FletchingSettings();
        s.bankingMode = cbBankingMode.isSelected();
        settings = s;
        return s;
    }

    private void applySettings(FletchingSettings s)
    {
        if (s == null || cbBankingMode == null) return;
        cbBankingMode.setSelected(s.bankingMode);
    }
}
