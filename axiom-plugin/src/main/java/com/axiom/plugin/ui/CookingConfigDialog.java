package com.axiom.plugin.ui;

import com.axiom.scripts.cooking.CookingSettings;
import com.axiom.scripts.cooking.CookingSettings.FoodType;
import com.axiom.scripts.cooking.CookingSettings.CookingLocation;

import javax.swing.Box;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import java.awt.Dimension;

/**
 * Configuration dialog for the Cooking script.
 */
public class CookingConfigDialog extends ScriptConfigDialog<CookingSettings>
{
    // ── Controls ──────────────────────────────────────────────────────────────
    private JComboBox<String> foodCombo;
    private JComboBox<String> locationCombo;
    private JCheckBox         bankForFoodBox;
    private JSpinner          breakIntervalSpinner;
    private JSpinner          breakDurationSpinner;

    public CookingConfigDialog(JComponent parent)
    {
        super(parent);
    }

    @Override
    public String getScriptName() { return "Axiom Cooking"; }

    @Override
    protected JPanel buildContent()
    {
        JPanel root = new JPanel();
        root.setLayout(new javax.swing.BoxLayout(root, javax.swing.BoxLayout.Y_AXIS));
        root.setBackground(AxiomTheme.BG_PANEL);
        root.setBorder(new javax.swing.border.EmptyBorder(
            AxiomTheme.PAD_MD, AxiomTheme.PAD_LG, AxiomTheme.PAD_MD, AxiomTheme.PAD_LG));

        // ── Section: Food ─────────────────────────────────────────────────────
        AxiomSectionPanel foodSection = new AxiomSectionPanel("FOOD TYPE");
        foodCombo = makeCombo(foodDisplayNames());
        foodCombo.setSelectedIndex(foodIndexFor(FoodType.TROUT)); // default: trout
        foodSection.addRow("Food", foodCombo);
        root.add(foodSection);
        root.add(Box.createRigidArea(new Dimension(0, AxiomTheme.PAD_SM)));

        // ── Section: Location ─────────────────────────────────────────────────
        AxiomSectionPanel locationSection = new AxiomSectionPanel("COOKING LOCATION");
        locationCombo = makeCombo(locationDisplayNames());
        locationCombo.setSelectedIndex(0); // default: Range
        locationSection.addRow("Location", locationCombo);
        root.add(locationSection);
        root.add(Box.createRigidArea(new Dimension(0, AxiomTheme.PAD_SM)));

        // ── Section: Banking ──────────────────────────────────────────────────
        AxiomSectionPanel bankSection = new AxiomSectionPanel("BANKING");
        bankForFoodBox = makeCheckBox("Bank when raw food runs out to get more", true);
        bankSection.addCheckRow("", bankForFoodBox);
        root.add(bankSection);
        root.add(Box.createRigidArea(new Dimension(0, AxiomTheme.PAD_SM)));

        // ── Section: Antiban ──────────────────────────────────────────────────
        AxiomSectionPanel antibanSection = new AxiomSectionPanel("ANTIBAN / BREAKS");
        breakIntervalSpinner = makeSpinner(10, 240, 60);
        antibanSection.addRow("Break every (min)", breakIntervalSpinner);
        breakDurationSpinner = makeSpinner(1, 30, 5);
        antibanSection.addRow("Break length (min)", breakDurationSpinner);
        root.add(antibanSection);
        root.add(Box.createRigidArea(new Dimension(0, AxiomTheme.PAD_SM)));

        return root;
    }

    @Override
    public CookingSettings getSettings()
    {
        return new CookingSettings(
            FoodType.values()[foodCombo.getSelectedIndex()],
            CookingLocation.values()[locationCombo.getSelectedIndex()],
            bankForFoodBox.isSelected(),
            (Integer) breakIntervalSpinner.getValue(),
            (Integer) breakDurationSpinner.getValue()
        );
    }

    // ── Display name builders ─────────────────────────────────────────────────

    private static String[] foodDisplayNames()
    {
        FoodType[] types = FoodType.values();
        String[] names = new String[types.length];
        for (int i = 0; i < types.length; i++)
        {
            names[i] = types[i].rawName + " → " + types[i].cookedName
                + " (Lv. " + types[i].levelRequired + ")";
        }
        return names;
    }

    private static String[] locationDisplayNames()
    {
        CookingLocation[] locs = CookingLocation.values();
        String[] names = new String[locs.length];
        for (int i = 0; i < locs.length; i++) names[i] = locs[i].objectName;
        return names;
    }

    private static int foodIndexFor(FoodType target)
    {
        FoodType[] types = FoodType.values();
        for (int i = 0; i < types.length; i++)
        {
            if (types[i] == target) return i;
        }
        return 0;
    }
}
