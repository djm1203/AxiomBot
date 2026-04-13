package com.botengine.osrs.scripts.woodcutting;

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

/**
 * Config dialog for WoodcuttingScript.
 *
 * Sections: TREE SELECTION, BEHAVIOUR
 */
public class WoodcuttingConfigDialog extends ScriptConfigDialog<WoodcuttingSettings>
{
    private JTextField  tfTreeName;
    private JTextField  tfProgression;
    private JComboBox<String> cbMode;
    private JCheckBox   cbPickupNests;
    private JCheckBox   cbHopOnCompetition;

    public WoodcuttingConfigDialog(JComponent parent)
    {
        super(parent);
        WoodcuttingSettings saved = WoodcuttingSettings.load(WoodcuttingSettings.class, getScriptName());
        applySettings(saved);
    }

    @Override public String getScriptName() { return "Woodcutting"; }

    @Override
    protected JPanel buildContent()
    {
        JPanel root = new JPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBackground(AxiomTheme.BG_PANEL);
        root.setBorder(new EmptyBorder(8, 8, 8, 8));

        // ── TREE SELECTION ────────────────────────────────────────────────────
        AxiomSectionPanel trees = new AxiomSectionPanel("TREE SELECTION");

        tfTreeName = makeTextField("e.g. Oak (overridden by progression)");
        trees.addRow("Tree name filter", tfTreeName);

        tfProgression = makeTextField("e.g. 1:Oak,30:Willow,45:Maple,60:Yew");
        trees.addRow("Progression", tfProgression);

        root.add(trees);

        // ── BEHAVIOUR ─────────────────────────────────────────────────────────
        AxiomSectionPanel behaviour = new AxiomSectionPanel("BEHAVIOUR");

        cbMode = makeCombo("Power-drop", "Banking");
        behaviour.addRow("Mode", cbMode);

        cbPickupNests = makeCheckBox("Pick up bird nests", true);
        behaviour.addCheckRow("", cbPickupNests);

        cbHopOnCompetition = makeCheckBox("World hop on competition", false);
        behaviour.addCheckRow("", cbHopOnCompetition);

        root.add(behaviour);
        return root;
    }

    @Override
    public WoodcuttingSettings getSettings()
    {
        WoodcuttingSettings s = new WoodcuttingSettings();
        s.treeNameFilter   = tfTreeName.getText().trim();
        s.progression      = tfProgression.getText().trim();
        s.bankingMode      = "Banking".equals(cbMode.getSelectedItem());
        s.pickupNests      = cbPickupNests.isSelected();
        s.hopOnCompetition = cbHopOnCompetition.isSelected();
        settings = s;
        return s;
    }

    private void applySettings(WoodcuttingSettings s)
    {
        if (s == null || tfTreeName == null) return;
        tfTreeName.setText(s.treeNameFilter);
        tfProgression.setText(s.progression);
        cbMode.setSelectedItem(s.bankingMode ? "Banking" : "Power-drop");
        cbPickupNests.setSelected(s.pickupNests);
        cbHopOnCompetition.setSelected(s.hopOnCompetition);
    }
}
