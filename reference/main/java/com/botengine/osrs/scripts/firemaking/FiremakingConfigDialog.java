package com.botengine.osrs.scripts.firemaking;

import com.botengine.osrs.ui.AxiomSectionPanel;
import com.botengine.osrs.ui.ScriptConfigDialog;

import javax.swing.*;

public class FiremakingConfigDialog extends ScriptConfigDialog<FiremakingSettings>
{
    private static final int[] LOG_IDS = { 1511, 1521, 1519, 1517, 1515, 1513 };

    private JComboBox<String> cbLogType;
    private JComboBox<String> cbMode;

    public FiremakingConfigDialog(JComponent parent)
    {
        super(parent);
    }

    @Override
    public String getScriptName() { return "Firemaking"; }

    @Override
    protected JPanel buildContent()
    {
        JPanel root = new JPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setOpaque(false);

        cbLogType = makeCombo("Normal logs", "Oak logs", "Willow logs",
                              "Maple logs", "Yew logs", "Magic logs");
        cbMode    = makeCombo("Drop (stop when out)", "Banking");

        AxiomSectionPanel logs = new AxiomSectionPanel("LOG TYPE");
        logs.addRow("Type", cbLogType);
        root.add(logs);

        AxiomSectionPanel behaviour = new AxiomSectionPanel("BEHAVIOUR");
        behaviour.addRow("Mode", cbMode);
        root.add(behaviour);

        return root;
    }

    @Override
    public FiremakingSettings getSettings()
    {
        FiremakingSettings s = new FiremakingSettings();
        s.logItemId   = LOG_IDS[cbLogType.getSelectedIndex()];
        s.bankingMode = cbMode.getSelectedIndex() == 1;
        return s;
    }
}
