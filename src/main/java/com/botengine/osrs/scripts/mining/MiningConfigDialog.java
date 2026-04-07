package com.botengine.osrs.scripts.mining;

import com.botengine.osrs.ui.AxiomSectionPanel;
import com.botengine.osrs.ui.AxiomTheme;
import com.botengine.osrs.ui.ScriptConfigDialog;

import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;

/** Config dialog for MiningScript. */
public class MiningConfigDialog extends ScriptConfigDialog<MiningSettings>
{
    private JTextField  tfRockName;
    private JTextField  tfProgression;
    private JComboBox<String> cbMode;
    private JCheckBox   cbShiftDrop;
    private JCheckBox   cbHopOnCompetition;
    private JCheckBox   cbMotherlode;

    public MiningConfigDialog(JComponent parent)
    {
        super(parent);
        MiningSettings saved = MiningSettings.load(MiningSettings.class, getScriptName());
        applySettings(saved);
    }

    @Override public String getScriptName() { return "Mining"; }

    @Override
    protected JPanel buildContent()
    {
        JPanel root = new JPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBackground(AxiomTheme.BG_PANEL);
        root.setBorder(new EmptyBorder(8, 8, 8, 8));

        AxiomSectionPanel ore = new AxiomSectionPanel("ORE SELECTION");
        tfRockName = makeTextField("e.g. Iron (overridden by progression)");
        ore.addRow("Rock name filter", tfRockName);
        tfProgression = makeTextField("e.g. 1:Copper,15:Iron,30:Coal,50:Mithril");
        ore.addRow("Progression", tfProgression);
        root.add(ore);

        AxiomSectionPanel behaviour = new AxiomSectionPanel("BEHAVIOUR");
        cbMode = makeCombo("Power-drop", "Banking");
        behaviour.addRow("Mode", cbMode);
        cbShiftDrop = makeCheckBox("Shift-drop (faster power-mining)", false);
        behaviour.addCheckRow("", cbShiftDrop);
        cbHopOnCompetition = makeCheckBox("World hop on competition", false);
        behaviour.addCheckRow("", cbHopOnCompetition);
        cbMotherlode = makeCheckBox("Motherlode Mine mode", false);
        behaviour.addCheckRow("", cbMotherlode);
        root.add(behaviour);

        return root;
    }

    @Override
    public MiningSettings getSettings()
    {
        MiningSettings s = new MiningSettings();
        s.rockNameFilter   = tfRockName.getText().trim();
        s.progression      = tfProgression.getText().trim();
        s.bankingMode      = "Banking".equals(cbMode.getSelectedItem());
        s.shiftDrop        = cbShiftDrop.isSelected();
        s.hopOnCompetition = cbHopOnCompetition.isSelected();
        s.motherlodeMode   = cbMotherlode.isSelected();
        settings = s;
        return s;
    }

    private void applySettings(MiningSettings s)
    {
        if (s == null || tfRockName == null) return;
        tfRockName.setText(s.rockNameFilter);
        tfProgression.setText(s.progression);
        cbMode.setSelectedItem(s.bankingMode ? "Banking" : "Power-drop");
        cbShiftDrop.setSelected(s.shiftDrop);
        cbHopOnCompetition.setSelected(s.hopOnCompetition);
        cbMotherlode.setSelected(s.motherlodeMode);
    }
}
