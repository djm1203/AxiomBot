package com.botengine.osrs.scripts.fishing;

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

/** Config dialog for FishingScript. */
public class FishingConfigDialog extends ScriptConfigDialog<FishingSettings>
{
    private JComboBox<String> cbAction;
    private JTextField  tfProgression;
    private JComboBox<String> cbMode;
    private JCheckBox   cbShiftDrop;

    public FishingConfigDialog(JComponent parent)
    {
        super(parent);
        FishingSettings saved = FishingSettings.load(FishingSettings.class, getScriptName());
        applySettings(saved);
    }

    @Override public String getScriptName() { return "Fishing"; }

    @Override
    protected JPanel buildContent()
    {
        JPanel root = new JPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBackground(AxiomTheme.BG_PANEL);
        root.setBorder(new EmptyBorder(8, 8, 8, 8));

        AxiomSectionPanel spot = new AxiomSectionPanel("FISHING SPOT");
        cbAction = makeCombo("Lure", "Bait", "Net", "Cage", "Harpoon", "Fly");
        spot.addRow("Click action", cbAction);
        tfProgression = makeTextField("e.g. 1:Net,20:Fly,40:Harpoon");
        spot.addRow("Progression", tfProgression);
        root.add(spot);

        AxiomSectionPanel behaviour = new AxiomSectionPanel("BEHAVIOUR");
        cbMode = makeCombo("Power-drop", "Banking");
        behaviour.addRow("Mode", cbMode);
        cbShiftDrop = makeCheckBox("Shift-drop (faster power-fishing)", false);
        behaviour.addCheckRow("", cbShiftDrop);
        root.add(behaviour);

        return root;
    }

    @Override
    public FishingSettings getSettings()
    {
        FishingSettings s = new FishingSettings();
        s.fishingAction = (String) cbAction.getSelectedItem();
        s.progression   = tfProgression.getText().trim();
        s.bankingMode   = "Banking".equals(cbMode.getSelectedItem());
        s.shiftDrop     = cbShiftDrop.isSelected();
        settings = s;
        return s;
    }

    private void applySettings(FishingSettings s)
    {
        if (s == null || cbAction == null) return;
        setComboItem(cbAction, s.fishingAction);
        tfProgression.setText(s.progression);
        cbMode.setSelectedItem(s.bankingMode ? "Banking" : "Power-drop");
        cbShiftDrop.setSelected(s.shiftDrop);
    }

    private void setComboItem(JComboBox<String> cb, String val)
    {
        for (int i = 0; i < cb.getItemCount(); i++)
            if (val != null && val.equals(cb.getItemAt(i))) { cb.setSelectedIndex(i); return; }
    }
}
