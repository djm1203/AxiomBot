package com.botengine.osrs.scripts.agility;

import com.botengine.osrs.ui.AxiomSectionPanel;
import com.botengine.osrs.ui.ScriptConfigDialog;

import javax.swing.*;

public class AgilityConfigDialog extends ScriptConfigDialog<AgilitySettings>
{
    private JComboBox<String> cbCourse;
    private JCheckBox         cbPickMarks;

    public AgilityConfigDialog(JComponent parent)
    {
        super(parent);
    }

    @Override
    public String getScriptName() { return "Agility"; }

    @Override
    protected JPanel buildContent()
    {
        JPanel root = new JPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setOpaque(false);

        cbCourse    = makeCombo("Gnome Stronghold", "Draynor", "Al Kharid",
                                "Varrock", "Canifis");
        cbPickMarks = makeCheckBox("Pick up Marks of Grace", true);

        AxiomSectionPanel course = new AxiomSectionPanel("COURSE");
        course.addRow("Course", cbCourse);
        root.add(course);

        AxiomSectionPanel behaviour = new AxiomSectionPanel("BEHAVIOUR");
        behaviour.addCheckRow("Mark of Grace", cbPickMarks);
        root.add(behaviour);

        return root;
    }

    @Override
    public AgilitySettings getSettings()
    {
        AgilitySettings s = new AgilitySettings();
        s.courseName  = (String) cbCourse.getSelectedItem();
        s.pickupMarks = cbPickMarks.isSelected();
        return s;
    }
}
