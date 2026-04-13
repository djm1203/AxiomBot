package com.botengine.osrs.scripts.herblore;

import com.botengine.osrs.ui.AxiomSectionPanel;
import com.botengine.osrs.ui.ScriptConfigDialog;

import javax.swing.*;

public class HerbloreConfigDialog extends ScriptConfigDialog<HerbloreSettings>
{
    // Grimy herb IDs in order matching the combo
    private static final int[] GRIMY_IDS = {
        169, 171, 173, 175, 177, 179, 181, 183, 3049, 185, 2485, 187, 189
    };
    // Clean herb IDs in matching order
    private static final int[] CLEAN_IDS = {
        199, 201, 203, 205, 207, 209, 211, 213, 3051, 215, 2481, 217, 219
    };

    private JComboBox<String> cbMode;
    private JComboBox<String> cbHerb;
    private JTextField        tfSecondary;
    private JTextField        tfVial;

    public HerbloreConfigDialog(JComponent parent)
    {
        super(parent);
    }

    @Override
    public String getScriptName() { return "Herblore"; }

    @Override
    protected JPanel buildContent()
    {
        JPanel root = new JPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setOpaque(false);

        cbMode = makeCombo("Clean", "Make Potion");
        cbHerb = makeCombo(
            "Guam", "Marrentill", "Tarromin", "Harralander",
            "Ranarr", "Irit", "Avantoe", "Kwuarm",
            "Snapdragon", "Cadantine", "Lantadyme", "Dwarf Weed", "Torstol"
        );
        tfSecondary = makeTextField("221");  // Eye of newt default
        tfVial      = makeTextField("227");  // Vial of water default

        AxiomSectionPanel modeSection = new AxiomSectionPanel("MODE");
        modeSection.addRow("Mode", cbMode);
        root.add(modeSection);

        AxiomSectionPanel herb = new AxiomSectionPanel("HERB");
        herb.addRow("Herb", cbHerb);
        root.add(herb);

        AxiomSectionPanel potion = new AxiomSectionPanel("MAKE POTION");
        potion.addRow("Secondary ID", tfSecondary);
        potion.addRow("Vial of water ID", tfVial);
        root.add(potion);

        return root;
    }

    @Override
    public HerbloreSettings getSettings()
    {
        HerbloreSettings s   = new HerbloreSettings();
        int idx              = cbHerb.getSelectedIndex();
        s.mode               = (String) cbMode.getSelectedItem();
        s.herbItemId         = GRIMY_IDS[idx];
        s.cleanHerbId        = CLEAN_IDS[idx];
        try { s.secondaryId   = Integer.parseInt(tfSecondary.getText().trim()); }
        catch (NumberFormatException ignored) {}
        try { s.vialOfWaterId = Integer.parseInt(tfVial.getText().trim()); }
        catch (NumberFormatException ignored) {}
        return s;
    }
}
